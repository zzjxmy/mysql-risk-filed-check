package com.fieldcheck.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableStatsServiceTest {

    @Test
    void buildTableStatsSqlFiltersSystemSchemasAndAvoidsDivideByZero() {
        TableStatsService.QueryPlan plan = TableStatsService.buildQueryPlan("biz", "order", 16.0);

        assertThat(plan.getSql()).contains("information_schema.TABLES");
        assertThat(plan.getSql()).contains("NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')");
        assertThat(plan.getSql()).contains("CASE WHEN data_length > 0 THEN");
        assertThat(plan.getSql()).contains("ORDER BY data_free DESC");
        assertThat(plan.getParameters()).containsExactly("biz", "%order%", "%order%", 16.0);
    }
}
