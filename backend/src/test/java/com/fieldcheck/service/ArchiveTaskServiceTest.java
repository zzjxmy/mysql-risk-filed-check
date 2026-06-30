package com.fieldcheck.service;

import com.fieldcheck.dto.ArchiveTaskDTO;
import com.fieldcheck.dto.ArchiveTaskStepDTO;
import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.dto.ArchiveBatchConfigDTO;
import com.fieldcheck.dto.ArchiveTaskVariableDTO;
import com.fieldcheck.entity.DbConnection;
import com.fieldcheck.entity.SysUser;
import com.fieldcheck.repository.AlertConfigRepository;
import com.fieldcheck.repository.ArchiveExecutionRepository;
import com.fieldcheck.repository.ArchiveTaskAlertConfigRepository;
import com.fieldcheck.repository.ArchiveTaskRepository;
import com.fieldcheck.repository.DbConnectionRepository;
import com.fieldcheck.repository.SysUserRepository;
import com.fieldcheck.scheduler.ArchiveTaskSchedulerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveTaskServiceTest {

    @Mock
    private ArchiveTaskRepository taskRepository;
    @Mock
    private DbConnectionRepository connectionRepository;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AlertConfigRepository alertConfigRepository;
    @Mock
    private ArchiveTaskAlertConfigRepository alertConfigRepositoryRelation;
    @Mock
    private ArchiveExecutionRepository executionRepository;
    @Mock
    private ArchiveTaskSchedulerConfig schedulerConfig;

    @InjectMocks
    private ArchiveTaskService archiveTaskService;

    @Test
    void createTaskDefaultsBulkInsertToFalseWhenOmitted() {
        DbConnection source = DbConnection.builder().name("source").host("source-db").username("root").password("pwd").build();
        source.setId(1L);
        DbConnection dest = DbConnection.builder().name("dest").host("dest-db").username("root").password("pwd").build();
        dest.setId(2L);
        SysUser user = SysUser.builder().username("admin").build();
        user.setId(1L);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(source));
        when(connectionRepository.findById(2L)).thenReturn(Optional.of(dest));
        when(taskRepository.save(ArgumentMatchers.any(ArchiveTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArchiveTaskStepDTO step = new ArchiveTaskStepDTO();
        step.setName("archive orders");
        step.setSourceDatabase("biz");
        step.setSourceTable("orders");
        step.setDestDatabase("legacy_biz");
        step.setDestTable("orders");
        step.setWhereTemplate("id < 100");

        ArchiveTaskDTO dto = new ArchiveTaskDTO();
        dto.setName("archive task");
        dto.setSourceConnectionId(1L);
        dto.setDestConnectionId(2L);
        dto.setAlertConfigIds(Collections.emptySet());
        dto.setSteps(Collections.singletonList(step));

        ArchiveTask task = archiveTaskService.createTask(dto, "admin");

        assertThat(task.getSteps()).singleElement()
                .extracting(archiveStep -> archiveStep.getBulkInsert())
                .isEqualTo(false);
    }

    @Test
    void createTaskPersistsStructuredArchiveModesVariableConnectionAndBatchConfig() {
        DbConnection source = DbConnection.builder().name("source").host("source-db").username("root").password("pwd").build();
        source.setId(1L);
        DbConnection dest = DbConnection.builder().name("dest").host("dest-db").username("root").password("pwd").build();
        dest.setId(2L);
        DbConnection tidbQuery = DbConnection.builder().name("tidb-query").host("tidb-db").username("root").password("pwd").build();
        tidbQuery.setId(3L);
        DbConnection mysqlHelper = DbConnection.builder().name("mysql-helper").host("helper-db").username("root").password("pwd").build();
        mysqlHelper.setId(4L);
        SysUser user = SysUser.builder().username("admin").build();
        user.setId(1L);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(source));
        when(connectionRepository.findById(2L)).thenReturn(Optional.of(dest));
        when(connectionRepository.findById(3L)).thenReturn(Optional.of(tidbQuery));
        when(connectionRepository.findById(4L)).thenReturn(Optional.of(mysqlHelper));
        when(taskRepository.save(ArgumentMatchers.any(ArchiveTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArchiveTaskVariableDTO variable = new ArchiveTaskVariableDTO();
        variable.setName("maxid");
        variable.setQuerySql("select max(id) from hsq_online.cart");
        variable.setConnectionId(3L);
        variable.setEnabled(true);

        ArchiveTaskStepDTO purgeStep = new ArchiveTaskStepDTO();
        purgeStep.setName("cart purge by user_id");
        purgeStep.setStepMode("PURGE");
        purgeStep.setSourceDatabase("hsq_online");
        purgeStep.setSourceTable("cart");
        purgeStep.setIndexName("idx_c_userid");
        purgeStep.setWhereTemplate("user_id < ${maxid}");

        ArchiveBatchConfigDTO batchConfig = new ArchiveBatchConfigDTO();
        batchConfig.setQueryConnectionId(3L);
        batchConfig.setTargetConnectionId(4L);
        batchConfig.setBatchQuery("select id,pay_id from trade_order order by id limit 2000");
        batchConfig.setTargetDatabase("hsq_online");
        batchConfig.setTargetTable("tmp_arch_order_id");
        batchConfig.setTruncateSql("truncate table hsq_online.tmp_arch_order_id");
        batchConfig.setLoadSql("insert into hsq_online.tmp_arch_order_id (order_id,pay_id) values (?,?)");
        batchConfig.setBatchSize(2000);
        batchConfig.setMaxRounds(3);
        batchConfig.setEnabled(true);

        ArchiveTaskDTO dto = new ArchiveTaskDTO();
        dto.setName("haoshiqi/arch_cart");
        dto.setTaskMode("BATCH_ARCHIVE");
        dto.setSourceConnectionId(1L);
        dto.setDestConnectionId(2L);
        dto.setAlertConfigIds(Collections.emptySet());
        dto.setVariables(Collections.singletonList(variable));
        dto.setSteps(Collections.singletonList(purgeStep));
        dto.setBatchConfig(batchConfig);

        ArchiveTask task = archiveTaskService.createTask(dto, "admin");
        ArchiveTaskDTO result = archiveTaskService.toDTO(task);

        assertThat(result.getTaskMode()).isEqualTo("BATCH_ARCHIVE");
        assertThat(result.getVariables()).singleElement().satisfies(savedVariable -> {
            assertThat(savedVariable.getName()).isEqualTo("maxid");
            assertThat(savedVariable.getConnectionId()).isEqualTo(3L);
        });
        assertThat(result.getSteps()).singleElement().satisfies(savedStep -> {
            assertThat(savedStep.getStepMode()).isEqualTo("PURGE");
            assertThat(savedStep.getIndexName()).isEqualTo("idx_c_userid");
            assertThat(savedStep.getDestDatabase()).isNull();
            assertThat(savedStep.getDestTable()).isNull();
        });
        assertThat(result.getBatchConfig()).satisfies(savedBatch -> {
            assertThat(savedBatch.getQueryConnectionId()).isEqualTo(3L);
            assertThat(savedBatch.getQueryConnectionName()).isEqualTo("tidb-query");
            assertThat(savedBatch.getTargetConnectionId()).isEqualTo(4L);
            assertThat(savedBatch.getTargetConnectionName()).isEqualTo("mysql-helper");
            assertThat(savedBatch.getTargetTable()).isEqualTo("tmp_arch_order_id");
            assertThat(savedBatch.getMaxRounds()).isEqualTo(3);
        });
    }
}
