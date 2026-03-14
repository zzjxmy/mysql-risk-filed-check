package com.fieldcheck.service;

import com.fieldcheck.entity.AuditLog;
import com.fieldcheck.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 异步记录审计日志
     */
    @Async
    @Transactional
    public void logAsync(String action, String targetType, Long targetId, 
                         String targetName, String detail, HttpServletRequest request) {
        try {
            AuditLog auditLog = buildAuditLog(action, targetType, targetId, targetName, detail, request, true);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    /**
     * 同步记录审计日志
     */
    @Transactional
    public void log(String action, String targetType, Long targetId, 
                    String targetName, String detail, HttpServletRequest request, boolean success) {
        try {
            AuditLog auditLog = buildAuditLog(action, targetType, targetId, targetName, detail, request, success);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    /**
     * 记录登录日志
     */
    @Transactional
    public void logLogin(String username, HttpServletRequest request, boolean success, String detail) {
        AuditLog auditLog = AuditLog.builder()
                .username(username)
                .action("LOGIN")
                .detail(detail)
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .success(success)
                .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * 分页查询审计日志
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByConditions(String username, String action,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           Pageable pageable) {
        return auditLogRepository.findByConditions(username, action, startTime, endTime, pageable);
    }

    /**
     * 查询用户的审计日志
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByUserId(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    private AuditLog buildAuditLog(String action, String targetType, Long targetId,
                                   String targetName, String detail, 
                                   HttpServletRequest request, boolean success) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        
        return AuditLog.builder()
                .username(username)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .detail(detail)
                .ipAddress(getClientIp(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .success(success)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
