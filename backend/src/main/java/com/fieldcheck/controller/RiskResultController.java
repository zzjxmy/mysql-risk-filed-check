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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    
    @GetMapping("/export")
    public ResponseEntity<Resource> exportRisks(
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) RiskType riskType,
            @RequestParam(required = false) RiskStatus status) {
        try {
            List<RiskResult> results = riskResultService.getAllRiskResults(executionId, databaseName, tableName, riskType, status);
            
            // Create Excel workbook
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("风险结果");
            
            // Create header row
            org.apache.poi.xssf.usermodel.XSSFRow headerRow = sheet.createRow(0);
            String[] headers = {"ID", "执行记录ID", "任务名称", "数据库", "表名", "字段名", "风险类型", 
                              "字段类型", "当前值", "最大值", "使用率(%)", "风险描述", "状态", "备注", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            // Create data rows
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < results.size(); i++) {
                RiskResult r = results.get(i);
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getId());
                row.createCell(1).setCellValue(r.getExecution() != null ? r.getExecution().getId() : 0);
                row.createCell(2).setCellValue(r.getExecution() != null && r.getExecution().getTask() != null ? r.getExecution().getTask().getName() : "");
                row.createCell(3).setCellValue(r.getDatabaseName());
                row.createCell(4).setCellValue(r.getTableName());
                row.createCell(5).setCellValue(r.getColumnName());
                row.createCell(6).setCellValue(r.getRiskType() != null ? r.getRiskType().name() : "");
                row.createCell(7).setCellValue(r.getColumnType());
                row.createCell(8).setCellValue(r.getCurrentValue() != null ? r.getCurrentValue() : "");
                row.createCell(9).setCellValue(r.getThresholdValue() != null ? r.getThresholdValue() : "");
                row.createCell(10).setCellValue(r.getUsagePercent() != null ? r.getUsagePercent().doubleValue() : 0);
                row.createCell(11).setCellValue(r.getDetail());
                row.createCell(12).setCellValue(r.getStatus() != null ? r.getStatus().name() : "");
                row.createCell(13).setCellValue(r.getRemark() != null ? r.getRemark() : "");
                row.createCell(14).setCellValue(r.getCreatedAt() != null ? r.getCreatedAt().format(formatter) : "");
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(outputStream);
            workbook.close();
            
            // Generate filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "风险结果_" + timestamp + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8.name()) + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(outputStream.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
}
