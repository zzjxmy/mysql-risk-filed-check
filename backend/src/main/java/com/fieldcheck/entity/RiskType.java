package com.fieldcheck.entity;

public enum RiskType {
    INT_OVERFLOW,       // 整型溢出
    DECIMAL_OVERFLOW,   // 小数类型溢出
    Y2038,              // Y2038问题
    STRING_TRUNCATION,  // 字符串截断
    DATE_ANOMALY,       // 日期异常
    OTHER               // 其他风险
}
