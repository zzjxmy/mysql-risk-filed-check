package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "archive_batch_config")
@EqualsAndHashCode(callSuper = true, exclude = {"task", "queryConnection", "targetConnection"})
public class ArchiveBatchConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private ArchiveTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_connection_id", nullable = false)
    private DbConnection queryConnection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_connection_id", nullable = false)
    private DbConnection targetConnection;

    @Column(name = "batch_query", nullable = false, columnDefinition = "TEXT")
    private String batchQuery;

    @Column(name = "target_database", nullable = false, length = 100)
    private String targetDatabase;

    @Column(name = "target_table", nullable = false, length = 100)
    private String targetTable;

    @Column(name = "truncate_sql", nullable = false, columnDefinition = "TEXT")
    private String truncateSql;

    @Column(name = "load_sql", nullable = false, columnDefinition = "TEXT")
    private String loadSql;

    @Column(name = "batch_size", nullable = false)
    @Builder.Default
    private Integer batchSize = 2000;

    @Column(name = "max_rounds")
    private Integer maxRounds;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
