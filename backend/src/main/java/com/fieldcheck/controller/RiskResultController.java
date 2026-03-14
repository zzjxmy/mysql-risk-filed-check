package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.RiskResultDTO;
import com.fieldcheck.dto.RiskStatsDTO;
import com.fieldcheck.entity.RiskResult;
import com.fieldcheck.entity.RiskStatus;
import com.fieldcheck.entity.RiskType;
import com.fieldcheck.service.RiskResultService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/risks")
@RequiredArgsConstructor
public class RiskResultController {

    private final RiskResultService riskResultService;

    @GetMapping
    public ApiResponse<Page<RiskResultDTO>> getRisks(
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) RiskType riskType,
            @RequestParam(required = false) RiskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RiskResult> results = riskResultService.getRiskResults(
                executionId, databaseName, tableName, riskType, status, pageRequest);
        Page<RiskResultDTO> dtoPage = results.map(riskResultService::toDTO);
        return ApiResponse.success(dtoPage);
    }

    @GetMapping("/{id}")
    public ApiResponse<RiskResultDTO> getRisk(@PathVariable Long id) {
        RiskResult result = riskResultService.getRiskResult(id);
        return ApiResponse.success(riskResultService.toDTO(result));
    }

    @GetMapping("/stats")
    public ApiResponse<RiskStatsDTO> getStats() {
        return ApiResponse.success(riskResultService.getStats());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ApiResponse<RiskResultDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        RiskResult result = riskResultService.updateRiskStatus(id, request.getStatus(), request.getRemark());
        return ApiResponse.success("更新成功", riskResultService.toDTO(result));
    }

    @Data
    public static class StatusUpdateRequest {
        private RiskStatus status;
        private String remark;
    }
}
