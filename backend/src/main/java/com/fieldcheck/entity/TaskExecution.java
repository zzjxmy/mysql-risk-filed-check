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
@Table(name = "task_execution")
@EqualsAndHashCode(callSuper = true, exclude = {"task"})
public class TaskExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private CheckTask task;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "total_tables")
    @Builder.Default
    private Integer totalTables = 0;

    @Column(name = "processed_tables")
    @Builder.Default
    private Integer processedTables = 0;

    @Column(name = "risk_count")
    @Builder.Default
    private Integer riskCount = 0;

    @Column(name = "log_path", length = 500)
    private String logPath;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "trigger_type", length = 20)
    @Builder.Default
    private String triggerType = "MANUAL";  // MANUAL or SCHEDULED
}
