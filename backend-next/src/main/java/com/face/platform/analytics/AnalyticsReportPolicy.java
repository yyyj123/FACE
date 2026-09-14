package com.face.platform.analytics;

import com.face.platform.api.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Locale;

public final class AnalyticsReportPolicy {

    private AnalyticsReportPolicy() {
    }

    public static ValidatedRequest validate(
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType,
        String format
    ) {
        if (shopId != null && shopId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "门店标识不正确");
        }
        AnalyticsQueryPolicy.DateRange range = AnalyticsQueryPolicy.dateRange(fromDate, toDate);
        String normalizedType = AnalyticsQueryPolicy.itemType(itemType);
        String normalizedFormat = format == null || format.isBlank()
            ? "CSV"
            : format.trim().toUpperCase(Locale.ROOT);
        if (!"CSV".equals(normalizedFormat)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前仅支持安全的 CSV 经营报表");
        }
        return new ValidatedRequest(
            shopId,
            range.fromDate(),
            range.toDate(),
            normalizedType,
            normalizedFormat,
            "SALES_OVERVIEW"
        );
    }

    public record ValidatedRequest(
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType,
        String format,
        String reportType
    ) {
    }
}
