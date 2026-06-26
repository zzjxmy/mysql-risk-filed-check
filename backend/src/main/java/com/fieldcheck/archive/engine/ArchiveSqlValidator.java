package com.fieldcheck.archive.engine;

public final class ArchiveSqlValidator {

    private ArchiveSqlValidator() {
    }

    public static void validateSingleSelect(String sql) {
        String normalized = normalize(sql);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("SQL不能为空");
        }
        if (!normalized.toLowerCase().startsWith("select ")) {
            throw new IllegalArgumentException("预查询SQL只允许SELECT语句");
        }
        if (containsStatementSeparator(normalized)) {
            throw new IllegalArgumentException("预查询SQL不允许多语句");
        }
    }

    public static void validateWhereTemplate(String whereTemplate) {
        String normalized = normalize(whereTemplate);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("归档条件不能为空");
        }
        String lower = normalized.toLowerCase();
        if ("1=1".equals(lower) || "true".equals(lower) || lower.contains(";")) {
            throw new IllegalArgumentException("归档条件存在风险");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsStatementSeparator(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.contains(";");
    }
}
