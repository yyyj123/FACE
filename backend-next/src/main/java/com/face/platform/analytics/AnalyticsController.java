package com.face.platform.analytics;

import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/analytics/sales")
public class AnalyticsController {

    private final TransactionAnalyticsQueryPort transactionAnalytics;

    public AnalyticsController(TransactionAnalyticsQueryPort transactionAnalytics) {
        this.transactionAnalytics = transactionAnalytics;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(defaultValue = "ALL") String itemType,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(transactionAnalytics.overview(
            principal(request), shopId, fromDate, toDate, itemType
        ));
    }

    @GetMapping("/lines")
    public ApiResponse<Map<String, Object>> lines(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(defaultValue = "ALL") String itemType,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(transactionAnalytics.lines(
            principal(request), shopId, fromDate, toDate, itemType, page, pageSize
        ));
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return principal;
    }
}
