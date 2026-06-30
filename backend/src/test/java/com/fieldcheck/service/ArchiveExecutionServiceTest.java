package com.fieldcheck.service;

import com.fieldcheck.archive.engine.ArchiveTaskExecutor;
import com.fieldcheck.entity.ExecutionStatus;
import com.fieldcheck.repository.ArchiveExecutionRepository;
import com.fieldcheck.repository.ArchiveTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveExecutionServiceTest {

    @Mock
    private ArchiveExecutionRepository executionRepository;
    @Mock
    private ArchiveTaskRepository taskRepository;
    @Mock
    private ArchiveTaskExecutor taskExecutor;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private AlertService alertService;
    @Mock
    private ArchiveTaskService taskService;

    @Test
    void getExecutionsPassesOptionalFiltersToRepository() {
        ArchiveExecutionService service = new ArchiveExecutionService(
                executionRepository,
                taskRepository,
                taskExecutor,
                messagingTemplate,
                alertService,
                taskService
        );
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDateTime startFrom = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime startTo = LocalDateTime.of(2026, 6, 30, 23, 59);
        when(executionRepository.findByConditions(
                "arch_cart",
                ExecutionStatus.SUCCESS,
                "MANUAL",
                startFrom,
                startTo,
                pageable
        )).thenReturn(Page.empty(pageable));

        service.getExecutions("arch_cart", ExecutionStatus.SUCCESS, "MANUAL", startFrom, startTo, pageable);

        verify(executionRepository).findByConditions(
                "arch_cart",
                ExecutionStatus.SUCCESS,
                "MANUAL",
                startFrom,
                startTo,
                pageable
        );
    }
}
