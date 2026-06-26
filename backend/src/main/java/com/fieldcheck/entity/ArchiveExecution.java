package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "archive_execution")
@EqualsAndHashCode(callSuper = true, exclude = {"task"})
public class ArchiveExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ArchiveTask task;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "total_steps")
    @Builder.Default
    private Integer totalSteps = 0;

    @Column(name = "processed_steps")
    @Builder.Default
    private Integer processedSteps = 0;

    @Column(name = "skipped_steps")
    @Builder.Default
    private Integer skippedSteps = 0;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "variable_snapshot", columnDefinition = "TEXT")
    private String variableSnapshot;

    @Column(name = "log_path", length = 500)
    private String logPath;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "trigger_type", length = 20)
    @Builder.Default
    private String triggerType = "MANUAL";
}
