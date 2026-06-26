package com.fieldcheck.archive;

import com.fieldcheck.archive.engine.ArchiveSqlValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveSqlValidatorTest {

    @Test
    void allowsSingleSelectStatement() {
        assertDoesNotThrow(() -> ArchiveSqlValidator.validateSingleSelect(
                "select max(id) from hsq_online.sys_kpi_log where log_time < now()"));
    }

    @Test
    void rejectsNonSelectStatement() {
        assertThrows(IllegalArgumentException.class,
                () -> ArchiveSqlValidator.validateSingleSelect("delete from sys_kpi_log"));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> ArchiveSqlValidator.validateSingleSelect("select max(id) from t; select min(id) from t"));
    }

    @Test
    void rejectsBlankWhereTemplate() {
        assertThrows(IllegalArgumentException.class,
                () -> ArchiveSqlValidator.validateWhereTemplate("   "));
    }

    @Test
    void rejectsDangerousWhereTemplate() {
        assertThrows(IllegalArgumentException.class,
                () -> ArchiveSqlValidator.validateWhereTemplate("1=1"));
    }
}
