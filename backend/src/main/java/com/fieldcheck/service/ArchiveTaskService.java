package com.fieldcheck.service;

import com.fieldcheck.archive.engine.ArchiveSqlValidator;
import com.fieldcheck.dto.ArchiveTaskDTO;
import com.fieldcheck.dto.ArchiveTaskStepDTO;
import com.fieldcheck.dto.ArchiveTaskVariableDTO;
import com.fieldcheck.entity.*;
import com.fieldcheck.repository.*;
import com.fieldcheck.scheduler.ArchiveTaskSchedulerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveTaskService {

    private final ArchiveTaskRepository taskRepository;
    private final DbConnectionRepository connectionRepository;
    private final SysUserRepository userRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final ArchiveTaskAlertConfigRepository alertConfigRepositoryRelation;
    private final ArchiveExecutionRepository executionRepository;
    private final ArchiveTaskSchedulerConfig schedulerConfig;

    public Page<ArchiveTask> getTasks(String name, TaskStatus status, Pageable pageable) {
        return taskRepository.findByConditions(name, status, pageable);
    }

    public ArchiveTask getTask(Long id) {
        return taskRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("归档任务不存在"));
    }

    @Transactional
    public ArchiveTask createTask(ArchiveTaskDTO dto, String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        ArchiveTask task = ArchiveTask.builder()
                .name(dto.getName())
                .sourceConnection(getConnection(dto.getSourceConnectionId(), "源数据库连接不存在"))
                .destConnection(getConnection(dto.getDestConnectionId(), "目标数据库连接不存在"))
                .cronExpression(dto.getCronExpression())
                .status(dto.getStatus() != null ? dto.getStatus() : TaskStatus.ENABLED)
                .remark(dto.getRemark())
                .createdBy(user)
                .build();
        applyVariablesAndSteps(task, dto);
        task = taskRepository.save(task);
        saveAlertConfigs(task, dto.getAlertConfigIds());
        scheduleIfNeeded(task);
        return task;
    }

    @Transactional
    public ArchiveTask updateTask(Long id, ArchiveTaskDTO dto) {
        ArchiveTask task = getTask(id);
        task.setName(dto.getName());
        task.setSourceConnection(getConnection(dto.getSourceConnectionId(), "源数据库连接不存在"));
        task.setDestConnection(getConnection(dto.getDestConnectionId(), "目标数据库连接不存在"));
        task.setCronExpression(dto.getCronExpression());
        task.setStatus(dto.getStatus() != null ? dto.getStatus() : TaskStatus.ENABLED);
        task.setRemark(dto.getRemark());
        task.getVariables().clear();
        task.getSteps().clear();
        applyVariablesAndSteps(task, dto);
        task = taskRepository.save(task);
        alertConfigRepositoryRelation.deleteByTaskId(task.getId());
        alertConfigRepositoryRelation.flush();
        saveAlertConfigs(task, dto.getAlertConfigIds());
        scheduleIfNeeded(task);
        return task;
    }

    @Transactional
    public void deleteTask(Long id) {
        List<ArchiveExecution> running = executionRepository.findByTaskIdAndStatus(id, ExecutionStatus.RUNNING);
        if (!running.isEmpty()) {
            throw new RuntimeException("归档任务正在执行中，无法删除");
        }
        try {
            schedulerConfig.unscheduleTask(id);
        } catch (Exception e) {
            log.error("Failed to unschedule archive task {}: {}", id, e.getMessage());
        }
        taskRepository.delete(getTask(id));
    }

    @Transactional(readOnly = true)
    public List<AlertConfig> getTaskAlertConfigs(Long taskId) {
        return alertConfigRepositoryRelation.findByTaskIdWithAlertConfig(taskId).stream()
                .map(ArchiveTaskAlertConfig::getAlertConfig)
                .filter(AlertConfig::getEnabled)
                .collect(Collectors.toList());
    }

    public ArchiveTaskDTO toDTO(ArchiveTask task) {
        ArchiveTaskDTO dto = new ArchiveTaskDTO();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setSourceConnectionId(task.getSourceConnection().getId());
        dto.setSourceConnectionName(task.getSourceConnection().getName());
        dto.setDestConnectionId(task.getDestConnection().getId());
        dto.setDestConnectionName(task.getDestConnection().getName());
        dto.setCronExpression(task.getCronExpression());
        dto.setStatus(task.getStatus());
        dto.setRemark(task.getRemark());
        dto.setAlertConfigIds(alertConfigRepositoryRelation.findByTaskId(task.getId()).stream()
                .map(config -> config.getAlertConfig().getId())
                .collect(Collectors.toSet()));
        dto.setVariables(task.getVariables().stream()
                .sorted(Comparator.comparing(v -> valueOrDefault(v.getSortOrder(), 0)))
                .map(this::toVariableDTO)
                .collect(Collectors.toList()));
        dto.setSteps(task.getSteps().stream()
                .sorted(Comparator.comparing(s -> valueOrDefault(s.getSortOrder(), 0)))
                .map(this::toStepDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private void applyVariablesAndSteps(ArchiveTask task, ArchiveTaskDTO dto) {
        if (dto.getVariables() != null) {
            int index = 0;
            for (ArchiveTaskVariableDTO variableDTO : dto.getVariables()) {
                if (Boolean.FALSE.equals(variableDTO.getEnabled())) {
                    continue;
                }
                ArchiveSqlValidator.validateSingleSelect(variableDTO.getQuerySql());
                task.getVariables().add(ArchiveTaskVariable.builder()
                        .task(task)
                        .name(variableDTO.getName())
                        .querySql(variableDTO.getQuerySql())
                        .sortOrder(variableDTO.getSortOrder() != null ? variableDTO.getSortOrder() : index)
                        .enabled(variableDTO.getEnabled() != null ? variableDTO.getEnabled() : true)
                        .build());
                index++;
            }
        }
        if (dto.getSteps() == null || dto.getSteps().isEmpty()) {
            throw new RuntimeException("至少需要配置一个归档步骤");
        }
        int index = 0;
        for (ArchiveTaskStepDTO stepDTO : dto.getSteps()) {
            ArchiveSqlValidator.validateWhereTemplate(stepDTO.getWhereTemplate());
            task.getSteps().add(ArchiveTaskStep.builder()
                    .task(task)
                    .name(stepDTO.getName())
                    .sourceDatabase(stepDTO.getSourceDatabase())
                    .sourceTable(stepDTO.getSourceTable())
                    .destDatabase(stepDTO.getDestDatabase())
                    .destTable(stepDTO.getDestTable())
                    .whereTemplate(stepDTO.getWhereTemplate())
                    .deleteSource(stepDTO.getDeleteSource() != null ? stepDTO.getDeleteSource() : true)
                    .charset(stepDTO.getCharset() != null ? stepDTO.getCharset() : "UTF8")
                    .limitSize(stepDTO.getLimitSize() != null ? stepDTO.getLimitSize() : 5000)
                    .progressSize(stepDTO.getProgressSize() != null ? stepDTO.getProgressSize() : 5000)
                    .bulkInsert(stepDTO.getBulkInsert() != null ? stepDTO.getBulkInsert() : true)
                    .commitEach(stepDTO.getCommitEach() != null ? stepDTO.getCommitEach() : true)
                    .extraOptions(stepDTO.getExtraOptions())
                    .sortOrder(stepDTO.getSortOrder() != null ? stepDTO.getSortOrder() : index)
                    .enabled(stepDTO.getEnabled() != null ? stepDTO.getEnabled() : true)
                    .build());
            index++;
        }
    }

    private void saveAlertConfigs(ArchiveTask task, Set<Long> alertConfigIds) {
        if (alertConfigIds == null || alertConfigIds.isEmpty()) {
            return;
        }
        for (Long alertConfigId : alertConfigIds) {
            AlertConfig alertConfig = alertConfigRepository.findById(alertConfigId)
                    .orElseThrow(() -> new RuntimeException("告警配置不存在: " + alertConfigId));
            alertConfigRepositoryRelation.save(ArchiveTaskAlertConfig.builder()
                    .task(task)
                    .alertConfig(alertConfig)
                    .build());
        }
    }

    private void scheduleIfNeeded(ArchiveTask task) {
        try {
            if (task.getCronExpression() != null && !task.getCronExpression().isEmpty() && task.getStatus() == TaskStatus.ENABLED) {
                schedulerConfig.scheduleTask(task);
            } else {
                schedulerConfig.unscheduleTask(task.getId());
            }
        } catch (Exception e) {
            log.error("Failed to schedule archive task {}: {}", task.getId(), e.getMessage());
        }
    }

    private DbConnection getConnection(Long id, String message) {
        return connectionRepository.findById(id).orElseThrow(() -> new RuntimeException(message));
    }

    private ArchiveTaskVariableDTO toVariableDTO(ArchiveTaskVariable variable) {
        ArchiveTaskVariableDTO dto = new ArchiveTaskVariableDTO();
        dto.setId(variable.getId());
        dto.setName(variable.getName());
        dto.setQuerySql(variable.getQuerySql());
        dto.setSortOrder(variable.getSortOrder());
        dto.setEnabled(variable.getEnabled());
        return dto;
    }

    private ArchiveTaskStepDTO toStepDTO(ArchiveTaskStep step) {
        ArchiveTaskStepDTO dto = new ArchiveTaskStepDTO();
        dto.setId(step.getId());
        dto.setName(step.getName());
        dto.setSourceDatabase(step.getSourceDatabase());
        dto.setSourceTable(step.getSourceTable());
        dto.setDestDatabase(step.getDestDatabase());
        dto.setDestTable(step.getDestTable());
        dto.setWhereTemplate(step.getWhereTemplate());
        dto.setDeleteSource(step.getDeleteSource());
        dto.setCharset(step.getCharset());
        dto.setLimitSize(step.getLimitSize());
        dto.setProgressSize(step.getProgressSize());
        dto.setBulkInsert(step.getBulkInsert());
        dto.setCommitEach(step.getCommitEach());
        dto.setExtraOptions(step.getExtraOptions());
        dto.setSortOrder(step.getSortOrder());
        dto.setEnabled(step.getEnabled());
        return dto;
    }

    private Integer valueOrDefault(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
