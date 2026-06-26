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
@Table(name = "archive_task_variable")
@EqualsAndHashCode(callSuper = true, exclude = {"task"})
public class ArchiveTaskVariable extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ArchiveTask task;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "query_sql", nullable = false, columnDefinition = "TEXT")
    private String querySql;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
