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
@RequestMapping("/api/v3/analytics/operations")
public class OperationsOverviewController {

    private final OperationsOverviewService operationsOverview;

    public OperationsOverviewController(OperationsOverviewService operationsOverview) {
        this.operationsOverview = operationsOverview;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        HttpServletRequest request
    ) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return ApiResponse.ok(operationsOverview.overview(principal, shopId, fromDate, toDate));
    }
}
