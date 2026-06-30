package com.fieldcheck.archive.engine;

import com.fieldcheck.entity.DbConnection;
import com.fieldcheck.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MysqlArchiveJdbcClient implements ArchiveJdbcClient {

    private final ConnectionService connectionService;

    @Override
    public String querySingleValue(DbConnection connectionConfig, String querySql) throws Exception {
        try (Connection connection = openConnection(connectionConfig);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(querySql)) {
            if (!resultSet.next()) {
                return null;
            }
            Object value = resultSet.getObject(1);
            return value == null ? null : String.valueOf(value);
        }
    }

    @Override
    public List<List<Object>> queryRows(DbConnection connectionConfig, String querySql, Integer maxRows) throws Exception {
        try (Connection connection = openConnection(connectionConfig);
             Statement statement = connection.createStatement()) {
            if (maxRows != null && maxRows > 0) {
                statement.setMaxRows(maxRows);
            }
            try (ResultSet resultSet = statement.executeQuery(querySql)) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<List<Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(resultSet.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    @Override
    public void executeUpdate(DbConnection connectionConfig, String sql) throws Exception {
        try (Connection connection = openConnection(connectionConfig);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    @Override
    public void insertRows(DbConnection connectionConfig, String sql, List<List<Object>> rows) throws Exception {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        try (Connection connection = openConnection(connectionConfig);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (List<Object> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    statement.setObject(i + 1, row.get(i));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Connection openConnection(DbConnection connectionConfig) throws SQLException {
        String password = connectionService.getDecryptedPassword(connectionConfig.getId());
        String jdbcUrl = String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=30000&socketTimeout=300000",
                connectionConfig.getHost(), connectionConfig.getPort());
        return DriverManager.getConnection(jdbcUrl, connectionConfig.getUsername(), password);
    }
}
