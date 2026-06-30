package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "archive_task")
@EqualsAndHashCode(callSuper = true, exclude = {"sourceConnection", "destConnection", "createdBy", "variables", "steps", "batchConfig"})
public class ArchiveTask extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "task_mode", nullable = false, length = 30)
    @Builder.Default
    private String taskMode = "NORMAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_connection_id", nullable = false)
    private DbConnection sourceConnection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_connection_id", nullable = false)
    private DbConnection destConnection;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.ENABLED;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SysUser createdBy;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private Set<ArchiveTaskVariable> variables = new LinkedHashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private Set<ArchiveTaskStep> steps = new LinkedHashSet<>();

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private ArchiveBatchConfig batchConfig;
}
