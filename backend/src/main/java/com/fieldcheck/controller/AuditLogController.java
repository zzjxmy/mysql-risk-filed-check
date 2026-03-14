package com.fieldcheck.controller;

import com.fieldcheck.entity.AuditLog;
import com.fieldcheck.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogService.findByConditions(username, action, startTime, endTime, pageRequest);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getUserAuditLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogService.findByUserId(userId, pageRequest);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/actions")
    public ResponseEntity<String[]> getActionTypes() {
        String[] actions = {
            "LOGIN", "LOGOUT",
            "CREATE_CONNECTION", "UPDATE_CONNECTION", "DELETE_CONNECTION", "TEST_CONNECTION",
            "CREATE_TASK", "UPDATE_TASK", "DELETE_TASK", "EXECUTE_TASK", "STOP_TASK",
            "CREATE_WHITELIST", "UPDATE_WHITELIST", "DELETE_WHITELIST",
            "CREATE_ALERT", "UPDATE_ALERT", "DELETE_ALERT", "TEST_ALERT",
            "CREATE_USER", "UPDATE_USER", "DELETE_USER", "RESET_PASSWORD",
            "UPDATE_RISK_STATUS"
        };
        return ResponseEntity.ok(actions);
    }
}
