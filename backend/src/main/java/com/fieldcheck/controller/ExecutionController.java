package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.ExecutionDTO;
import com.fieldcheck.entity.TaskExecution;
import com.fieldcheck.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping
    public ApiResponse<Page<ExecutionDTO>> getAllExecutions(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskExecution> executions = executionService.getAllExecutions(taskName, status, triggerType, pageRequest);
        Page<ExecutionDTO> dtoPage = executions.map(executionService::toDTO);
        return ApiResponse.success(dtoPage);
    }

    @GetMapping("/{id}")
    public ApiResponse<ExecutionDTO> getExecution(@PathVariable Long id) {
        TaskExecution execution = executionService.getExecution(id);
        return ApiResponse.success(executionService.toDTO(execution));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<ExecutionDTO> getProgress(@PathVariable Long id) {
        TaskExecution execution = executionService.getExecution(id);
        return ApiResponse.success(executionService.toDTO(execution));
    }

    @GetMapping("/{id}/log")
    public ApiResponse<String> getLog(@PathVariable Long id) {
        String logContent = executionService.getLogContent(id);
        return ApiResponse.success(logContent);
    }

    @GetMapping("/{id}/log/download")
    public ResponseEntity<Resource> downloadLog(@PathVariable Long id) {
        TaskExecution execution = executionService.getExecution(id);
        
        if (execution.getLogPath() == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(execution.getLogPath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }
}
