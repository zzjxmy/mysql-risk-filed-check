package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.ArchiveExecutionDTO;
import com.fieldcheck.dto.ArchiveTaskDTO;
import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.entity.ArchiveTask;
import com.fieldcheck.entity.TaskStatus;
import com.fieldcheck.service.ArchiveExecutionService;
import com.fieldcheck.service.ArchiveTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/archive-tasks")
@RequiredArgsConstructor
public class ArchiveTaskController {

    private final ArchiveTaskService taskService;
    private final ArchiveExecutionService executionService;

    @GetMapping
    public ApiResponse<Page<ArchiveTaskDTO>> getTasks(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(taskService.getTasks(name, status, pageRequest).map(taskService::toDTO));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArchiveTaskDTO> getTask(@PathVariable Long id) {
        return ApiResponse.success(taskService.toDTO(taskService.getTask(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ArchiveTaskDTO> createTask(
            @Valid @RequestBody ArchiveTaskDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        ArchiveTask task = taskService.createTask(dto, userDetails.getUsername());
        return ApiResponse.success("创建成功", taskService.toDTO(task));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ArchiveTaskDTO> updateTask(@PathVariable Long id, @Valid @RequestBody ArchiveTaskDTO dto) {
        ArchiveTask task = taskService.updateTask(id, dto);
        return ApiResponse.success("更新成功", taskService.toDTO(task));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ArchiveExecutionDTO> runTask(@PathVariable Long id) {
        ArchiveExecution execution = executionService.startExecution(id, "MANUAL");
        return ApiResponse.success("归档任务已启动", executionService.toDTO(execution));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> stopTask(@PathVariable Long id) {
        executionService.stopExecution(id);
        return ApiResponse.success("归档任务已停止", null);
    }

    @GetMapping("/{id}/executions")
    public ApiResponse<Page<ArchiveExecutionDTO>> getExecutions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(executionService.getExecutionsByTask(id, pageRequest).map(executionService::toDTO));
    }
}
