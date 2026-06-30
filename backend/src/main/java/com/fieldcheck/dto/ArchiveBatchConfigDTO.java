package com.fieldcheck.dto;

import lombok.Data;

@Data
public class ArchiveBatchConfigDTO {
    private Long id;
    private Long queryConnectionId;
    private String queryConnectionName;
    private Long targetConnectionId;
    private String targetConnectionName;
    /**
     * Kept for older seed/UI payloads. New batch archive tasks should use
     * queryConnectionId and targetConnectionId explicitly.
     */
    private Long connectionId;
    private String connectionName;
    private String batchQuery;
    private String targetDatabase;
    private String targetTable;
    private String truncateSql;
    private String loadSql;
    private Integer batchSize;
    private Integer maxRounds;
    private Boolean enabled;
}
