package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "risk_result", indexes = {
    @Index(name = "idx_execution_id", columnList = "execution_id"),
    @Index(name = "idx_risk_type", columnList = "risk_type"),
    @Index(name = "idx_status", columnList = "status")
})
@EqualsAndHashCode(callSuper = true, exclude = {"execution"})
public class RiskResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private TaskExecution execution;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;

    @Column(name = "column_type", length = 100)
    private String columnType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", nullable = false, length = 30)
    private RiskType riskType;

    @Column(name = "current_value", length = 500)
    private String currentValue;

    @Column(name = "threshold_value", length = 500)
    private String thresholdValue;

    @Column(name = "usage_percent", precision = 5, scale = 2)
    private BigDecimal usagePercent;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RiskStatus status = RiskStatus.PENDING;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;
}
