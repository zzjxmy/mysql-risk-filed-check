package com.fieldcheck.entity;

public enum UserRole {
    ADMIN,      // 管理员：所有权限
    USER,       // 普通用户：可管理自己的任务
    READONLY    // 只读用户：仅查看
}
