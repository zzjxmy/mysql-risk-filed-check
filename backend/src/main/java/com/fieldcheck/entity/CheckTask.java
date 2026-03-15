package com.fieldcheck.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "check_task")
@EqualsAndHashCode(callSuper = true, exclude = {"connection", "createdBy"})
public class CheckTask extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private DbConnection connection;

    @Column(name = "db_pattern", length = 500)
    private String dbPattern;  // 数据库匹配模式，支持正则

    @Column(name = "table_pattern", length = 500)
    private String tablePattern;  // 表匹配模式，支持正则

    @Column(name = "full_scan")
    @Builder.Default
    private Boolean fullScan = false;  // 是否强制全表扫描

    @Column(name = "sample_size")
    @Builder.Default
    private Integer sampleSize = 1000;  // 抽样条数

    @Column(name = "max_table_rows")
    @Builder.Default
    private Long maxTableRows = 1000000L;  // 大表阈值

    @Column(name = "threshold_pct")
    @Builder.Default
    private Integer thresholdPct = 90;  // 风险阈值百分比

    @Column(name = "y2038_warning_year")
    @Builder.Default
    private Integer y2038WarningYear = 2030;  // Y2038告警年份

    @Enumerated(EnumType.STRING)
    @Column(name = "whitelist_type", length = 20)
    @Builder.Default
    private WhitelistType whitelistType = WhitelistType.NONE;

    @Column(name = "custom_whitelist", columnDefinition = "TEXT")
    private String customWhitelist;  // 自定义白名单内容

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;  // Cron表达式

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.ENABLED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SysUser createdBy;
}
