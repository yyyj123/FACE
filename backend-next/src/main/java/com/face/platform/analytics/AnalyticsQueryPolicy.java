package com.face.platform.analytics;

import com.face.platform.api.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

public final class AnalyticsQueryPolicy {

    private static final Set<String> ITEM_TYPES = Set.of("ALL", "SERVICE", "PRODUCT");

    private AnalyticsQueryPolicy() {
    }

    public static DateRange dateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate safeTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate safeFrom = fromDate == null ? safeTo.minusDays(29) : fromDate;
        if (safeTo.isBefore(safeFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (safeFrom.plusDays(92).isBefore(safeTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "经营分析单次最多查询93天");
        }
        return new DateRange(safeFrom, safeTo);
    }

    public static String itemType(String value) {
        String normalized = value == null || value.isBlank()
            ? "ALL"
            : value.trim().toUpperCase(Locale.ROOT);
        if (!ITEM_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "品项类型只能选择服务项目或零售商品");
        }
        return normalized;
    }

    public static DateRange previousRange(DateRange current) {
        long inclusiveDays = ChronoUnit.DAYS.between(
            current.fromDate(),
            current.toDate()
        ) + 1;
        LocalDate previousTo = current.fromDate().minusDays(1);
        return new DateRange(
            previousTo.minusDays(inclusiveDays - 1),
            previousTo
        );
    }

    public record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}
