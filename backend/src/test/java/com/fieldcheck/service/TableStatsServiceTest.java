package com.fieldcheck.service;

import com.fieldcheck.dto.TableStatsDTO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;

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

    @Test
    void exportToExcelIncludesTableStatsHeadersAndRows() throws Exception {
        TableStatsDTO stat = TableStatsDTO.builder()
                .schemaName("biz")
                .tableName("orders")
                .tableComment("订单表")
                .tableRows(1200L)
                .dataSizeMb(32.5)
                .indexSizeMb(8.25)
                .totalSizeMb(40.75)
                .fragmentSizeMb(3.5)
                .fragmentPercent(10.77)
                .engine("InnoDB")
                .build();

        byte[] content = TableStatsService.exportToExcel(Collections.singletonList(stat));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("表空间分析");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("库名");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(9).getStringCellValue()).isEqualTo("表引擎");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("biz");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("orders");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(3).getNumericCellValue()).isEqualTo(1200);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(8).getNumericCellValue()).isEqualTo(10.77);
        }
    }
}
