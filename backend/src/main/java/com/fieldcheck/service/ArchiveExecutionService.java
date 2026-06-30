package com.fieldcheck.service;

import com.fieldcheck.archive.engine.ArchiveTaskExecutor;
import com.fieldcheck.dto.ArchiveExecutionDTO;
import com.fieldcheck.dto.LogMessage;
import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.entity.ExecutionStatus;
import com.fieldcheck.repository.ArchiveExecutionRepository;
import com.fieldcheck.repository.ArchiveTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
public class ArchiveExecutionService {

    private final ArchiveExecutionRepository executionRepository;
    private final ArchiveTaskRepository taskRepository;
    private final ArchiveTaskExecutor taskExecutor;
    private final SimpMessagingTemplate messagingTemplate;
    private final AlertService alertService;
    private final ArchiveTaskService taskService;

    @Autowired
    @Lazy
    private ArchiveExecutionService self;

    @Value("${app.log-path:./logs/executions}")
    private String logPath;

    private final Map<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    @Autowired
    public ArchiveExecutionService(ArchiveExecutionRepository executionRepository,
                                   ArchiveTaskRepository taskRepository,
                                   ArchiveTaskExecutor taskExecutor,
                                   SimpMessagingTemplate messagingTemplate,
                                   AlertService alertService,
                                   ArchiveTaskService taskService) {
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
        this.messagingTemplate = messagingTemplate;
        this.alertService = alertService;
        this.taskService = taskService;
    }

    public Page<ArchiveExecution> getExecutionsByTask(Long taskId, Pageable pageable) {
        return executionRepository.findByTaskId(taskId, pageable);
    }

    public Page<ArchiveExecution> getAllExecutions(Pageable pageable) {
        return getExecutions(null, null, null, null, null, pageable);
    }

    public Page<ArchiveExecution> getExecutions(String taskName,
                                                ExecutionStatus status,
                                                String triggerType,
                                                LocalDateTime startFrom,
                                                LocalDateTime startTo,
                                                Pageable pageable) {
        return executionRepository.findByConditions(
                normalize(taskName),
                status,
                normalize(triggerType),
                startFrom,
                startTo,
                pageable
        );
    }

    public ArchiveExecution getExecution(Long id) {
        return executionRepository.findByIdWithTask(id)
                .orElseThrow(() -> new RuntimeException("归档执行记录不存在"));
    }

    public ArchiveExecution startExecution(Long taskId, String triggerType) {
        ArchiveTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("归档任务不存在"));
        List<ArchiveExecution> running = executionRepository.findByTaskIdAndStatus(taskId, ExecutionStatus.RUNNING);
        if (!running.isEmpty() || runningTasks.containsKey(taskId)) {
            throw new RuntimeException("归档任务正在执行中");
        }

        String logFileName = String.format("archive_task_%d_%s.log",
                taskId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path logFilePath = Paths.get(logPath, logFileName);
        try {
            Files.createDirectories(logFilePath.getParent());
        } catch (IOException e) {
            log.error("Failed to create archive log directory", e);
        }

        ArchiveExecution execution = ArchiveExecution.builder()
                .task(task)
                .status(ExecutionStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .triggerType(triggerType)
                .logPath(logFilePath.toString())
                .build();
        execution = executionRepository.saveAndFlush(execution);
        runningTasks.put(taskId, true);
        self.executeAsync(execution.getId());
        return execution;
    }

    @Async("taskExecutor")
    public void executeAsync(Long executionId) {
        ArchiveExecution execution = executionRepository.findByIdWithTask(executionId)
                .orElseThrow(() -> new RuntimeException("归档执行记录不存在"));
        ArchiveTask task = taskRepository.findByIdWithDetails(execution.getTask().getId())
                .orElseThrow(() -> new RuntimeException("归档任务不存在"));
        execution.setTask(task);
        try {
            sendLog(executionId, "INFO|开始执行归档任务: " + task.getName());
            taskExecutor.execute(execution, this::sendLog);
            execution.setStatus(ExecutionStatus.SUCCESS);
            sendLog(executionId, "INFO|归档任务执行完成");
        } catch (Exception e) {
            if (!runningTasks.containsKey(task.getId())) {
                execution.setStatus(ExecutionStatus.STOPPED);
                sendLog(executionId, "WARN|归档任务已停止");
            } else {
                log.error("Archive task execution failed", e);
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setErrorMessage(e.getMessage());
                sendLog(executionId, "ERROR|归档任务执行失败: " + e.getMessage());
            }
        } finally {
            execution.setEndTime(LocalDateTime.now());
            executionRepository.save(execution);
            if (execution.getStatus() == ExecutionStatus.FAILED) {
                try {
                    List<com.fieldcheck.entity.AlertConfig> configs = taskService.getTaskAlertConfigs(task.getId());
                    if (!configs.isEmpty()) {
                        alertService.sendArchiveAlert(execution, configs);
                    }
                } catch (Exception e) {
                    log.error("Failed to send archive alert for execution {}: {}", execution.getId(), e.getMessage());
                }
            }
            runningTasks.remove(task.getId());
        }
    }

    public void stopExecution(Long taskId) {
        executionRepository.findByTaskIdAndStatus(taskId, ExecutionStatus.RUNNING).forEach(e -> {
            runningTasks.remove(taskId);
            sendLog(e.getId(), "WARN|收到停止归档任务请求");
            taskExecutor.stop(e.getId());
            e.setStatus(ExecutionStatus.STOPPED);
            e.setEndTime(LocalDateTime.now());
            executionRepository.save(e);
        });
    }

    public void sendLog(Long executionId, String combinedMessage) {
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
        messagingTemplate.convertAndSend("/topic/archive-execution/" + executionId + "/log", logMessage);

        ArchiveExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution != null && execution.getLogPath() != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(execution.getLogPath(), true))) {
                writer.printf("[%s] [%s] %s%n",
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        level,
                        message);
            } catch (IOException e) {
                log.error("Failed to write archive log", e);
            }
        }
    }

    public String getLogContent(Long executionId) {
        ArchiveExecution execution = getExecution(executionId);
        if (execution.getLogPath() == null) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(Paths.get(execution.getLogPath())), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read archive log", e);
            return "";
        }
    }

    public File getLogFile(Long executionId) {
        ArchiveExecution execution = getExecution(executionId);
        return execution.getLogPath() == null ? null : new File(execution.getLogPath());
    }

    public ArchiveExecutionDTO toDTO(ArchiveExecution execution) {
        int progressPercent = 0;
        if (execution.getTotalSteps() != null && execution.getTotalSteps() > 0) {
            progressPercent = (execution.getProcessedSteps() * 100) / execution.getTotalSteps();
        }
        return ArchiveExecutionDTO.builder()
                .id(execution.getId())
                .taskId(execution.getTask().getId())
                .taskName(execution.getTask().getName())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .status(execution.getStatus())
                .totalSteps(execution.getTotalSteps())
                .processedSteps(execution.getProcessedSteps())
                .skippedSteps(execution.getSkippedSteps())
                .exitCode(execution.getExitCode())
                .variableSnapshot(execution.getVariableSnapshot())
                .logPath(execution.getLogPath())
                .errorMessage(execution.getErrorMessage())
                .triggerType(execution.getTriggerType())
                .progressPercent(progressPercent)
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
