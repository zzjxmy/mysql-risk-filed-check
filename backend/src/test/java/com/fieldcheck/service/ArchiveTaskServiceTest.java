package com.fieldcheck.service;

import com.fieldcheck.dto.ArchiveTaskDTO;
import com.fieldcheck.dto.ArchiveTaskStepDTO;
import com.fieldcheck.entity.ArchiveTask;
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
}
