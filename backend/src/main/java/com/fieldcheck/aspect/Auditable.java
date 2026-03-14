package com.fieldcheck.aspect;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 用于标记需要记录审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    
    /**
     * 操作类型
     */
    String action();
    
    /**
     * 目标类型 (Connection, Task, etc.)
     */
    String targetType() default "";
    
    /**
     * 目标ID参数名称 (用于从方法参数中提取)
     */
    String targetIdParam() default "id";
    
    /**
     * 目标名称参数名称
     */
    String targetNameParam() default "";
    
    /**
     * 描述模板
     */
    String description() default "";
}
