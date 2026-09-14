package com.face.platform.analytics;

import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/analytics/reports")
public class AnalyticsReportController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AnalyticsReportSnapshotService reports;

    public AnalyticsReportController(AnalyticsReportSnapshotService reports) {
        this.reports = reports;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody CreateReportRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(reports.create(
            principal(request),
            idempotencyKey,
            new AnalyticsReportSnapshotService.CreateRequest(
                body.shopId(), body.fromDate(), body.toDate(), body.itemType(), body.format()
            )
        ));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(reports.list(principal(request), page, pageSize));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<Map<String, Object>> detail(
        @PathVariable long reportId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(reports.detail(principal(request), reportId));
    }

    @GetMapping("/{reportId}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable long reportId,
        HttpServletRequest request
    ) {
        AnalyticsReportSnapshotService.Download download = reports.download(
            principal(request), reportId
        );
        return ResponseEntity.ok()
            .contentType(CSV)
            .contentLength(download.content().length)
            .header("X-Content-SHA256", download.sha256())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(download.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(download.content());
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return principal;
    }

    public record CreateReportRequest(
        Long shopId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        String itemType,
        String format
    ) {
    }
}
