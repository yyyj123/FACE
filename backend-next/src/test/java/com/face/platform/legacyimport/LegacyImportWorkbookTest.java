package com.face.platform.legacyimport;

import com.face.platform.api.ApiException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyImportWorkbookTest {

    @Test
    void templateContainsAllFiveBusinessSheetsAndCanBeParsed() throws Exception {
        byte[] template = LegacyImportWorkbook.template();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertTrue(workbook.getSheetIndex("客户资料") >= 0);
            assertTrue(workbook.getSheetIndex("次数组合卡") >= 0);
            assertTrue(workbook.getSheetIndex("组合卡护理明细") >= 0);
            assertTrue(workbook.getSheetIndex("储值卡") >= 0);
            assertTrue(workbook.getSheetIndex("折扣卡") >= 0);
            var row = workbook.getSheet("客户资料").createRow(1);
            row.createCell(0).setCellValue("LEGACY_CRM");
            row.createCell(1).setCellValue("M-001");
            row.createCell(2).setCellValue("测试会员");
            row.createCell(3).setCellValue("13800000000");
            row.createCell(6).setCellValue("S001");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            var parsed = LegacyImportWorkbook.parse(output.toByteArray());
            assertEquals(1, parsed.size());
            assertEquals("MEMBER", parsed.getFirst().recordType());
            assertEquals("M-001", parsed.getFirst().values().get("originalMemberNo"));
        }
    }

    @Test
    void rejectsEmptyAndOversizedFilesBeforeParsing() {
        assertThrows(ApiException.class, () -> LegacyImportWorkbook.parse(new byte[0]));
        assertThrows(ApiException.class, () -> LegacyImportWorkbook.parse(
            new byte[LegacyImportWorkbook.MAX_FILE_BYTES + 1]
        ));
    }
}
