package com.fieldcheck.archive.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldcheck.entity.ArchiveBatchConfig;
import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.entity.ArchiveTaskStep;
import com.fieldcheck.entity.ArchiveTaskVariable;
import com.fieldcheck.entity.DbConnection;
import com.fieldcheck.repository.ArchiveExecutionRepository;
import com.fieldcheck.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Component
@RequiredArgsConstructor
public class ArchiveTaskExecutor {

    private final ConnectionService connectionService;
    private final ArchiveExecutionRepository executionRepository;
    private final ArchiveProcessRunner processRunner;
    private final ObjectMapper objectMapper;
    private final ArchiveJdbcClient jdbcClient;

    @Value("${archive.pt-archiver-path:/bin/pt-archiver}")
    private String ptArchiverPath;

    public void execute(ArchiveExecution execution, BiConsumer<Long, String> logCallback) {
        ArchiveTask task = execution.getTask();

        try {
            if (Boolean.TRUE.equals(task.getDryRun())) {
                logCallback.accept(execution.getId(), "WARN|当前为 dry-run 演练模式，pt-archiver 不会归档或删除源表数据");
            }
            Map<String, String> variables = loadVariables(task, execution, logCallback);
            List<ArchiveTaskStep> steps = enabledSteps(task);
            execution.setTotalSteps((int) steps.stream().filter(s -> Boolean.TRUE.equals(s.getEnabled())).count());
            executionRepository.save(execution);

            if ("BATCH_ARCHIVE".equalsIgnoreCase(task.getTaskMode())) {
                executeBatchArchive(task, execution, variables, steps, logCallback);
            } else {
                StepCounters counters = new StepCounters();
                runSteps(task, execution, variables, steps, counters, logCallback);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void stop(Long executionId) {
        processRunner.stop(executionId);
    }

    private Map<String, String> loadVariables(ArchiveTask task, ArchiveExecution execution, BiConsumer<Long, String> logCallback) throws Exception {
        Map<String, String> variables = new LinkedHashMap<>();
        List<ArchiveTaskVariable> enabledVariables = new ArrayList<>(task.getVariables());
        enabledVariables.sort(Comparator.comparing(v -> valueOrDefault(v.getSortOrder(), 0)));
        for (ArchiveTaskVariable variable : enabledVariables) {
            if (!Boolean.TRUE.equals(variable.getEnabled())) {
                continue;
            }
            ArchiveSqlValidator.validateSingleSelect(variable.getQuerySql());
            DbConnection variableConnection = variable.getConnection() != null ? variable.getConnection() : task.getSourceConnection();
            String value = jdbcClient.querySingleValue(variableConnection, variable.getQuerySql());
            variables.put(variable.getName(), value);
            logCallback.accept(execution.getId(), "INFO|变量 " + variable.getName() + " = " + (value == null ? "NULL" : value));
        }
        execution.setVariableSnapshot(objectMapper.writeValueAsString(variables));
        executionRepository.save(execution);
        return variables;
    }

    private List<ArchiveTaskStep> enabledSteps(ArchiveTask task) {
        List<ArchiveTaskStep> steps = new ArrayList<>(task.getSteps());
        steps.sort(Comparator.comparing(s -> valueOrDefault(s.getSortOrder(), 0)));
        return steps;
    }

    private void executeBatchArchive(ArchiveTask task, ArchiveExecution execution, Map<String, String> variables, List<ArchiveTaskStep> steps, BiConsumer<Long, String> logCallback) throws Exception {
        ArchiveBatchConfig batchConfig = task.getBatchConfig();
        if (batchConfig == null || !Boolean.TRUE.equals(batchConfig.getEnabled())) {
            throw new RuntimeException("批次归档任务缺少启用的批次配置");
        }

        StepCounters counters = new StepCounters();
        int round = 1;
        while (true) {
            if (batchConfig.getMaxRounds() != null && round > batchConfig.getMaxRounds()) {
                logCallback.accept(execution.getId(), "INFO|达到最大批次数: " + batchConfig.getMaxRounds());
                break;
            }
            ArchiveSqlValidator.validateSingleSelect(batchConfig.getBatchQuery());
            List<List<Object>> rows = jdbcClient.queryRows(batchConfig.getQueryConnection(), batchConfig.getBatchQuery(), batchConfig.getBatchSize());
            if (rows.isEmpty()) {
                logCallback.accept(execution.getId(), "INFO|批次查询无数据，任务结束");
                break;
            }

            logCallback.accept(execution.getId(), "INFO|开始第 " + round + " 轮批次，行数: " + rows.size() +
                    ", 辅助表: " + batchConfig.getTargetDatabase() + "." + batchConfig.getTargetTable());
            jdbcClient.executeUpdate(batchConfig.getTargetConnection(), batchConfig.getTruncateSql());
            jdbcClient.insertRows(batchConfig.getTargetConnection(), batchConfig.getLoadSql(), rows);
            runSteps(task, execution, variables, steps, counters, logCallback);
            round++;
        }
    }

    private void runSteps(ArchiveTask task, ArchiveExecution execution, Map<String, String> variables, List<ArchiveTaskStep> steps, StepCounters counters, BiConsumer<Long, String> logCallback) throws Exception {
        for (ArchiveTaskStep step : steps) {
            if (!Boolean.TRUE.equals(step.getEnabled())) {
                continue;
            }
            String whereClause;
            try {
                whereClause = ArchiveVariableRenderer.render(step.getWhereTemplate(), variables);
                ArchiveSqlValidator.validateWhereTemplate(whereClause);
            } catch (ArchiveVariableRenderer.MissingVariableException e) {
                counters.skipped++;
                execution.setSkippedSteps(counters.skipped);
                executionRepository.save(execution);
                logCallback.accept(execution.getId(), "WARN|跳过步骤 " + step.getName() + ": " + e.getMessage());
                continue;
            }

            logCallback.accept(execution.getId(), "INFO|开始归档步骤: " + step.getName() + ", where: " + whereClause +
                    ", 模式: " + describeStepMode(step));
            List<String> command = ArchiveCommandBuilder.build(toCommandSpec(task, step, whereClause));
            logCallback.accept(execution.getId(), "INFO|执行命令: " + ArchiveCommandBuilder.toRedactedLogLine(command));
            int lastExitCode = processRunner.run(execution.getId(), command, line -> logCallback.accept(execution.getId(), "INFO|" + line));
            execution.setExitCode(lastExitCode);
            if (lastExitCode != 0) {
                throw new RuntimeException("归档步骤 " + step.getName() + " 执行失败，退出码: " + lastExitCode);
            }
            counters.processed++;
            execution.setProcessedSteps(counters.processed);
            executionRepository.save(execution);
            logCallback.accept(execution.getId(), "INFO|完成归档步骤: " + step.getName());
        }
    }

    private ArchiveCommandSpec toCommandSpec(ArchiveTask task, ArchiveTaskStep step, String whereClause) {
        DbConnection source = task.getSourceConnection();
        DbConnection dest = task.getDestConnection();
        return ArchiveCommandSpec.builder()
                .ptArchiverPath(ptArchiverPath)
                .stepMode(step.getStepMode())
                .sourceHost(source.getHost())
                .sourcePort(source.getPort())
                .sourceUsername(source.getUsername())
                .sourcePassword(connectionService.getDecryptedPassword(source.getId()))
                .sourceDatabase(step.getSourceDatabase())
                .sourceTable(step.getSourceTable())
                .sourceIndexName(step.getIndexName())
                .destHost(dest.getHost())
                .destPort(dest.getPort())
                .destUsername(dest.getUsername())
                .destPassword(connectionService.getDecryptedPassword(dest.getId()))
                .destDatabase(step.getDestDatabase())
                .destTable(step.getDestTable())
                .whereClause(whereClause)
                .charset(step.getCharset())
                .limitSize(step.getLimitSize())
                .progressSize(step.getProgressSize())
                .deleteSource(step.getDeleteSource())
                .bulkInsert(step.getBulkInsert())
                .commitEach(step.getCommitEach())
                .dryRun(task.getDryRun())
                .extraOptions(parseExtraOptions(step.getExtraOptions()))
                .build();
    }

    private String describeStepMode(ArchiveTaskStep step) {
        if ("PURGE".equalsIgnoreCase(step.getStepMode())) {
            return "纯删除";
        }
        return Boolean.TRUE.equals(step.getDeleteSource()) ? "移动并删除源数据" : "只复制不删除";
    }

    private List<String> parseExtraOptions(String extraOptions) {
        List<String> options = new ArrayList<>();
        if (extraOptions == null || extraOptions.trim().isEmpty()) {
            return options;
        }
        for (String line : extraOptions.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                options.add(trimmed);
            }
        }
        return options;
    }

    private Integer valueOrDefault(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static class StepCounters {
        private int processed;
        private int skipped;
    }
}
