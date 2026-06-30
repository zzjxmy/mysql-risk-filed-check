package com.fieldcheck.archive.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveCommandSpec {
    private String ptArchiverPath;
    private String stepMode;
    private String sourceHost;
    private Integer sourcePort;
    private String sourceUsername;
    private String sourcePassword;
    private String sourceDatabase;
    private String sourceTable;
    private String sourceIndexName;
    private String destHost;
    private Integer destPort;
    private String destUsername;
    private String destPassword;
    private String destDatabase;
    private String destTable;
    private String whereClause;
    private String charset;
    private Integer limitSize;
    private Integer progressSize;
    private Boolean deleteSource;
    private Boolean bulkInsert;
    private Boolean commitEach;
    private Boolean dryRun;
    @Builder.Default
    private List<String> extraOptions = new ArrayList<>();
}
