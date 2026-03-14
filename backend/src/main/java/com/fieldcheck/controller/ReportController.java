package com.fieldcheck.controller;

import com.fieldcheck.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 生成执行报告
     */
    @PostMapping("/execution/{executionId}")
    public ResponseEntity<Map<String, String>> generateExecutionReport(@PathVariable Long executionId) {
        try {
            String reportPath = reportService.generateExecutionReport(executionId);
            Map<String, String> result = new HashMap<>();
            result.put("message", "报告生成成功");
            result.put("reportPath", reportPath);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "报告生成失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 生成任务报告
     */
    @PostMapping("/task/{taskId}")
    public ResponseEntity<Map<String, String>> generateTaskReport(@PathVariable Long taskId) {
        try {
            String reportPath = reportService.generateTaskReport(taskId);
            Map<String, String> result = new HashMap<>();
            result.put("message", "报告生成成功");
            result.put("reportPath", reportPath);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "报告生成失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 获取报告列表
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listReports() {
        try {
            List<Map<String, Object>> reports = reportService.listReports();
            return ResponseEntity.ok(reports);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 下载报告
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String fileName) {
        try {
            byte[] content = reportService.getReportContent(fileName);
            
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/markdown; charset=utf-8"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(encodedFileName, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(content.length);

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 预览报告（返回Markdown内容）
     */
    @GetMapping("/preview/{fileName}")
    public ResponseEntity<String> previewReport(@PathVariable String fileName) {
        try {
            byte[] content = reportService.getReportContent(fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/markdown; charset=utf-8"))
                    .body(new String(content, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除报告
     */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> deleteReport(@PathVariable String fileName) {
        try {
            reportService.deleteReport(fileName);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
