package com.fieldcheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableStatsDTO {
    private String schemaName;
    private String tableName;
    private String tableComment;
    private Long tableRows;
    private Double dataSizeMb;
    private Double indexSizeMb;
    private Double totalSizeMb;
    private Double fragmentSizeMb;
    private Double fragmentPercent;
    private String engine;
}
