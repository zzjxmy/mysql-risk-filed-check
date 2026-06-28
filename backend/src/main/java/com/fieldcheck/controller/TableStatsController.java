package com.fieldcheck.controller;

import com.fieldcheck.dto.ApiResponse;
import com.fieldcheck.dto.TableStatsDTO;
import com.fieldcheck.service.TableStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/table-stats")
@RequiredArgsConstructor
public class TableStatsController {

    private final TableStatsService tableStatsService;

    @GetMapping
    public ApiResponse<List<TableStatsDTO>> getTableStats(
            @RequestParam Long connectionId,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double minFragmentMb) {
        return ApiResponse.success(tableStatsService.getTableStats(connectionId, schema, keyword, minFragmentMb));
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportTableStats(
            @RequestParam Long connectionId,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double minFragmentMb) {
        try {
            List<TableStatsDTO> stats = tableStatsService.getTableStats(connectionId, schema, keyword, minFragmentMb);
            byte[] content = TableStatsService.exportToExcel(stats);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "表空间分析_" + timestamp + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8.name()) + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(content));
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }
}
