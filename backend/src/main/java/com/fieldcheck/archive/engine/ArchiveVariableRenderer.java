package com.fieldcheck.archive.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchiveVariableRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");

    private ArchiveVariableRenderer() {
    }

    public static String render(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template == null ? "" : template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null || value.trim().isEmpty()) {
                throw new MissingVariableException("变量 " + name + " 为空，跳过归档步骤");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static class MissingVariableException extends RuntimeException {
        public MissingVariableException(String message) {
            super(message);
        }
    }
}
