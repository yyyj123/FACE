package com.face.platform.legacyimport;

import com.face.platform.api.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyImportWorkbook {

    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private static final Map<String, SheetDefinition> SHEETS = new LinkedHashMap<>();

    static {
        SHEETS.put("客户资料", new SheetDefinition("MEMBER", List.of(
            "来源系统*", "原会员号*", "姓名*", "手机号*", "性别", "生日(yyyy-MM-dd)", "门店编码*"
        ), List.of("sourceSystem", "originalMemberNo", "name", "phone", "gender", "birthday", "shopCode")));
        SHEETS.put("次数组合卡", new SheetDefinition("COMBO_CARD", List.of(
            "来源系统*", "原卡号*", "原会员号*", "门店编码*", "卡产品编码*", "原购买日*", "到期日*", "总次数*", "剩余次数*"
        ), List.of("sourceSystem", "originalCardNo", "originalMemberNo", "shopCode", "packageCode", "purchaseDate", "expiresAt", "totalQuantity", "remainingQuantity")));
        SHEETS.put("组合卡护理明细", new SheetDefinition("COMBO_DETAIL", List.of(
            "来源系统*", "原卡号*", "护理项目编码*", "总次数*", "剩余次数*"
        ), List.of("sourceSystem", "originalCardNo", "serviceCode", "totalQuantity", "remainingQuantity")));
        SHEETS.put("储值卡", new SheetDefinition("STORED_VALUE_CARD", List.of(
            "来源系统*", "原卡号*", "原会员号*", "门店编码*", "卡产品编码*", "原购买日*", "到期日*", "剩余本金*", "剩余赠送金*"
        ), List.of("sourceSystem", "originalCardNo", "originalMemberNo", "shopCode", "packageCode", "purchaseDate", "expiresAt", "principalRemaining", "giftRemaining")));
        SHEETS.put("折扣卡", new SheetDefinition("DISCOUNT_CARD", List.of(
            "来源系统*", "原卡号*", "原会员号*", "门店编码*", "卡产品编码*", "原购买日*", "到期日*", "剩余可用次数*"
        ), List.of("sourceSystem", "originalCardNo", "originalMemberNo", "shopCode", "packageCode", "purchaseDate", "expiresAt", "remainingUses")));
    }

    private LegacyImportWorkbook() {
    }

    public static byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (Map.Entry<String, SheetDefinition> entry : SHEETS.entrySet()) {
                Sheet sheet = workbook.createSheet(entry.getKey());
                Row header = sheet.createRow(0);
                for (int index = 0; index < entry.getValue().headers().size(); index++) {
                    header.createCell(index).setCellValue(entry.getValue().headers().get(index));
                    sheet.setColumnWidth(index, Math.min(30, Math.max(14, entry.getValue().headers().get(index).length() + 4)) * 256);
                }
                sheet.createFreezePane(0, 1);
            }
            Sheet guide = workbook.createSheet("填写说明");
            guide.createRow(0).createCell(0).setCellValue("带 * 的字段必填；请勿修改工作表名称或第一行表头。正式导入仅超级管理员可执行。历史卡不会与新卡合并。");
            guide.setColumnWidth(0, 110 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Excel 模板生成失败", exception);
        }
    }

    public static List<ParsedRow> parse(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel 文件不能为空且不能超过 10MB");
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter();
            List<ParsedRow> result = new ArrayList<>();
            for (Map.Entry<String, SheetDefinition> entry : SHEETS.entrySet()) {
                Sheet sheet = workbook.getSheet(entry.getKey());
                if (sheet == null) continue;
                verifyHeader(sheet, entry.getValue(), formatter);
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || blank(row, entry.getValue().keys().size(), formatter)) continue;
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int cellIndex = 0; cellIndex < entry.getValue().keys().size(); cellIndex++) {
                        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values.put(entry.getValue().keys().get(cellIndex), cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    result.add(new ParsedRow(entry.getKey(), rowIndex + 1, entry.getValue().recordType(), values));
                }
            }
            if (result.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Excel 中没有可预检的数据行");
            if (result.size() > 10_000) throw new ApiException(HttpStatus.BAD_REQUEST, "单批次最多允许 10000 行");
            return List.copyOf(result);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel 文件无法读取，请使用系统模板并保持 .xlsx 格式");
        }
    }

    private static void verifyHeader(Sheet sheet, SheetDefinition definition, DataFormatter formatter) {
        Row header = sheet.getRow(0);
        if (header == null) throw new ApiException(HttpStatus.BAD_REQUEST, sheet.getSheetName() + " 缺少表头");
        for (int index = 0; index < definition.headers().size(); index++) {
            String actual = formatter.formatCellValue(header.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();
            if (!definition.headers().get(index).equals(actual)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, sheet.getSheetName() + " 表头与模板不一致");
            }
        }
    }

    private static boolean blank(Row row, int cells, DataFormatter formatter) {
        for (int index = 0; index < cells; index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) return false;
        }
        return true;
    }

    public record ParsedRow(String sheetName, int rowNumber, String recordType, Map<String, String> values) {
    }

    private record SheetDefinition(String recordType, List<String> headers, List<String> keys) {
    }
}
