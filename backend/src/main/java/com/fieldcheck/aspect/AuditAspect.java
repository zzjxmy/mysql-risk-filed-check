package com.fieldcheck.aspect;

import com.fieldcheck.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 审计日志AOP切面
 * 自动记录标注了@Auditable注解的方法调用
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Pointcut("@annotation(com.fieldcheck.aspect.Auditable)")
    public void auditPointcut() {}

    @AfterReturning(pointcut = "auditPointcut()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        try {
            recordAuditLog(joinPoint, true, null);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    @AfterThrowing(pointcut = "auditPointcut()", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Exception ex) {
        try {
            recordAuditLog(joinPoint, false, ex.getMessage());
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    private void recordAuditLog(JoinPoint joinPoint, boolean success, String errorMsg) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);
        
        if (auditable == null) return;

        String action = auditable.action();
        String targetType = auditable.targetType();
        Long targetId = extractTargetId(joinPoint, auditable.targetIdParam());
        String targetName = extractTargetName(joinPoint, auditable.targetNameParam());
        
        String detail = buildDetail(auditable.description(), success, errorMsg);
        HttpServletRequest request = getCurrentRequest();
        
        auditLogService.logAsync(action, targetType, targetId, targetName, detail, request);
    }

    private Long extractTargetId(JoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        
        Object value = getParameterValue(joinPoint, paramName);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractTargetName(JoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        
        Object value = getParameterValue(joinPoint, paramName);
        return value != null ? value.toString() : null;
    }

    private Object getParameterValue(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                return args[i];
            }
        }
        
        // 尝试从参数名数组中获取
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if (parameterNames[i].equals(paramName)) {
                    return args[i];
                }
            }
        }
        
        return null;
    }

    private String buildDetail(String template, boolean success, String errorMsg) {
        StringBuilder sb = new StringBuilder();
        
        if (template != null && !template.isEmpty()) {
            sb.append(template);
        }
        
        if (!success && errorMsg != null) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append("失败原因: ").append(errorMsg);
        }
        
        return sb.toString();
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
