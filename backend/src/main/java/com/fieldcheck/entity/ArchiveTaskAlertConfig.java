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
@Table(name = "archive_task_alert_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_archive_task_alert", columnNames = {"task_id", "alert_config_id"})
})
@EqualsAndHashCode(callSuper = true, exclude = {"task", "alertConfig"})
public class ArchiveTaskAlertConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ArchiveTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_config_id", nullable = false)
    private AlertConfig alertConfig;
}
