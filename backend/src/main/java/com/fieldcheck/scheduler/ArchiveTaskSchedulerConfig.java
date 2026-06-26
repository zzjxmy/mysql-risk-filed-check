package com.fieldcheck.scheduler;

import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.entity.TaskStatus;
import com.fieldcheck.repository.ArchiveTaskRepository;
import com.fieldcheck.service.ArchiveExecutionService;
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
public class ArchiveTaskSchedulerConfig {

    private final Scheduler scheduler;
    private final ArchiveTaskRepository taskRepository;

    @PostConstruct
    public void init() {
        List<ArchiveTask> scheduledTasks = taskRepository.findByStatusAndCronExpressionIsNotNull(TaskStatus.ENABLED);
        for (ArchiveTask task : scheduledTasks) {
            try {
                scheduleTask(task);
            } catch (Exception e) {
                log.error("Failed to schedule archive task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    public void scheduleTask(ArchiveTask task) throws SchedulerException {
        if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
            return;
        }
        String jobKey = "archive_task_" + task.getId();
        String triggerKey = "archive_trigger_" + task.getId();
        if (scheduler.checkExists(JobKey.jobKey(jobKey))) {
            scheduler.deleteJob(JobKey.jobKey(jobKey));
        }
        JobDetail job = JobBuilder.newJob(ArchiveTaskJob.class)
                .withIdentity(jobKey)
                .usingJobData("taskId", task.getId())
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(fixCronExpression(task.getCronExpression())))
                .build();
        scheduler.scheduleJob(job, trigger);
    }

    public void unscheduleTask(Long taskId) throws SchedulerException {
        String jobKey = "archive_task_" + taskId;
        if (scheduler.checkExists(JobKey.jobKey(jobKey))) {
            scheduler.deleteJob(JobKey.jobKey(jobKey));
        }
    }

    private String fixCronExpression(String cronExpr) {
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length >= 6 && "*".equals(parts[3]) && "*".equals(parts[5])) {
            parts[5] = "?";
            return String.join(" ", parts);
        }
        return cronExpr;
    }

    @Component
    @RequiredArgsConstructor
    public static class ArchiveTaskJob extends QuartzJobBean {

        private final ArchiveExecutionService executionService;

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            Long taskId = context.getJobDetail().getJobDataMap().getLong("taskId");
            try {
                executionService.startExecution(taskId, "SCHEDULED");
            } catch (Exception e) {
                throw new JobExecutionException(e);
            }
        }
    }
}
