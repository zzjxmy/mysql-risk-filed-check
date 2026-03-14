package com.fieldcheck.dto;

import com.fieldcheck.entity.RiskStatus;
import com.fieldcheck.entity.RiskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskResultDTO {
    private Long id;
    private Long executionId;
    private String databaseName;
    private String tableName;
    private String columnName;
    private String columnType;
    private RiskType riskType;
    private String riskTypeDesc;
    private String currentValue;
    private String thresholdValue;
    private BigDecimal usagePercent;
    private String detail;
    private String suggestion;
    private RiskStatus status;
    private String remark;
    private LocalDateTime createdAt;
}
