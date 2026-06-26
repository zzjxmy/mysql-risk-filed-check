package com.fieldcheck.archive;

import com.fieldcheck.archive.engine.ArchiveVariableRenderer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveVariableRendererTest {

    @Test
    void rendersVariablesInWhereTemplate() {
        Map<String, String> variables = new HashMap<>();
        variables.put("maxid", "1100000");

        String rendered = ArchiveVariableRenderer.render("id < ${maxid}", variables);

        assertEquals("id < 1100000", rendered);
    }

    @Test
    void reportsNullVariableAsSkippableStep() {
        Map<String, String> variables = new HashMap<>();
        variables.put("maxid", null);

        ArchiveVariableRenderer.MissingVariableException error = assertThrows(
                ArchiveVariableRenderer.MissingVariableException.class,
                () -> ArchiveVariableRenderer.render("id < ${maxid}", variables));

        assertTrue(error.getMessage().contains("maxid"));
    }
}
