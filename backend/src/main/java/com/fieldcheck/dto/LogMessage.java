package com.fieldcheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogMessage {
    private Long executionId;
    private LocalDateTime timestamp;
    private String level;  // INFO, WARN, ERROR
    private String message;
    private String currentTable;
    private Integer processedTables;
    private Integer totalTables;
    private Integer progressPercent;
}
