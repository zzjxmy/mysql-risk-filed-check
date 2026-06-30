package com.fieldcheck.dto;

import com.fieldcheck.entity.TaskStatus;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class ArchiveTaskDTO {
    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String taskMode;

    @NotNull(message = "源数据库连接不能为空")
    private Long sourceConnectionId;

    private String sourceConnectionName;

    @NotNull(message = "目标数据库连接不能为空")
    private Long destConnectionId;

    private String destConnectionName;

    private String cronExpression;
    private TaskStatus status;
    private String remark;
    private Set<Long> alertConfigIds;
    private List<ArchiveTaskVariableDTO> variables = new ArrayList<>();
    private List<ArchiveTaskStepDTO> steps = new ArrayList<>();
    private ArchiveBatchConfigDTO batchConfig;
}
