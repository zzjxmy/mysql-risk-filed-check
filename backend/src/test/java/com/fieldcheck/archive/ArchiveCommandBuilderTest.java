package com.fieldcheck.archive;

import com.fieldcheck.archive.engine.ArchiveCommandBuilder;
import com.fieldcheck.archive.engine.ArchiveCommandSpec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveCommandBuilderTest {

    @Test
    void buildsPtArchiverCommandWithNoDeleteWhenStepCopiesOnly() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("sys_kpi_log")
                .destHost("archive-db")
                .destPort(3306)
                .destUsername("archiver")
                .destPassword("archive-secret")
                .destDatabase("legacy_hsq_online")
                .destTable("sys_kpi_log")
                .whereClause("id < 1100000")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .deleteSource(false)
                .bulkInsert(true)
                .commitEach(true)
                .extraOptions(Arrays.asList("--why-quit", "--sleep", "1"))
                .build();

        List<String> command = ArchiveCommandBuilder.build(spec);

        assertEquals("/bin/pt-archiver", command.get(0));
        assertTrue(command.contains("--no-delete"));
        assertTrue(command.contains("--bulk-insert"));
        assertTrue(command.contains("--commit-each"));
        assertTrue(command.contains("--where"));
        assertEquals("id < 1100000", command.get(command.indexOf("--where") + 1));
        assertTrue(command.containsAll(Arrays.asList("--why-quit", "--sleep", "1")));
    }

    @Test
    void omitsNoDeleteWhenStepMovesRows() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("sys_kpi_log")
                .destHost("archive-db")
                .destPort(3306)
                .destUsername("archiver")
                .destPassword("archive-secret")
                .destDatabase("legacy_hsq_online")
                .destTable("sys_kpi_log")
                .whereClause("id < 1100000")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .deleteSource(true)
                .bulkInsert(true)
                .commitEach(true)
                .extraOptions(Collections.emptyList())
                .build();

        assertFalse(ArchiveCommandBuilder.build(spec).contains("--no-delete"));
    }

    @Test
    void enablesLocalInfileInDsnsWhenBulkInsertIsUsed() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("sys_kpi_log")
                .destHost("archive-db")
                .destPort(3306)
                .destUsername("archiver")
                .destPassword("archive-secret")
                .destDatabase("legacy_hsq_online")
                .destTable("sys_kpi_log")
                .whereClause("id < 1100000")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .deleteSource(true)
                .bulkInsert(true)
                .commitEach(true)
                .extraOptions(Collections.emptyList())
                .build();

        List<String> command = ArchiveCommandBuilder.build(spec);

        assertTrue(command.get(command.indexOf("--source") + 1).contains(",L=1"));
        assertTrue(command.get(command.indexOf("--dest") + 1).contains(",L=1"));
    }

    @Test
    void leavesLocalInfileDisabledWhenBulkInsertIsNotUsed() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("sys_kpi_log")
                .destHost("archive-db")
                .destPort(3306)
                .destUsername("archiver")
                .destPassword("archive-secret")
                .destDatabase("legacy_hsq_online")
                .destTable("sys_kpi_log")
                .whereClause("id < 1100000")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .deleteSource(true)
                .bulkInsert(false)
                .commitEach(true)
                .extraOptions(Collections.emptyList())
                .build();

        List<String> command = ArchiveCommandBuilder.build(spec);

        assertFalse(command.get(command.indexOf("--source") + 1).contains(",L=1"));
        assertFalse(command.get(command.indexOf("--dest") + 1).contains(",L=1"));
    }

    @Test
    void buildsPurgeCommandWithoutDestination() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .stepMode("PURGE")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("cart")
                .whereClause("user_id < 100")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .bulkInsert(false)
                .commitEach(true)
                .extraOptions(Collections.emptyList())
                .build();

        List<String> command = ArchiveCommandBuilder.build(spec);

        assertTrue(command.contains("--purge"));
        assertFalse(command.contains("--dest"));
        assertFalse(command.contains("--no-delete"));
    }

    @Test
    void includesSourceIndexNameInSourceDsn() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .stepMode("ARCHIVE")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("cart")
                .sourceIndexName("idx_c_userid")
                .destHost("archive-db")
                .destPort(3306)
                .destUsername("archiver")
                .destPassword("archive-secret")
                .destDatabase("legacy_hsq_online")
                .destTable("cart")
                .whereClause("user_id < 100")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .bulkInsert(false)
                .commitEach(true)
                .extraOptions(Collections.emptyList())
                .build();

        List<String> command = ArchiveCommandBuilder.build(spec);

        assertTrue(command.get(command.indexOf("--source") + 1).contains(",i=idx_c_userid"));
        assertFalse(command.get(command.indexOf("--dest") + 1).contains(",i=idx_c_userid"));
    }

    @Test
    void redactsPasswordsInLogCommand() {
        ArchiveCommandSpec spec = ArchiveCommandSpec.builder()
                .ptArchiverPath("/bin/pt-archiver")
                .sourceHost("source-db")
                .sourcePort(3306)
                .sourceUsername("archiver")
                .sourcePassword("secret")
                .sourceDatabase("hsq_online")
                .sourceTable("sys_kpi_log")
                .destHost("archive-db")
                .destPort(3306)
                .destUsername("archiver")
                .destPassword("archive-secret")
                .destDatabase("legacy_hsq_online")
                .destTable("sys_kpi_log")
                .whereClause("id < 1100000")
                .charset("UTF8")
                .limitSize(5000)
                .progressSize(5000)
                .deleteSource(true)
                .bulkInsert(true)
                .commitEach(true)
                .extraOptions(Collections.emptyList())
                .build();

        String redacted = ArchiveCommandBuilder.toRedactedLogLine(ArchiveCommandBuilder.build(spec));

        assertFalse(redacted.contains("secret"));
        assertFalse(redacted.contains("archive-secret"));
        assertTrue(redacted.contains("p=****"));
    }
}
