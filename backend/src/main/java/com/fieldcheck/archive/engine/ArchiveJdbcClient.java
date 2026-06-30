package com.fieldcheck.archive.engine;

import com.fieldcheck.entity.DbConnection;

import java.util.List;

public interface ArchiveJdbcClient {
    String querySingleValue(DbConnection connectionConfig, String querySql) throws Exception;

    List<List<Object>> queryRows(DbConnection connectionConfig, String querySql, Integer maxRows) throws Exception;

    void executeUpdate(DbConnection connectionConfig, String sql) throws Exception;

    void insertRows(DbConnection connectionConfig, String sql, List<List<Object>> rows) throws Exception;
}
