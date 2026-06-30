package com.fieldcheck.dto;

import lombok.Data;

@Data
public class ArchiveTaskVariableDTO {
    private Long id;
    private String name;
    private String querySql;
    private Long connectionId;
    private String connectionName;
    private Integer sortOrder;
    private Boolean enabled;
}
