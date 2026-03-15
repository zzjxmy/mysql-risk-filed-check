package com.fieldcheck.service;

import com.fieldcheck.dto.ExecutionDTO;
import com.fieldcheck.dto.LogMessage;
import com.fieldcheck.entity.CheckTask;
import com.fieldcheck.entity.ExecutionStatus;
import com.fieldcheck.entity.TaskExecution;
import com.fieldcheck.engine.CheckEngine;
import com.fieldcheck.repository.CheckTaskRepository;
import com.fieldcheck.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final TaskExecutionRepository executionRepository;
    private final CheckTaskRepository taskRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CheckEngine checkEngine;
    private final AlertService alertService;
    private final TaskService taskService;

    @Value("${app.log-path:./logs/executions}")
    private String logPath;

    private final Map<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    public Page<TaskExecution> getExecutionsByTask(Long taskId, Pageable pageable) {
        return executionRepository.findByTaskId(taskId, pageable);
    }

    public Page<TaskExecution> getAllExecutions(String taskName, String status, String triggerType, Pageable pageable) {
        // For now, return all and filter in memory if needed
        // In production, this should be done with a custom repository query
        Page<TaskExecution> all = executionRepository.findAll(pageable);
        
        List<TaskExecution> filtered = all.getContent().stream()
                .filter(e -> {
                    if (taskName != null && !taskName.isEmpty()) {
                        if (e.getTask() == null || !e.getTask().getName().contains(taskName)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isEmpty()) {
                        if (!status.equals(e.getStatus().name())) {
                            return false;
                        }
                    }
                    if (triggerType != null && !triggerType.isEmpty()) {
                        if (!triggerType.equals(e.getTriggerType())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
        
        return new org.springframework.data.domain.PageImpl<>(filtered, pageable, all.getTotalElements());
    }

    public TaskExecution getExecution(Long id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("执行记录不存在"));
    }

    @Transactional
    public TaskExecution startExecution(Long taskId, String triggerType) {
        CheckTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));

        // Clear any stale running task entries (in case of server restart)
        // and check database for actual running status
        List<TaskExecution> runningExecutions = executionRepository.findByTaskIdAndStatus(taskId, ExecutionStatus.RUNNING);
        if (runningExecutions.isEmpty()) {
            // No actual running execution in database, clear memory cache
            runningTasks.remove(taskId);
        } else {
            // Update stale running tasks to FAILED
            runningExecutions.forEach(e -> {
                e.setStatus(ExecutionStatus.FAILED);
                e.setEndTime(LocalDateTime.now());
                e.setErrorMessage("任务被异常中断");
                executionRepository.save(e);
            });
            runningTasks.remove(taskId);
        }

        // Check if task is already running in memory
        if (runningTasks.containsKey(taskId)) {
            throw new RuntimeException("任务正在执行中");
        }

        // Create execution record
        String logFileName = String.format("task_%d_%s.log", 
                taskId, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        
        Path logFilePath = Paths.get(logPath, logFileName);
        try {
            Files.createDirectories(logFilePath.getParent());
        } catch (IOException e) {
            log.error("Failed to create log directory", e);
        }

        TaskExecution execution = TaskExecution.builder()
                .task(task)
                .status(ExecutionStatus.PENDING)
                .triggerType(triggerType)
                .logPath(logFilePath.toString())
                .build();

        execution = executionRepository.save(execution);

        // Start async execution
        runningTasks.put(taskId, true);
        executeAsync(execution.getId());

        return execution;
    }

    @Async("taskExecutor")
    public void executeAsync(Long executionId) {
        TaskExecution execution = getExecution(executionId);
        CheckTask task = execution.getTask();
        
        try {
            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setStartTime(LocalDateTime.now());
            executionRepository.save(execution);

            sendLog(executionId, "INFO|开始执行任务: " + task.getName());

            // Execute check engine
            checkEngine.execute(execution, this::sendLog, () -> !runningTasks.containsKey(task.getId()));

            execution.setStatus(ExecutionStatus.SUCCESS);
            sendLog(executionId, "INFO|任务执行完成");

        } catch (Exception e) {
            log.error("Task execution failed", e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            sendLog(executionId, "ERROR|任务执行失败: " + e.getMessage());
        } finally {
            execution.setEndTime(LocalDateTime.now());
            executionRepository.save(execution);
            
            // Send alert if there are risks or task failed
            if (execution.getRiskCount() > 0 || execution.getStatus() == ExecutionStatus.FAILED) {
                try {
                    // Get task-specific alert configs
                    List<com.fieldcheck.entity.AlertConfig> configs = taskService.getTaskAlertConfigs(task.getId());
                    if (!configs.isEmpty()) {
                        alertService.sendAlert(execution, configs);
                        log.info("Alert sent for execution {} using {} configs", execution.getId(), configs.size());
                    } else {
                        log.info("No alert configs associated with task {}, skipping alert", task.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to send alert for execution {}: {}", execution.getId(), e.getMessage());
                }
            }
            
            runningTasks.remove(task.getId());
        }
    }

    public void stopExecution(Long taskId) {
        if (runningTasks.containsKey(taskId)) {
            runningTasks.remove(taskId);
            
            // Update execution status
            executionRepository.findByTaskIdAndStatus(taskId, ExecutionStatus.RUNNING)
                    .forEach(e -> {
                        e.setStatus(ExecutionStatus.STOPPED);
                        e.setEndTime(LocalDateTime.now());
                        executionRepository.save(e);
                    });
        }
    }

    @Transactional
    public void updateProgress(Long executionId, int processedTables, int totalTables, int riskCount) {
        TaskExecution execution = getExecution(executionId);
        if (totalTables > 0) {
            execution.setTotalTables(totalTables);
        }
        execution.setProcessedTables(processedTables);
        execution.setRiskCount(riskCount);
        executionRepository.save(execution);
    }

    public void sendLog(Long executionId, String combinedMessage) {
        // Parse combined message: "LEVEL|message"
        String level = "INFO";
        String message = combinedMessage;
        if (combinedMessage.contains("|")) {
            int idx = combinedMessage.indexOf('|');
            level = combinedMessage.substring(0, idx);
            message = combinedMessage.substring(idx + 1);
        }
        
        LogMessage logMessage = LogMessage.builder()
                .executionId(executionId)
                .timestamp(LocalDateTime.now())
                .level(level)
                .message(message)
                .build();

        // Send via WebSocket
        messagingTemplate.convertAndSend("/topic/execution/" + executionId + "/log", logMessage);

        // Write to log file
        TaskExecution execution = getExecution(executionId);
        if (execution.getLogPath() != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(execution.getLogPath(), true))) {
                writer.printf("[%s] [%s] %s%n", 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        level, message);
            } catch (IOException e) {
                log.error("Failed to write log file", e);
            }
        }
    }

    public String getLogContent(Long executionId) {
        TaskExecution execution = getExecution(executionId);
        if (execution.getLogPath() == null) {
            return "";
        }
        
        try {
            return new String(Files.readAllBytes(Paths.get(execution.getLogPath())), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read log file", e);
            return "";
        }
    }

    public ExecutionDTO toDTO(TaskExecution execution) {
        int progressPercent = 0;
        if (execution.getTotalTables() != null && execution.getTotalTables() > 0) {
            progressPercent = (execution.getProcessedTables() * 100) / execution.getTotalTables();
        }

        return ExecutionDTO.builder()
                .id(execution.getId())
                .taskId(execution.getTask().getId())
                .taskName(execution.getTask().getName())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .status(execution.getStatus())
                .totalTables(execution.getTotalTables())
                .processedTables(execution.getProcessedTables())
                .riskCount(execution.getRiskCount())
                .logPath(execution.getLogPath())
                .errorMessage(execution.getErrorMessage())
                .triggerType(execution.getTriggerType())
                .progressPercent(progressPercent)
                .build();
    }
}
