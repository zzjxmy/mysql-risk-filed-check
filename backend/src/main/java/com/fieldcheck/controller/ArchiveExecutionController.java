package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.ArchiveExecutionDTO;
import com.fieldcheck.entity.ArchiveExecution;
import com.fieldcheck.service.ArchiveExecutionService;
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
@RequestMapping("/api/archive-executions")
@RequiredArgsConstructor
public class ArchiveExecutionController {

    private final ArchiveExecutionService executionService;

    @GetMapping
    public ApiResponse<Page<ArchiveExecutionDTO>> getExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(executionService.getAllExecutions(pageRequest).map(executionService::toDTO));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArchiveExecutionDTO> getExecution(@PathVariable Long id) {
        ArchiveExecution execution = executionService.getExecution(id);
        return ApiResponse.success(executionService.toDTO(execution));
    }

    @GetMapping("/{id}/log")
    public ApiResponse<String> getLog(@PathVariable Long id) {
        return ApiResponse.success(executionService.getLogContent(id));
    }

    @GetMapping("/{id}/log/download")
    public ResponseEntity<Resource> downloadLog(@PathVariable Long id) {
        File file = executionService.getLogFile(id);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.TEXT_PLAIN)
                .body(new FileSystemResource(file));
    }
}
