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
        
        String jdbcUrl = String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=Asia/Shanghai",
                dbConnection.getHost(), dbConnection.getPort());
        String password = connectionService.getDecryptedPassword(dbConnection.getId());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbConnection.getUsername(), password)) {
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
                    logCallback.accept(execution.getId(), String.format("INFO|跳过白名单表: %s.%s", table.database, table.tableName));
                    processed++;
                    continue;
                }

                logCallback.accept(execution.getId(), String.format("INFO|检查表: %s.%s", table.database, table.tableName));

                // Get columns
                List<ColumnInfo> columns = getColumns(conn, table.database, table.tableName);
                
                for (ColumnInfo column : columns) {
                    if (stopCheck.get()) return;
                    
                    // Check whitelist for field
                    if (whitelistService.isWhitelisted(table.database, table.tableName, column.columnName, task)) {
                        continue;
                    }

                    List<RiskResult> risks = checkColumn(conn, execution, table, column, task, logCallback);
                    if (!risks.isEmpty()) {
                        riskResultRepository.saveAll(risks);
                        riskCount += risks.size();
                    }
                }

                processed++;
                execution.setProcessedTables(processed);
                execution.setRiskCount(riskCount);
                
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
    
    @Transactional
    public void saveExecution(TaskExecution execution) {
        executionRepository.save(execution);
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
                                          ColumnInfo column, CheckTask task, BiConsumer<Long, String> logCallback) {
        List<RiskResult> risks = new ArrayList<>();
        
        try {
            // Check integer overflow
            if (isIntegerType(column.dataType)) {
                RiskResult risk = checkIntegerOverflow(conn, execution, table, column, task);
                if (risk != null) {
                    risks.add(risk);
                    logCallback.accept(execution.getId(), 
                            String.format("WARN|发现整型溢出风险: %s.%s.%s", table.database, table.tableName, column.columnName));
                }
            }
            
            // Check Y2038 problem
            if (isTimestampType(column.dataType)) {
                RiskResult risk = checkY2038(conn, execution, table, column, task);
                if (risk != null) {
                    risks.add(risk);
                    logCallback.accept(execution.getId(), 
                            String.format("WARN|发现Y2038风险: %s.%s.%s", table.database, table.tableName, column.columnName));
                }
            }
            
            // Check decimal overflow
            if (isDecimalType(column.dataType)) {
                RiskResult risk = checkDecimalOverflow(conn, execution, table, column, task);
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
                                            ColumnInfo column, CheckTask task) throws SQLException {
        String typeKey = column.columnType.contains("UNSIGNED") 
                ? column.dataType + " UNSIGNED" 
                : column.dataType;
        
        BigInteger maxAllowed = INT_MAX_VALUES.get(typeKey);
        BigInteger minAllowed = INT_MIN_VALUES.getOrDefault(typeKey, BigInteger.ZERO);
        
        if (maxAllowed == null) return null;

        // Get actual max/min values
        String sql = String.format("SELECT MAX(`%s`) as max_val, MIN(`%s`) as min_val FROM `%s`.`%s`",
                column.columnName, column.columnName, table.database, table.tableName);
        
        // For large tables, use sampling
        if (table.rowCount > task.getMaxTableRows() && !task.getFullScan()) {
            sql = String.format("SELECT MAX(`%s`) as max_val, MIN(`%s`) as min_val FROM `%s`.`%s` ORDER BY RAND() LIMIT %d",
                    column.columnName, column.columnName, table.database, table.tableName, task.getSampleSize());
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
                            .detail(String.format("当前最大值: %s, 类型最大值: %s", maxVal, maxAllowed))
                            .suggestion(getSuggestion(column.dataType, typeKey))
                            .status(RiskStatus.PENDING)
                            .build();
                }
            }
        }
        return null;
    }

    private RiskResult checkY2038(Connection conn, TaskExecution execution, TableInfo table, 
                                  ColumnInfo column, CheckTask task) throws SQLException {
        // TIMESTAMP type has Y2038 problem (max: 2038-01-19 03:14:07)
        String sql = String.format("SELECT MAX(`%s`) as max_val FROM `%s`.`%s`",
                column.columnName, table.database, table.tableName);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                Timestamp maxVal = rs.getTimestamp("max_val");
                if (maxVal != null) {
                    int year = maxVal.toLocalDateTime().getYear();
                    if (year >= task.getY2038WarningYear()) {
                        return RiskResult.builder()
                                .execution(execution)
                                .databaseName(table.database)
                                .tableName(table.tableName)
                                .columnName(column.columnName)
                                .columnType(column.columnType)
                                .riskType(RiskType.Y2038)
                                .currentValue(maxVal.toString())
                                .thresholdValue("2038-01-19 03:14:07")
                                .detail(String.format("当前最大时间: %s, 已接近2038年限制", maxVal))
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
                                            ColumnInfo column, CheckTask task) throws SQLException {
        if (column.numericPrecision == null || column.numericScale == null) return null;
        
        int intDigits = column.numericPrecision - column.numericScale;
        BigDecimal maxAllowed = BigDecimal.TEN.pow(intDigits).subtract(BigDecimal.ONE);

        String sql = String.format("SELECT MAX(ABS(`%s`)) as max_val FROM `%s`.`%s`",
                column.columnName, table.database, table.tableName);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                BigDecimal maxVal = rs.getBigDecimal("max_val");
                if (maxVal != null && maxVal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal usage = maxVal.multiply(BigDecimal.valueOf(100))
                            .divide(maxAllowed, 2, BigDecimal.ROUND_HALF_UP);
                    
                    if (usage.compareTo(BigDecimal.valueOf(task.getThresholdPct())) >= 0) {
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
                                .detail(String.format("当前最大值: %s, 类型最大值: %s", maxVal, maxAllowed))
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
