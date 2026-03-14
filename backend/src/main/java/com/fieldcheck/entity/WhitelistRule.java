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
@Table(name = "whitelist_rule")
@EqualsAndHashCode(callSuper = true)
public class WhitelistRule extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String rule;  // 规则字符串，如 "db.*", "db.table.*", "db.table.field"

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private WhitelistRuleType ruleType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String remark;
}
