package com.face.platform.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class AnalyticsReportCsv {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private AnalyticsReportCsv() {
    }

    public static GeneratedCsv generate(Map<String, Object> overview) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("报表", "经营分析概览"));
        rows.add(List.of("指标版本", nested(overview, "dataQuality", "metricVersion")));
        rows.add(List.of("开始日期", value(overview, "fromDate")));
        rows.add(List.of("结束日期", value(overview, "toDate")));
        rows.add(List.of("品项类型", value(overview, "itemType")));
        rows.add(List.of("授权门店", value(overview, "shopIds")));
        rows.add(List.of("数据截至", value(overview, "asOf")));
        rows.add(List.of("退款口径", nested(overview, "dataQuality", "refundAllocation")));
        rows.add(List.of());
        rows.add(List.of("经营汇总"));
        rows.add(List.of("订单数", "消费客户数", "实收金额", "退款金额", "净实收金额"));
        Map<String, Object> summary = map(overview.get("summary"));
        rows.add(List.of(
            value(summary, "orderCount"),
            value(summary, "consumingCustomerCount"),
            value(summary, "collectedAmount"),
            value(summary, "refundedAmount"),
            value(summary, "netCollectedAmount")
        ));

        addMapSection(rows, "门店汇总", list(overview.get("shopBreakdown")), List.of(
            new Column("门店ID", "shopId"),
            new Column("订单数", "orderCount"),
            new Column("净实收金额", "netCollectedAmount")
        ));
        addMapSection(rows, "项目排行", list(overview.get("ranking")), List.of(
            new Column("类型", "itemType"),
            new Column("项目", "itemName"),
            new Column("分类", "categoryName"),
            new Column("品牌", "brandName"),
            new Column("数量", "quantity"),
            new Column("退款前成交额", "lineSalesAmountBeforeRefund")
        ));
        addMapSection(rows, "每日趋势", list(overview.get("trend")), List.of(
            new Column("日期", "businessDate"),
            new Column("订单数", "orderCount"),
            new Column("实收金额", "collectedAmount"),
            new Column("退款金额", "refundedAmount"),
            new Column("净实收金额", "netCollectedAmount")
        ));

        StringBuilder text = new StringBuilder();
        for (List<Object> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) text.append(',');
                text.append(quote(neutralize(String.valueOf(row.get(index)))));
            }
            text.append("\r\n");
        }
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, bytes, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, bytes, UTF8_BOM.length, body.length);
        return new GeneratedCsv(bytes, new String(bytes, StandardCharsets.UTF_8), sha256(bytes), rows.size());
    }

    private static void addMapSection(
        List<List<Object>> rows,
        String title,
        List<Map<String, Object>> records,
        List<Column> columns
    ) {
        rows.add(List.of());
        rows.add(List.of(title));
        rows.add(columns.stream().map(Column::label).map(value -> (Object) value).toList());
        for (Map<String, Object> record : records) {
            rows.add(columns.stream().map(column -> value(record, column.key())).toList());
        }
    }

    private static String neutralize(String value) {
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static Object nested(Map<String, Object> source, String parent, String key) {
        return value(map(source.get(parent)), key);
    }

    private static Object value(Map<String, Object> source, String key) {
        Object result = source.get(key);
        return result == null ? "" : result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record Column(String label, String key) {
    }

    public record GeneratedCsv(byte[] bytes, String utf8Text, String sha256, int rowCount) {
    }
}
