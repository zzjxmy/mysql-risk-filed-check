package com.fieldcheck.dto;

import com.fieldcheck.entity.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionDTO {
    private Long id;
    private Long taskId;
    private String taskName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ExecutionStatus status;
    private Integer totalTables;
    private Integer processedTables;
    private Integer riskCount;
    private String logPath;
    private String errorMessage;
    private String triggerType;
    private Integer progressPercent;
}
