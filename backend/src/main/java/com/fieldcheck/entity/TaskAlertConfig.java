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
@Table(name = "task_alert_config")
@EqualsAndHashCode(callSuper = true)
public class TaskAlertConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private CheckTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_config_id", nullable = false)
    private AlertConfig alertConfig;
}
