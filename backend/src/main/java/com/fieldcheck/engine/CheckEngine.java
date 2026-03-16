package com.fieldcheck.engine;

import com.fieldcheck.entity.*;
import com.fieldcheck.repository.RiskResultRepository;
import com.fieldcheck.repository.TaskExecutionRepository;
import com.fieldcheck.service.ConnectionService;
import com.fieldcheck.service.WhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckEngine {

    private final ConnectionService connectionService;
    private final WhitelistService whitelistService;
    private final RiskResultRepository riskResultRepository;
    private final TaskExecutionRepository executionRepository;
    private final TransactionTemplate transactionTemplate;

    // 整型类型的最大值映射
    private static final Map<String, BigInteger> INT_MAX_VALUES = new HashMap<>();
    private static final Map<String, BigInteger> INT_MIN_VALUES = new HashMap<>();
    
    static {
        INT_MAX_VALUES.put("TINYINT", BigInteger.valueOf(127));
        INT_MAX_VALUES.put("TINYINT UNSIGNED", BigInteger.valueOf(255));
        INT_MAX_VALUES.put("SMALLINT", BigInteger.valueOf(32767));
        INT_MAX_VALUES.put("SMALLINT UNSIGNED", BigInteger.valueOf(65535));
        INT_MAX_VALUES.put("MEDIUMINT", BigInteger.valueOf(8388607));
        INT_MAX_VALUES.put("MEDIUMINT UNSIGNED", BigInteger.valueOf(16777215));
        INT_MAX_VALUES.put("INT", BigInteger.valueOf(2147483647));
        INT_MAX_VALUES.put("INT UNSIGNED", BigInteger.valueOf(4294967295L));
        INT_MAX_VALUES.put("BIGINT", new BigInteger("9223372036854775807"));
        INT_MAX_VALUES.put("BIGINT UNSIGNED", new BigInteger("18446744073709551615"));
        
        INT_MIN_VALUES.put("TINYINT", BigInteger.valueOf(-128));
        INT_MIN_VALUES.put("SMALLINT", BigInteger.valueOf(-32768));
        INT_MIN_VALUES.put("MEDIUMINT", BigInteger.valueOf(-8388608));
        INT_MIN_VALUES.put("INT", BigInteger.valueOf(-2147483648));
        INT_MIN_VALUES.put("BIGINT", new BigInteger("-9223372036854775808"));
    }

    public void execute(TaskExecution execution, BiConsumer<Long, String> logCallback, Supplier<Boolean> stopCheck) {
        CheckTask task = execution.getTask();
        DbConnection dbConnection = task.getConnection();
        
        String jdbcUrl = String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=30000&socketTimeout=300000&autoReconnect=true&failOverReadOnly=false&maxReconnects=3",
                dbConnection.getHost(), dbConnection.getPort());
        String password = connectionService.getDecryptedPassword(dbConnection.getId());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbConnection.getUsername(), password)) {
            // Set connection to keep alive during long operations
            conn.setAutoCommit(true);
            // Get databases to check
            List<String> databases = getDatabases(conn, task.getDbPattern());
            logCallback.accept(execution.getId(), String.format("INFO|找到 %d 个数据库待检查", databases.size()));

            // Get all tables
            List<TableInfo> tables = new ArrayList<>();
            for (String database : databases) {
                if (stopCheck.get()) {
                    logCallback.accept(execution.getId(), "WARN|任务被用户停止");
                    return;
                }
                tables.addAll(getTables(conn, database, task.getTablePattern()));
            }
            
            logCallback.accept(execution.getId(), String.format("INFO|共计 %d 张表待检查", tables.size()));
            execution.setTotalTables(tables.size());
            saveExecution(execution);

            int processed = 0;
            int riskCount = 0;
            int saveCounter = 0;

            for (TableInfo table : tables) {
                if (stopCheck.get()) {
                    logCallback.accept(execution.getId(), "WARN|任务被用户停止");
                    return;
                }

                // Check whitelist
                if (whitelistService.isWhitelisted(table.database, table.tableName, null, task)) {
                    processed++;
                    logCallback.accept(execution.getId(), String.format("INFO|[%d/%d] 跳过白名单表: %s.%s", processed, tables.size(), table.database, table.tableName));
                    continue;
                }

                // Skip empty tables (0 rows)
                if (table.rowCount == 0) {
                    processed++;
                    logCallback.accept(execution.getId(), String.format("INFO|[%d/%d] 跳过空表: %s.%s (0行)", processed, tables.size(), table.database, table.tableName));
                    continue;
                }

                // Check connection before processing large table
                if (table.rowCount > 100000) {
                    try {
                        if (!conn.isValid(5)) {
                            logCallback.accept(execution.getId(), String.format("WARN|[%d/%d] 连接已失效，尝试重新连接...", processed + 1, tables.size()));
                            // Connection will be re-established on next operation with autoReconnect
                        }
                    } catch (SQLException e) {
                        log.warn("Connection validation failed", e);
                    }
                }

                // Determine scan strategy
                // For large tables without fullScan, use sampling based on primary key (latest data)
                boolean isLargeTable = table.rowCount > task.getMaxTableRows() && !task.getFullScan();
                String strategy = isLargeTable ? "抽样检查(按主键)" : "全量扫描";
                String rowCountStr = formatRowCount(table.rowCount);
                
                // Get primary key column for sampling
                String primaryKey = null;
                if (isLargeTable) {
                    primaryKey = getPrimaryKeyColumn(conn, table.database, table.tableName);
                }
                final String tablePrimaryKey = primaryKey;
                final boolean tableIsLargeTable = isLargeTable;
                
                logCallback.accept(execution.getId(), String.format("INFO|[%d/%d] 开始检查表: %s.%s (约%s行, 策略: %s%s)", 
                        processed + 1, tables.size(), table.database, table.tableName, rowCountStr, strategy,
                        isLargeTable && primaryKey != null ? ", 主键: " + primaryKey : ""));

                // Get columns
                List<ColumnInfo> columns = getColumns(conn, table.database, table.tableName);
                logCallback.accept(execution.getId(), String.format("INFO|[%d/%d] %s.%s 共%d个字段待检查", 
                        processed + 1, tables.size(), table.database, table.tableName, columns.size()));
                
                int columnProcessed = 0;
                for (ColumnInfo column : columns) {
                    if (stopCheck.get()) return;
                    
                    // Check whitelist for field
                    if (whitelistService.isWhitelisted(table.database, table.tableName, column.columnName, task)) {
                        columnProcessed++;
                        continue;
                    }

                    try {
                        List<RiskResult> risks = checkColumn(conn, execution, table, column, task, logCallback, 
                                tableIsLargeTable, tablePrimaryKey);
                        if (!risks.isEmpty()) {
                            riskResultRepository.saveAll(risks);
                            riskCount += risks.size();
                        }
                    } catch (Exception e) {
                        if (e.getMessage() != null && (e.getMessage().contains("connection closed") || e.getMessage().contains("Connection reset"))) {
                            logCallback.accept(execution.getId(), String.format("ERROR|[%d/%d] 检查字段 %s.%s.%s 时连接断开: %s", 
                                    processed + 1, tables.size(), table.database, table.tableName, column.columnName, e.getMessage()));
                            throw new RuntimeException("数据库连接断开: " + e.getMessage(), e);
                        }
                        log.error("检查字段失败: {}.{}.{}", table.database, table.tableName, column.columnName, e);
                    }
                    columnProcessed++;
                }

                processed++;
                execution.setProcessedTables(processed);
                execution.setRiskCount(riskCount);
                
                logCallback.accept(execution.getId(), String.format("INFO|[%d/%d] 完成表: %s.%s (累计风险: %d)", 
                        processed, tables.size(), table.database, table.tableName, riskCount));
                
                // Save progress every 5 tables to reduce database writes
                saveCounter++;
                if (saveCounter >= 5 || processed == tables.size()) {
                    saveExecution(execution);
                    saveCounter = 0;
                }
            }

            logCallback.accept(execution.getId(), String.format("INFO|检查完成，发现 %d 个风险", riskCount));

        } catch (SQLException e) {
            log.error("Database check failed", e);
            throw new RuntimeException("数据库检查失败: " + e.getMessage());
        }
    }
    
    public void saveExecution(TaskExecution execution) {
        transactionTemplate.execute(status -> {
            TaskExecution freshExecution = executionRepository.findById(execution.getId())
                    .orElseThrow(() -> new RuntimeException("执行记录不存在"));
            if (execution.getTotalTables() > 0) {
                freshExecution.setTotalTables(execution.getTotalTables());
            }
            freshExecution.setProcessedTables(execution.getProcessedTables());
            freshExecution.setRiskCount(execution.getRiskCount());
            executionRepository.save(freshExecution);
            return null;
        });
    }

    private List<String> getDatabases(Connection conn, String pattern) throws SQLException {
        List<String> databases = new ArrayList<>();
        String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String dbName = rs.getString(1);
                if (matchPattern(dbName, pattern)) {
                    databases.add(dbName);
                }
            }
        }
        return databases;
    }

    private List<TableInfo> getTables(Connection conn, String database, String tablePattern) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        String sql = String.format(
            "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = 'BASE TABLE'",
            database);
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (matchPattern(tableName, tablePattern)) {
                    TableInfo info = new TableInfo();
                    info.database = database;
                    info.tableName = tableName;
                    info.rowCount = rs.getLong("TABLE_ROWS");
                    tables.add(info);
                }
            }
        }
        return tables;
    }

    private List<ColumnInfo> getColumns(Connection conn, String database, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        String sql = String.format(
            "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE, CHARACTER_MAXIMUM_LENGTH " +
            "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
            database, tableName);
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ColumnInfo info = new ColumnInfo();
                info.columnName = rs.getString("COLUMN_NAME");
                info.dataType = rs.getString("DATA_TYPE").toUpperCase();
                info.columnType = rs.getString("COLUMN_TYPE").toUpperCase();
                info.numericPrecision = rs.getObject("NUMERIC_PRECISION") != null ? rs.getInt("NUMERIC_PRECISION") : null;
                info.numericScale = rs.getObject("NUMERIC_SCALE") != null ? rs.getInt("NUMERIC_SCALE") : null;
                info.charMaxLength = rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ? rs.getLong("CHARACTER_MAXIMUM_LENGTH") : null;
                columns.add(info);
            }
        }
        return columns;
    }

    private List<RiskResult> checkColumn(Connection conn, TaskExecution execution, TableInfo table, 
                                          ColumnInfo column, CheckTask task, BiConsumer<Long, String> logCallback,
                                          boolean isLargeTable, String primaryKey) {
        List<RiskResult> risks = new ArrayList<>();
        
        try {
            // Check integer overflow
            if (isIntegerType(column.dataType)) {
                RiskResult risk = checkIntegerOverflow(conn, execution, table, column, task, isLargeTable, primaryKey);
                if (risk != null) {
                    risks.add(risk);
                    logCallback.accept(execution.getId(), 
                            String.format("WARN|发现整型溢出风险: %s.%s.%s", table.database, table.tableName, column.columnName));
                }
            }
            
            // Check Y2038 problem
            if (isTimestampType(column.dataType)) {
                RiskResult risk = checkY2038(conn, execution, table, column, task, isLargeTable, primaryKey);
                if (risk != null) {
                    risks.add(risk);
                    logCallback.accept(execution.getId(), 
                            String.format("WARN|发现Y2038风险: %s.%s.%s", table.database, table.tableName, column.columnName));
                }
            }
            
            // Check decimal overflow
            if (isDecimalType(column.dataType)) {
                RiskResult risk = checkDecimalOverflow(conn, execution, table, column, task, isLargeTable, primaryKey);
                if (risk != null) {
                    risks.add(risk);
                    logCallback.accept(execution.getId(), 
                            String.format("WARN|发现小数溢出风险: %s.%s.%s", table.database, table.tableName, column.columnName));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check column {}.{}.{}", table.database, table.tableName, column.columnName, e);
        }
        
        return risks;
    }

    private RiskResult checkIntegerOverflow(Connection conn, TaskExecution execution, TableInfo table, 
                                            ColumnInfo column, CheckTask task, 
                                            boolean isLargeTable, String primaryKey) throws SQLException {
        String typeKey = column.columnType.contains("UNSIGNED") 
                ? column.dataType + " UNSIGNED" 
                : column.dataType;
        
        BigInteger maxAllowed = INT_MAX_VALUES.get(typeKey);
        BigInteger minAllowed = INT_MIN_VALUES.getOrDefault(typeKey, BigInteger.ZERO);
        
        if (maxAllowed == null) return null;

        // Build SQL query
        String sql;
        if (isLargeTable && primaryKey != null) {
            // For large tables, sample by ordering on primary key and taking latest records
            sql = String.format("SELECT MAX(`%s`) as max_val, MIN(`%s`) as min_val FROM " +
                    "(SELECT `%s` FROM `%s`.`%s` ORDER BY `%s` DESC LIMIT %d) AS sample",
                    column.columnName, column.columnName, column.columnName, 
                    table.database, table.tableName, primaryKey, task.getSampleSize());
        } else {
            // Full table scan
            sql = String.format("SELECT MAX(`%s`) as max_val, MIN(`%s`) as min_val FROM `%s`.`%s`",
                    column.columnName, column.columnName, table.database, table.tableName);
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                BigInteger maxVal = rs.getObject("max_val") != null ? new BigInteger(rs.getString("max_val")) : BigInteger.ZERO;
                BigInteger minVal = rs.getObject("min_val") != null ? new BigInteger(rs.getString("min_val")) : BigInteger.ZERO;

                // Calculate usage percentage
                BigDecimal usage = BigDecimal.ZERO;
                if (maxAllowed.compareTo(BigInteger.ZERO) > 0) {
                    usage = new BigDecimal(maxVal.abs()).multiply(BigDecimal.valueOf(100))
                            .divide(new BigDecimal(maxAllowed), 2, BigDecimal.ROUND_HALF_UP);
                }

                if (usage.compareTo(BigDecimal.valueOf(task.getThresholdPct())) >= 0) {
                    String detail = isLargeTable ? 
                            String.format("抽样检查(最新%d条): 最大值=%s, 类型最大值=%s", task.getSampleSize(), maxVal, maxAllowed) :
                            String.format("当前最大值: %s, 类型最大值: %s", maxVal, maxAllowed);
                    return RiskResult.builder()
                            .execution(execution)
                            .databaseName(table.database)
                            .tableName(table.tableName)
                            .columnName(column.columnName)
                            .columnType(column.columnType)
                            .riskType(RiskType.INT_OVERFLOW)
                            .currentValue(maxVal.toString())
                            .thresholdValue(maxAllowed.toString())
                            .usagePercent(usage)
                            .detail(detail)
                            .suggestion(getSuggestion(column.dataType, typeKey))
                            .status(RiskStatus.PENDING)
                            .build();
                }
            }
        }
        return null;
    }

    private RiskResult checkY2038(Connection conn, TaskExecution execution, TableInfo table, 
                                  ColumnInfo column, CheckTask task,
                                  boolean isLargeTable, String primaryKey) throws SQLException {
        // TIMESTAMP type has Y2038 problem (max: 2038-01-19 03:14:07)
        String sql;
        if (isLargeTable && primaryKey != null) {
            // For large tables, sample by ordering on primary key
            sql = String.format("SELECT MAX(`%s`) as max_val FROM " +
                    "(SELECT `%s` FROM `%s`.`%s` ORDER BY `%s` DESC LIMIT %d) AS sample",
                    column.columnName, column.columnName, 
                    table.database, table.tableName, primaryKey, task.getSampleSize());
        } else {
            sql = String.format("SELECT MAX(`%s`) as max_val FROM `%s`.`%s`",
                    column.columnName, table.database, table.tableName);
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                Timestamp maxVal = rs.getTimestamp("max_val");
                if (maxVal != null) {
                    int year = maxVal.toLocalDateTime().getYear();
                    if (year >= task.getY2038WarningYear()) {
                        String detail = isLargeTable ?
                                String.format("抽样检查(最新%d条): 最大时间=%s, 已接近2038年限制", task.getSampleSize(), maxVal) :
                                String.format("当前最大时间: %s, 已接近2038年限制", maxVal);
                        return RiskResult.builder()
                                .execution(execution)
                                .databaseName(table.database)
                                .tableName(table.tableName)
                                .columnName(column.columnName)
                                .columnType(column.columnType)
                                .riskType(RiskType.Y2038)
                                .currentValue(maxVal.toString())
                                .thresholdValue("2038-01-19 03:14:07")
                                .detail(detail)
                                .suggestion("建议将TIMESTAMP类型改为DATETIME类型")
                                .status(RiskStatus.PENDING)
                                .build();
                    }
                }
            }
        }
        return null;
    }

    private RiskResult checkDecimalOverflow(Connection conn, TaskExecution execution, TableInfo table, 
                                            ColumnInfo column, CheckTask task,
                                            boolean isLargeTable, String primaryKey) throws SQLException {
        if (column.numericPrecision == null || column.numericScale == null) return null;
        
        int intDigits = column.numericPrecision - column.numericScale;
        BigDecimal maxAllowed = BigDecimal.TEN.pow(intDigits).subtract(BigDecimal.ONE);

        String sql;
        if (isLargeTable && primaryKey != null) {
            // For large tables, sample by ordering on primary key
            sql = String.format("SELECT MAX(ABS(`%s`)) as max_val FROM " +
                    "(SELECT `%s` FROM `%s`.`%s` ORDER BY `%s` DESC LIMIT %d) AS sample",
                    column.columnName, column.columnName, 
                    table.database, table.tableName, primaryKey, task.getSampleSize());
        } else {
            sql = String.format("SELECT MAX(ABS(`%s`)) as max_val FROM `%s`.`%s`",
                    column.columnName, table.database, table.tableName);
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                BigDecimal maxVal = rs.getBigDecimal("max_val");
                if (maxVal != null && maxVal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal usage = maxVal.multiply(BigDecimal.valueOf(100))
                            .divide(maxAllowed, 2, BigDecimal.ROUND_HALF_UP);
                    
                    if (usage.compareTo(BigDecimal.valueOf(task.getThresholdPct())) >= 0) {
                        String detail = isLargeTable ?
                                String.format("抽样检查(最新%d条): 最大值=%s, 类型最大值=%s", task.getSampleSize(), maxVal, maxAllowed) :
                                String.format("当前最大值: %s, 类型最大值: %s", maxVal, maxAllowed);
                        return RiskResult.builder()
                                .execution(execution)
                                .databaseName(table.database)
                                .tableName(table.tableName)
                                .columnName(column.columnName)
                                .columnType(column.columnType)
                                .riskType(RiskType.DECIMAL_OVERFLOW)
                                .currentValue(maxVal.toString())
                                .thresholdValue(maxAllowed.toString())
                                .usagePercent(usage)
                                .detail(detail)
                                .suggestion(String.format("建议扩展精度，如: DECIMAL(%d,%d)", 
                                        column.numericPrecision + 5, column.numericScale))
                                .status(RiskStatus.PENDING)
                                .build();
                    }
                }
            }
        }
        return null;
    }

    private boolean matchPattern(String value, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        
        // Support comma-separated values
        if (pattern.contains(",")) {
            for (String p : pattern.split(",")) {
                if (matchSinglePattern(value, p.trim())) return true;
            }
            return false;
        }
        return matchSinglePattern(value, pattern);
    }

    private boolean matchSinglePattern(String value, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        
        // Convert wildcard to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        
        try {
            return Pattern.matches("(?i)" + regex, value);
        } catch (Exception e) {
            return value.equalsIgnoreCase(pattern);
        }
    }

    private boolean isIntegerType(String dataType) {
        return dataType.matches("(?i)(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT)");
    }

    private boolean isTimestampType(String dataType) {
        return dataType.equalsIgnoreCase("TIMESTAMP");
    }

    private boolean isDecimalType(String dataType) {
        return dataType.matches("(?i)(DECIMAL|NUMERIC)");
    }

    private String getSuggestion(String currentType, String fullType) {
        switch (currentType.toUpperCase()) {
            case "TINYINT": return "建议升级为SMALLINT或INT类型";
            case "SMALLINT": return "建议升级为INT类型";
            case "MEDIUMINT": return "建议升级为INT类型";
            case "INT": return "建议升级为BIGINT类型";
            case "BIGINT": return "已是最大整型，建议考虑分表或使用字符串存储";
            default: return "建议扩展字段容量";
        }
    }

    /**
     * Format row count to human readable string
     */
    private String formatRowCount(long count) {
        if (count >= 100_000_000) {
            return String.format("%.1f亿", count / 100_000_000.0);
        } else if (count >= 10_000) {
            return String.format("%.1f万", count / 10_000.0);
        } else {
            return String.valueOf(count);
        }
    }

    /**
     * Get primary key column name for a table
     * Returns the first primary key column, or null if no primary key exists
     */
    private String getPrimaryKeyColumn(Connection conn, String database, String tableName) {
        String sql = String.format(
            "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' AND CONSTRAINT_NAME = 'PRIMARY' " +
            "ORDER BY ORDINAL_POSITION LIMIT 1",
            database, tableName);
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString("COLUMN_NAME");
            }
        } catch (SQLException e) {
            log.warn("Failed to get primary key for {}.{}: {}", database, tableName, e.getMessage());
        }
        return null;
    }

    private static class TableInfo {
        String database;
        String tableName;
        long rowCount;
    }

    private static class ColumnInfo {
        String columnName;
        String dataType;
        String columnType;
        Integer numericPrecision;
        Integer numericScale;
        Long charMaxLength;
    }
}
