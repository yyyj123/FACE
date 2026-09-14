package com.face.platform.legacyimport;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;

public final class LegacyImportFixtureWriter {

    private LegacyImportFixtureWriter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                "Expected output, suffix, shopCode, comboCode, storedCode, discountCode, serviceCode and memberName"
            );
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(LegacyImportWorkbook.template()))) {
            String suffix = args[1];
            row(workbook.getSheet("客户资料"), 1,
                "SC7_RUNTIME", "MEM-" + suffix, args[7], "138" + suffix.substring(Math.max(0, suffix.length() - 8)), "", "", args[2]);
            row(workbook.getSheet("次数组合卡"), 1,
                "SC7_RUNTIME", "COMBO-" + suffix, "MEM-" + suffix, args[2], args[3], "2025-01-01", "2030-12-31", "10", "7");
            row(workbook.getSheet("组合卡护理明细"), 1,
                "SC7_RUNTIME", "COMBO-" + suffix, args[6], "10", "7");
            row(workbook.getSheet("储值卡"), 1,
                "SC7_RUNTIME", "STORED-" + suffix, "MEM-" + suffix, args[2], args[4], "2025-01-01", "2030-12-31", "100", "20");
            row(workbook.getSheet("折扣卡"), 1,
                "SC7_RUNTIME", "DISCOUNT-" + suffix, "MEM-" + suffix, args[2], args[5], "2025-01-01", "2030-12-31", "5");
            Path output = Path.of(args[0]).toAbsolutePath().normalize();
            try (FileOutputStream stream = new FileOutputStream(output.toFile())) {
                workbook.write(stream);
            }
        }
    }

    private static void row(Sheet sheet, int index, String... values) {
        var row = sheet.createRow(index);
        for (int cell = 0; cell < values.length; cell++) row.createCell(cell).setCellValue(values[cell]);
    }
}
