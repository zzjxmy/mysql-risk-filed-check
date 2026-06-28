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
@Table(name = "archive_task_step")
@EqualsAndHashCode(callSuper = true, exclude = {"task"})
public class ArchiveTaskStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ArchiveTask task;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "source_database", nullable = false, length = 100)
    private String sourceDatabase;

    @Column(name = "source_table", nullable = false, length = 100)
    private String sourceTable;

    @Column(name = "dest_database", nullable = false, length = 100)
    private String destDatabase;

    @Column(name = "dest_table", nullable = false, length = 100)
    private String destTable;

    @Column(name = "where_template", nullable = false, columnDefinition = "TEXT")
    private String whereTemplate;

    @Column(name = "delete_source", nullable = false)
    @Builder.Default
    private Boolean deleteSource = true;

    @Column(length = 50)
    @Builder.Default
    private String charset = "UTF8";

    @Column(name = "limit_size")
    @Builder.Default
    private Integer limitSize = 5000;

    @Column(name = "progress_size")
    @Builder.Default
    private Integer progressSize = 5000;

    @Column(name = "bulk_insert", nullable = false)
    @Builder.Default
    private Boolean bulkInsert = false;

    @Column(name = "commit_each", nullable = false)
    @Builder.Default
    private Boolean commitEach = true;

    @Column(name = "extra_options", columnDefinition = "TEXT")
    private String extraOptions;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
