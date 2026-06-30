package com.fieldcheck.archive.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ArchiveCommandBuilder {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("p=[^,\\s]+");

    private ArchiveCommandBuilder() {
    }

    public static List<String> build(ArchiveCommandSpec spec) {
        ArchiveSqlValidator.validateWhereTemplate(spec.getWhereClause());
        boolean purgeMode = "PURGE".equalsIgnoreCase(valueOrDefault(spec.getStepMode(), "ARCHIVE"));

        List<String> command = new ArrayList<>();
        command.add(valueOrDefault(spec.getPtArchiverPath(), "/bin/pt-archiver"));
        command.add("--source");
        command.add(connectionSpec(
                spec.getSourceHost(),
                spec.getSourcePort(),
                spec.getSourceUsername(),
                spec.getSourcePassword(),
                spec.getSourceDatabase(),
                spec.getSourceTable(),
                spec.getSourceIndexName(),
                Boolean.TRUE.equals(spec.getBulkInsert())));
        if (purgeMode) {
            command.add("--purge");
        } else {
            command.add("--dest");
            command.add(connectionSpec(
                    spec.getDestHost(),
                    spec.getDestPort(),
                    spec.getDestUsername(),
                    spec.getDestPassword(),
                    spec.getDestDatabase(),
                    spec.getDestTable(),
                    null,
                    Boolean.TRUE.equals(spec.getBulkInsert())));
        }
        command.add("--where");
        command.add(spec.getWhereClause());
        command.add("--charset");
        command.add(valueOrDefault(spec.getCharset(), "UTF8"));
        command.add("--no-version-check");
        command.add("--no-check-charset");
        command.add("--progress");
        command.add(String.valueOf(valueOrDefault(spec.getProgressSize(), 5000)));
        command.add("--limit");
        command.add(String.valueOf(valueOrDefault(spec.getLimitSize(), 5000)));
        if (Boolean.TRUE.equals(spec.getBulkInsert())) {
            command.add("--bulk-insert");
        }
        if (Boolean.TRUE.equals(spec.getCommitEach())) {
            command.add("--commit-each");
        }
        if (!purgeMode && !Boolean.TRUE.equals(spec.getDeleteSource())) {
            command.add("--no-delete");
        }
        if (Boolean.TRUE.equals(spec.getDryRun())) {
            command.add("--dry-run");
        }
        command.add("--statistics");
        if (spec.getExtraOptions() != null) {
            command.addAll(spec.getExtraOptions());
        }
        return command;
    }

    public static String toRedactedLogLine(List<String> command) {
        List<String> redacted = new ArrayList<>();
        for (String part : command) {
            redacted.add(PASSWORD_PATTERN.matcher(part).replaceAll("p=****"));
        }
        return String.join(" ", redacted);
    }

    private static String connectionSpec(String host, Integer port, String username, String password, String database, String table, String indexName, boolean localInfile) {
        String spec = "h=" + host +
                ",P=" + valueOrDefault(port, 3306) +
                ",u=" + username +
                ",p=" + password +
                ",D=" + database +
                ",t=" + table;
        if (indexName != null && !indexName.trim().isEmpty()) {
            spec += ",i=" + indexName.trim();
        }
        return localInfile ? spec + ",L=1" : spec;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private static Integer valueOrDefault(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
