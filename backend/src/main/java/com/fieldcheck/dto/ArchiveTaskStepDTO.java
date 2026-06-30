package com.fieldcheck.dto;

import lombok.Data;

@Data
public class ArchiveTaskStepDTO {
    private Long id;
    private String name;
    private String stepMode;
    private String sourceDatabase;
    private String sourceTable;
    private String indexName;
    private String destDatabase;
    private String destTable;
    private String whereTemplate;
    private Boolean deleteSource;
    private String charset;
    private Integer limitSize;
    private Integer progressSize;
    private Boolean bulkInsert;
    private Boolean commitEach;
    private String extraOptions;
    private Integer sortOrder;
    private Boolean enabled;
}
