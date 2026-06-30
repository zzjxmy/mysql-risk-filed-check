package com.fieldcheck.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldcheck.archive.engine.ArchiveJdbcClient;
import com.fieldcheck.archive.engine.ArchiveProcessRunner;
import com.fieldcheck.archive.engine.ArchiveTaskExecutor;
import com.fieldcheck.entity.ArchiveBatchConfig;
import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.entity.ArchiveTaskStep;
import com.fieldcheck.entity.DbConnection;
import com.fieldcheck.repository.ArchiveExecutionRepository;
import com.fieldcheck.service.ConnectionService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ArchiveTaskExecutorTest {

    @Test
    void executesBatchArchiveUntilBatchQueryReturnsNoRows() throws Exception {
        ConnectionService connectionService = mock(ConnectionService.class);
        ArchiveExecutionRepository executionRepository = mock(ArchiveExecutionRepository.class);
        ArchiveProcessRunner processRunner = mock(ArchiveProcessRunner.class);
        ArchiveJdbcClient jdbcClient = mock(ArchiveJdbcClient.class);
        ArchiveTaskExecutor executor = new ArchiveTaskExecutor(connectionService, executionRepository, processRunner, new ObjectMapper(), jdbcClient);

        DbConnection source = connection("source", 1L);
        DbConnection dest = connection("dest", 2L);
        DbConnection tidbBatchQuery = connection("tidb-batch-query", 3L);
        DbConnection mysqlHelper = connection("mysql-helper", 4L);
        ArchiveTaskStep step = ArchiveTaskStep.builder()
                .name("order archive")
                .stepMode("ARCHIVE")
                .sourceDatabase("hsq_online")
                .sourceTable("trade_order")
                .destDatabase("legacy_hsq_online")
                .destTable("trade_order")
                .whereTemplate("id in (select order_id from tmp_arch_order_id)")
                .enabled(true)
                .sortOrder(0)
                .build();
        ArchiveBatchConfig batchConfig = ArchiveBatchConfig.builder()
                .queryConnection(tidbBatchQuery)
                .targetConnection(mysqlHelper)
                .batchQuery("select id,pay_id from trade_order order by id limit 2000")
                .targetDatabase("hsq_online")
                .targetTable("tmp_arch_order_id")
                .truncateSql("truncate table hsq_online.tmp_arch_order_id")
                .loadSql("insert into hsq_online.tmp_arch_order_id (order_id,pay_id) values (?,?)")
                .batchSize(2000)
                .enabled(true)
                .build();
        ArchiveTask task = ArchiveTask.builder()
                .name("haoshiqi/order")
                .taskMode("BATCH_ARCHIVE")
                .sourceConnection(source)
                .destConnection(dest)
                .steps(Collections.singleton(step))
                .batchConfig(batchConfig)
                .build();
        step.setTask(task);
        batchConfig.setTask(task);
        ArchiveExecution execution = ArchiveExecution.builder().task(task).build();
        execution.setId(99L);

        List<List<Object>> firstBatch = Collections.singletonList(Arrays.asList(1001L, 2001L));
        when(jdbcClient.queryRows(tidbBatchQuery, batchConfig.getBatchQuery(), 2000))
                .thenReturn(firstBatch)
                .thenReturn(Collections.emptyList());
        when(connectionService.getDecryptedPassword(1L)).thenReturn("source-pwd");
        when(connectionService.getDecryptedPassword(2L)).thenReturn("dest-pwd");
        when(processRunner.run(eq(99L), anyList(), any())).thenReturn(0);

        executor.execute(execution, noopLog());

        verify(jdbcClient).executeUpdate(mysqlHelper, batchConfig.getTruncateSql());
        verify(jdbcClient).insertRows(mysqlHelper, batchConfig.getLoadSql(), firstBatch);
        verify(processRunner, times(1)).run(eq(99L), anyList(), any());
        assertThat(execution.getProcessedSteps()).isEqualTo(1);
        assertThat(execution.getExitCode()).isEqualTo(0);
    }

    private DbConnection connection(String name, Long id) {
        DbConnection connection = DbConnection.builder()
                .name(name)
                .host(name + "-db")
                .port(3306)
                .username("archiver")
                .password("encrypted")
                .build();
        connection.setId(id);
        return connection;
    }

    private BiConsumer<Long, String> noopLog() {
        return (id, line) -> {
        };
    }
}
