package com.fieldcheck.service;

import com.fieldcheck.dto.TaskDTO;
import com.fieldcheck.entity.*;
import com.fieldcheck.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final CheckTaskRepository taskRepository;
    private final DbConnectionRepository connectionRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final SysUserRepository userRepository;
    private final TaskExecutionRepository executionRepository;

    public Page<CheckTask> getTasks(String name, TaskStatus status, Long connectionId, Pageable pageable) {
        return taskRepository.findByConditions(name, status, connectionId, pageable);
    }

    public CheckTask getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在"));
    }

    public List<CheckTask> getScheduledTasks() {
        return taskRepository.findByStatusAndCronExpressionIsNotNull(TaskStatus.ENABLED);
    }

    @Transactional
    public CheckTask createTask(TaskDTO dto, String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        DbConnection connection = connectionRepository.findById(dto.getConnectionId())
                .orElseThrow(() -> new RuntimeException("数据库连接不存在"));

        Set<AlertConfig> alertConfigs = new HashSet<>();
        if (dto.getAlertConfigIds() != null && !dto.getAlertConfigIds().isEmpty()) {
            alertConfigs = dto.getAlertConfigIds().stream()
                    .map(id -> alertConfigRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("告警配置不存在: " + id)))
                    .collect(Collectors.toSet());
        }

        CheckTask task = CheckTask.builder()
                .name(dto.getName())
                .connection(connection)
                .dbPattern(dto.getDbPattern())
                .tablePattern(dto.getTablePattern())
                .fullScan(dto.getFullScan() != null ? dto.getFullScan() : false)
                .sampleSize(dto.getSampleSize() != null ? dto.getSampleSize() : 1000)
                .maxTableRows(dto.getMaxTableRows() != null ? dto.getMaxTableRows() : 1000000L)
                .thresholdPct(dto.getThresholdPct() != null ? dto.getThresholdPct() : 90)
                .y2038WarningYear(dto.getY2038WarningYear() != null ? dto.getY2038WarningYear() : 2030)
                .whitelistType(dto.getWhitelistType() != null ? dto.getWhitelistType() : WhitelistType.NONE)
                .customWhitelist(dto.getCustomWhitelist())
                .cronExpression(dto.getCronExpression())
                .status(dto.getStatus() != null ? dto.getStatus() : TaskStatus.ENABLED)
                .createdBy(user)
                .alertConfigs(alertConfigs)
                .build();

        return taskRepository.save(task);
    }

    @Transactional
    public CheckTask updateTask(Long id, TaskDTO dto) {
        CheckTask task = getTask(id);

        if (dto.getConnectionId() != null) {
            DbConnection connection = connectionRepository.findById(dto.getConnectionId())
                    .orElseThrow(() -> new RuntimeException("数据库连接不存在"));
            task.setConnection(connection);
        }

        task.setName(dto.getName());
        task.setDbPattern(dto.getDbPattern());
        task.setTablePattern(dto.getTablePattern());
        
        if (dto.getFullScan() != null) task.setFullScan(dto.getFullScan());
        if (dto.getSampleSize() != null) task.setSampleSize(dto.getSampleSize());
        if (dto.getMaxTableRows() != null) task.setMaxTableRows(dto.getMaxTableRows());
        if (dto.getThresholdPct() != null) task.setThresholdPct(dto.getThresholdPct());
        if (dto.getY2038WarningYear() != null) task.setY2038WarningYear(dto.getY2038WarningYear());
        if (dto.getWhitelistType() != null) task.setWhitelistType(dto.getWhitelistType());
        
        task.setCustomWhitelist(dto.getCustomWhitelist());
        task.setCronExpression(dto.getCronExpression());
        
        if (dto.getStatus() != null) task.setStatus(dto.getStatus());

        if (dto.getAlertConfigIds() != null) {
            Set<AlertConfig> alertConfigs = dto.getAlertConfigIds().stream()
                    .map(alertId -> alertConfigRepository.findById(alertId)
                            .orElseThrow(() -> new RuntimeException("告警配置不存在: " + alertId)))
                    .collect(Collectors.toSet());
            task.setAlertConfigs(alertConfigs);
        }

        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        CheckTask task = getTask(id);
        // Check if there are running executions
        List<TaskExecution> runningExecutions = executionRepository.findByTaskIdAndStatus(id, ExecutionStatus.RUNNING);
        if (!runningExecutions.isEmpty()) {
            throw new RuntimeException("任务正在执行中，无法删除");
        }
        taskRepository.delete(task);
    }

    public TaskDTO toDTO(CheckTask task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setConnectionId(task.getConnection().getId());
        dto.setConnectionName(task.getConnection().getName());
        dto.setDbPattern(task.getDbPattern());
        dto.setTablePattern(task.getTablePattern());
        dto.setFullScan(task.getFullScan());
        dto.setSampleSize(task.getSampleSize());
        dto.setMaxTableRows(task.getMaxTableRows());
        dto.setThresholdPct(task.getThresholdPct());
        dto.setY2038WarningYear(task.getY2038WarningYear());
        dto.setWhitelistType(task.getWhitelistType());
        dto.setCustomWhitelist(task.getCustomWhitelist());
        dto.setCronExpression(task.getCronExpression());
        dto.setStatus(task.getStatus());
        if (task.getAlertConfigs() != null) {
            dto.setAlertConfigIds(task.getAlertConfigs().stream()
                    .map(AlertConfig::getId)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}
