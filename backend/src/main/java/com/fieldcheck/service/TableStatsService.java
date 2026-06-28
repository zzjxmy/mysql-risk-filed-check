package com.fieldcheck.service;

import com.fieldcheck.dto.TableStatsDTO;
import com.fieldcheck.entity.DbConnection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TableStatsService {

    private final ConnectionService connectionService;
    private static final String[] EXPORT_HEADERS = {
            "库名", "表名", "表备注", "数据量", "数据大小(M)", "索引大小(M)", "总大小(M)", "碎片大小(M)", "碎片率(%)", "表引擎"
    };

    public List<TableStatsDTO> getTableStats(Long connectionId, String schema, String keyword, Double minFragmentMb) {
        DbConnection dbConnection = connectionService.getConnection(connectionId);
        if (!Boolean.TRUE.equals(dbConnection.getEnabled())) {
            throw new RuntimeException("数据库连接未启用");
        }

        QueryPlan queryPlan = buildQueryPlan(schema, keyword, minFragmentMb);
        String url = String.format(
                "jdbc:mysql://%s:%d/information_schema?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=10000&socketTimeout=60000",
                dbConnection.getHost(),
                dbConnection.getPort()
        );

        try (Connection connection = DriverManager.getConnection(
                url,
                dbConnection.getUsername(),
                connectionService.getDecryptedPassword(connectionId));
             PreparedStatement statement = connection.prepareStatement(queryPlan.getSql())) {

            for (int i = 0; i < queryPlan.getParameters().size(); i++) {
                statement.setObject(i + 1, queryPlan.getParameters().get(i));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                List<TableStatsDTO> stats = new ArrayList<>();
                while (resultSet.next()) {
                    stats.add(mapRow(resultSet));
                }
                return stats;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询表空间信息失败: " + e.getMessage(), e);
        }
    }

    public static byte[] exportToExcel(List<TableStatsDTO> stats) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("表空间分析");
            org.apache.poi.xssf.usermodel.XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(EXPORT_HEADERS[i]);
            }

            for (int i = 0; i < stats.size(); i++) {
                TableStatsDTO stat = stats.get(i);
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(valueOrEmpty(stat.getSchemaName()));
                row.createCell(1).setCellValue(valueOrEmpty(stat.getTableName()));
                row.createCell(2).setCellValue(valueOrEmpty(stat.getTableComment()));
                row.createCell(3).setCellValue(valueOrZero(stat.getTableRows()));
                row.createCell(4).setCellValue(valueOrZero(stat.getDataSizeMb()));
                row.createCell(5).setCellValue(valueOrZero(stat.getIndexSizeMb()));
                row.createCell(6).setCellValue(valueOrZero(stat.getTotalSizeMb()));
                row.createCell(7).setCellValue(valueOrZero(stat.getFragmentSizeMb()));
                row.createCell(8).setCellValue(valueOrZero(stat.getFragmentPercent()));
                row.createCell(9).setCellValue(valueOrEmpty(stat.getEngine()));
            }

            int[] widths = {18, 28, 32, 14, 16, 16, 16, 16, 14, 14};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static QueryPlan buildQueryPlan(String schema, String keyword, Double minFragmentMb) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        sql.append("SELECT ")
                .append("table_schema AS schema_name, ")
                .append("table_name, ")
                .append("table_comment, ")
                .append("table_rows, ")
                .append("ROUND(data_length / (1024 * 1024), 2) AS data_size_mb, ")
                .append("ROUND(index_length / (1024 * 1024), 2) AS index_size_mb, ")
                .append("ROUND((data_length + index_length) / (1024 * 1024), 2) AS total_size_mb, ")
                .append("ROUND(data_free / (1024 * 1024), 2) AS fragment_size_mb, ")
                .append("CASE WHEN data_length > 0 THEN ROUND(data_free / data_length * 100, 2) ELSE 0 END AS fragment_percent, ")
                .append("engine ")
                .append("FROM information_schema.TABLES ")
                .append("WHERE table_schema NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys') ")
                .append("AND table_type = 'BASE TABLE' ");

        if (StringUtils.hasText(schema)) {
            sql.append("AND table_schema = ? ");
            parameters.add(schema.trim());
        }

        if (StringUtils.hasText(keyword)) {
            sql.append("AND (table_schema LIKE ? OR table_name LIKE ?) ");
            String likeKeyword = "%" + keyword.trim() + "%";
            parameters.add(likeKeyword);
            parameters.add(likeKeyword);
        }

        if (minFragmentMb != null) {
            sql.append("AND data_free / (1024 * 1024) >= ? ");
            parameters.add(minFragmentMb);
        }

        sql.append("ORDER BY data_free DESC");
        return new QueryPlan(sql.toString(), parameters);
    }

    private TableStatsDTO mapRow(ResultSet resultSet) throws SQLException {
        return TableStatsDTO.builder()
                .schemaName(resultSet.getString("schema_name"))
                .tableName(resultSet.getString("table_name"))
                .tableComment(resultSet.getString("table_comment"))
                .tableRows(resultSet.getLong("table_rows"))
                .dataSizeMb(resultSet.getDouble("data_size_mb"))
                .indexSizeMb(resultSet.getDouble("index_size_mb"))
                .totalSizeMb(resultSet.getDouble("total_size_mb"))
                .fragmentSizeMb(resultSet.getDouble("fragment_size_mb"))
                .fragmentPercent(resultSet.getDouble("fragment_percent"))
                .engine(resultSet.getString("engine"))
                .build();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double valueOrZero(Number value) {
        return value == null ? 0 : value.doubleValue();
    }

    @Data
    @AllArgsConstructor
    static class QueryPlan {
        private String sql;
        private List<Object> parameters;
    }
}
