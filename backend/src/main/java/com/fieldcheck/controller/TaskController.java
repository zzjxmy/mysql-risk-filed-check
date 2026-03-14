package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.ExecutionDTO;
import com.fieldcheck.dto.TaskDTO;
import com.fieldcheck.entity.CheckTask;
import com.fieldcheck.entity.TaskExecution;
import com.fieldcheck.entity.TaskStatus;
import com.fieldcheck.service.ExecutionService;
import com.fieldcheck.service.TaskService;
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
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final ExecutionService executionService;

    @GetMapping
    public ApiResponse<Page<TaskDTO>> getTasks(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CheckTask> tasks = taskService.getTasks(name, status, connectionId, pageRequest);
        Page<TaskDTO> dtoPage = tasks.map(taskService::toDTO);
        return ApiResponse.success(dtoPage);
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskDTO> getTask(@PathVariable Long id) {
        CheckTask task = taskService.getTask(id);
        return ApiResponse.success(taskService.toDTO(task));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<TaskDTO> createTask(
            @Valid @RequestBody TaskDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        CheckTask task = taskService.createTask(dto, userDetails.getUsername());
        return ApiResponse.success("创建成功", taskService.toDTO(task));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<TaskDTO> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskDTO dto) {
        CheckTask task = taskService.updateTask(id, dto);
        return ApiResponse.success("更新成功", taskService.toDTO(task));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<ExecutionDTO> runTask(@PathVariable Long id) {
        TaskExecution execution = executionService.startExecution(id, "MANUAL");
        return ApiResponse.success("任务已启动", executionService.toDTO(execution));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<Void> stopTask(@PathVariable Long id) {
        executionService.stopExecution(id);
        return ApiResponse.success("任务已停止", null);
    }

    @GetMapping("/{id}/executions")
    public ApiResponse<Page<ExecutionDTO>> getTaskExecutions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskExecution> executions = executionService.getExecutionsByTask(id, pageRequest);
        Page<ExecutionDTO> dtoPage = executions.map(executionService::toDTO);
        return ApiResponse.success(dtoPage);
    }
}
