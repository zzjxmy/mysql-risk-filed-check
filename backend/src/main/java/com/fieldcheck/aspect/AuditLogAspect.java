package com.fieldcheck.aspect;

import com.fieldcheck.entity.AuditLog;
import com.fieldcheck.entity.SysUser;
import com.fieldcheck.repository.AuditLogRepository;
import com.fieldcheck.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final SysUserRepository userRepository;

    // 记录所有Controller的操作
    @Around("execution(* com.fieldcheck.controller.*Controller.*(..)) && " +
            "!execution(* com.fieldcheck.controller.AuditLogController.*(..)) && " +
            "!execution(* com.fieldcheck.controller.AuthController.me(..)) && " +
            "!execution(* com.fieldcheck.controller.*Controller.get*(..)) && " +
            "!execution(* com.fieldcheck.controller.*Controller.list*(..)) && " +
            "!execution(* com.fieldcheck.controller.*Controller.search*(..)) && " +
            "!execution(* com.fieldcheck.controller.*Controller.findBy*(..)) && " +
            "!execution(* com.fieldcheck.controller.ExecutionController.getProgress(..)) && " +
            "!execution(* com.fieldcheck.controller.ExecutionController.getLog(..))")
    public Object auditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;
        
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            try {
                saveAuditLog(joinPoint, result, exception, System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("Failed to save audit log: {}", e.getMessage());
            }
        }
    }

    private void saveAuditLog(ProceedingJoinPoint joinPoint, Object result, 
                              Exception exception, long duration) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        
        // 获取请求信息
        HttpServletRequest request = getRequest();
        String ipAddress = getClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        
        // 获取当前用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = "anonymous";
        Long userId = null;
        if (auth != null && auth.getPrincipal() instanceof User) {
            User userDetails = (User) auth.getPrincipal();
            username = userDetails.getUsername();
            // 通过用户名查询用户ID
            Optional<SysUser> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                userId = userOpt.get().getId();
            }
        } else if (auth != null) {
            username = auth.getName();
        }
        
        // 解析操作类型
        String action = parseAction(methodName, className);
        String targetType = parseTargetType(className);
        Long targetId = extractTargetId(joinPoint.getArgs(), result);
        String targetName = extractTargetName(joinPoint.getArgs(), result);
        String detail = buildDetail(methodName, className, joinPoint.getArgs(), result, exception);
        
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .detail(detail)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(exception == null)
                .build();
        
        auditLogRepository.save(auditLog);
        
        log.debug("Audit log saved: {} - {} - {} - {}ms", 
                username, action, targetName, duration);
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
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
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String parseAction(String methodName, String className) {
        // 根据方法名判断操作类型
        if (methodName.startsWith("create") || methodName.startsWith("add") || methodName.startsWith("save")) {
            return "CREATE";
        } else if (methodName.startsWith("update") || methodName.startsWith("edit") || methodName.startsWith("modify")) {
            return "UPDATE";
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "DELETE";
        } else if (methodName.startsWith("run") || methodName.startsWith("execute") || methodName.startsWith("start")) {
            return "EXECUTE";
        } else if (methodName.startsWith("stop") || methodName.startsWith("cancel")) {
            return "STOP";
        } else if (methodName.startsWith("login")) {
            return "LOGIN";
        } else if (methodName.startsWith("logout")) {
            return "LOGOUT";
        } else if (methodName.startsWith("test")) {
            return "TEST";
        } else if (methodName.startsWith("enable") || methodName.startsWith("disable")) {
            return "UPDATE";
        } else if (methodName.startsWith("reset")) {
            return "UPDATE";
        }
        return "OPERATION";
    }

    private String parseTargetType(String className) {
        // 根据Controller类名判断目标类型
        if (className.contains("Connection")) {
            return "Connection";
        } else if (className.contains("Task")) {
            return "Task";
        } else if (className.contains("Execution")) {
            return "Execution";
        } else if (className.contains("Risk")) {
            return "RiskResult";
        } else if (className.contains("Whitelist")) {
            return "WhitelistRule";
        } else if (className.contains("Alert")) {
            return "AlertConfig";
        } else if (className.contains("User")) {
            return "User";
        } else if (className.contains("Auth")) {
            return "Auth";
        }
        return className.replace("Controller", "");
    }

    private Long extractTargetId(Object[] args, Object result) {
        // 尝试从路径参数中提取ID
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof Integer) {
                    return ((Integer) arg).longValue();
                }
            }
        }
        return null;
    }

    private String extractTargetName(Object[] args, Object result) {
        // 尝试提取目标名称
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String && !((String) arg).isEmpty()) {
                    // 可能是名称
                    continue;
                }
                // 尝试从DTO中获取name字段
                try {
                    Method getNameMethod = arg.getClass().getMethod("getName");
                    Object name = getNameMethod.invoke(arg);
                    if (name instanceof String) {
                        return (String) name;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private String buildDetail(String methodName, String className, 
                               Object[] args, Object result, Exception exception) {
        StringBuilder detail = new StringBuilder();
        detail.append(className.replace("Controller", "")).append(".");
        detail.append(methodName);
        
        if (exception != null) {
            detail.append(" - 失败: ").append(exception.getMessage());
        } else {
            detail.append(" - 成功");
        }
        
        return detail.toString();
    }
}
