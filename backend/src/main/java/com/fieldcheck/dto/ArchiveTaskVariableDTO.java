package com.fieldcheck.dto;

import lombok.Data;

@Data
public class ArchiveTaskVariableDTO {
    private Long id;
    private String name;
    private String querySql;
    private Integer sortOrder;
    private Boolean enabled;
}
