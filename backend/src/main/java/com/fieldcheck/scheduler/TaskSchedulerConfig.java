package com.fieldcheck.scheduler;

import com.fieldcheck.entity.CheckTask;
import com.fieldcheck.entity.TaskStatus;
import com.fieldcheck.repository.CheckTaskRepository;
import com.fieldcheck.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskSchedulerConfig {

    private final Scheduler scheduler;
    private final CheckTaskRepository taskRepository;

    @PostConstruct
    public void init() {
        // Load all scheduled tasks on startup
        List<CheckTask> scheduledTasks = taskRepository.findByStatusAndCronExpressionIsNotNull(TaskStatus.ENABLED);
        for (CheckTask task : scheduledTasks) {
            try {
                scheduleTask(task);
            } catch (Exception e) {
                log.error("Failed to schedule task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    public void scheduleTask(CheckTask task) throws SchedulerException {
        if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
            return;
        }

        String jobKey = "task_" + task.getId();
        String triggerKey = "trigger_" + task.getId();

        // Remove existing job if exists
        if (scheduler.checkExists(JobKey.jobKey(jobKey))) {
            scheduler.deleteJob(JobKey.jobKey(jobKey));
        }

        // Create new job
        JobDetail job = JobBuilder.newJob(CheckTaskJob.class)
                .withIdentity(jobKey)
                .usingJobData("taskId", task.getId())
                .build();

        // Create trigger
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression()))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled task {} with cron: {}", task.getId(), task.getCronExpression());
    }

    public void unscheduleTask(Long taskId) throws SchedulerException {
        String jobKey = "task_" + taskId;
        if (scheduler.checkExists(JobKey.jobKey(jobKey))) {
            scheduler.deleteJob(JobKey.jobKey(jobKey));
            log.info("Unscheduled task {}", taskId);
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class CheckTaskJob extends QuartzJobBean {

        private final ExecutionService executionService;

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            Long taskId = context.getJobDetail().getJobDataMap().getLong("taskId");
            log.info("Scheduled execution triggered for task {}", taskId);
            
            try {
                executionService.startExecution(taskId, "SCHEDULED");
            } catch (Exception e) {
                log.error("Scheduled task {} execution failed: {}", taskId, e.getMessage());
                throw new JobExecutionException(e);
            }
        }
    }
}
