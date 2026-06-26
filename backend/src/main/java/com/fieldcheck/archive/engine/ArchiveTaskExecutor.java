package com.fieldcheck.archive.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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

    @Value("${archive.pt-archiver-path:/bin/pt-archiver}")
    private String ptArchiverPath;

    public void execute(ArchiveExecution execution, BiConsumer<Long, String> logCallback) {
        ArchiveTask task = execution.getTask();
        Map<String, String> variables = new LinkedHashMap<>();
        int processed = 0;
        int skipped = 0;
        int lastExitCode = 0;

        try {
            List<ArchiveTaskVariable> enabledVariables = new ArrayList<>(task.getVariables());
            enabledVariables.sort(Comparator.comparing(v -> valueOrDefault(v.getSortOrder(), 0)));
            for (ArchiveTaskVariable variable : enabledVariables) {
                if (!Boolean.TRUE.equals(variable.getEnabled())) {
                    continue;
                }
                ArchiveSqlValidator.validateSingleSelect(variable.getQuerySql());
                String value = queryVariable(task.getSourceConnection(), variable.getQuerySql());
                variables.put(variable.getName(), value);
                logCallback.accept(execution.getId(), "INFO|变量 " + variable.getName() + " = " + (value == null ? "NULL" : value));
            }
            execution.setVariableSnapshot(objectMapper.writeValueAsString(variables));
            executionRepository.save(execution);

            List<ArchiveTaskStep> steps = new ArrayList<>(task.getSteps());
            steps.sort(Comparator.comparing(s -> valueOrDefault(s.getSortOrder(), 0)));
            execution.setTotalSteps((int) steps.stream().filter(s -> Boolean.TRUE.equals(s.getEnabled())).count());
            executionRepository.save(execution);

            for (ArchiveTaskStep step : steps) {
                if (!Boolean.TRUE.equals(step.getEnabled())) {
                    continue;
                }
                String whereClause;
                try {
                    whereClause = ArchiveVariableRenderer.render(step.getWhereTemplate(), variables);
                    ArchiveSqlValidator.validateWhereTemplate(whereClause);
                } catch (ArchiveVariableRenderer.MissingVariableException e) {
                    skipped++;
                    execution.setSkippedSteps(skipped);
                    executionRepository.save(execution);
                    logCallback.accept(execution.getId(), "WARN|跳过步骤 " + step.getName() + ": " + e.getMessage());
                    continue;
                }

                logCallback.accept(execution.getId(), "INFO|开始归档步骤: " + step.getName() + ", where: " + whereClause +
                        ", 模式: " + (Boolean.TRUE.equals(step.getDeleteSource()) ? "移动并删除源数据" : "只复制不删除"));
                List<String> command = ArchiveCommandBuilder.build(toCommandSpec(task, step, whereClause));
                logCallback.accept(execution.getId(), "INFO|执行命令: " + ArchiveCommandBuilder.toRedactedLogLine(command));
                lastExitCode = processRunner.run(execution.getId(), command, line -> logCallback.accept(execution.getId(), "INFO|" + line));
                execution.setExitCode(lastExitCode);
                if (lastExitCode != 0) {
                    throw new RuntimeException("归档步骤 " + step.getName() + " 执行失败，退出码: " + lastExitCode);
                }
                processed++;
                execution.setProcessedSteps(processed);
                executionRepository.save(execution);
                logCallback.accept(execution.getId(), "INFO|完成归档步骤: " + step.getName());
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void stop(Long executionId) {
        processRunner.stop(executionId);
    }

    private String queryVariable(DbConnection connectionConfig, String querySql) throws Exception {
        String password = connectionService.getDecryptedPassword(connectionConfig.getId());
        String jdbcUrl = String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=30000&socketTimeout=300000",
                connectionConfig.getHost(), connectionConfig.getPort());
        try (Connection connection = DriverManager.getConnection(jdbcUrl, connectionConfig.getUsername(), password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(querySql)) {
            if (!resultSet.next()) {
                return null;
            }
            Object value = resultSet.getObject(1);
            return value == null ? null : String.valueOf(value);
        }
    }

    private ArchiveCommandSpec toCommandSpec(ArchiveTask task, ArchiveTaskStep step, String whereClause) {
        DbConnection source = task.getSourceConnection();
        DbConnection dest = task.getDestConnection();
        return ArchiveCommandSpec.builder()
                .ptArchiverPath(ptArchiverPath)
                .sourceHost(source.getHost())
                .sourcePort(source.getPort())
                .sourceUsername(source.getUsername())
                .sourcePassword(connectionService.getDecryptedPassword(source.getId()))
                .sourceDatabase(step.getSourceDatabase())
                .sourceTable(step.getSourceTable())
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
                .extraOptions(parseExtraOptions(step.getExtraOptions()))
                .build();
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
}
