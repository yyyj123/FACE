package com.face.platform.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsReportCsvTest {

    @Test
    void createsStableUtf8SnapshotAndNeutralizesSpreadsheetFormulaInjection() {
        Map<String, Object> overview = Map.ofEntries(
            Map.entry("fromDate", LocalDate.of(2026, 7, 1)),
            Map.entry("toDate", LocalDate.of(2026, 7, 28)),
            Map.entry("itemType", "ALL"),
            Map.entry("shopIds", List.of(2L)),
            Map.entry("asOf", Instant.parse("2026-08-01T08:00:00Z")),
            Map.entry("dataQuality", Map.of(
                "metricVersion", "M6-04-v1",
                "refundAllocation", "ORDER_LEVEL_ONLY"
            )),
            Map.entry("summary", Map.of(
                "orderCount", 3,
                "consumingCustomerCount", 2,
                "collectedAmount", new BigDecimal("288.00"),
                "refundedAmount", new BigDecimal("20.00"),
                "netCollectedAmount", new BigDecimal("268.00")
            )),
            Map.entry("comparison", Map.of(
                "previousFromDate", LocalDate.of(2026, 6, 3),
                "previousToDate", LocalDate.of(2026, 6, 30),
                "previousSummary", Map.of("netCollectedAmount", new BigDecimal("100.00"))
            )),
            Map.entry("shopBreakdown", List.of(Map.of(
                "shopId", 2L,
                "orderCount", 3,
                "netCollectedAmount", new BigDecimal("268.00")
            ))),
            Map.entry("ranking", List.of(Map.of(
                "itemType", "PRODUCT",
                "itemName", "=HYPERLINK(\"https://invalid.example\")",
                "categoryName", "+高风险文本",
                "brandName", "@品牌",
                "quantity", 1,
                "lineSalesAmountBeforeRefund", new BigDecimal("288.00")
            ))),
            Map.entry("trend", List.of())
        );

        AnalyticsReportCsv.GeneratedCsv generated = AnalyticsReportCsv.generate(overview);
        String csv = generated.utf8Text();

        assertThat(generated.bytes()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(generated.sha256()).matches("[0-9a-f]{64}");
        assertThat(generated.rowCount()).isGreaterThan(10);
        assertThat(csv).contains("M6-04-v1");
        assertThat(csv).contains("'" + "=HYPERLINK");
        assertThat(csv).contains("'+高风险文本");
        assertThat(csv).contains("'@品牌");
        assertThat(csv).doesNotContain("13800138000");
    }
}
