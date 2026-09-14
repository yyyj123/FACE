package com.face.platform.legacyimport;

import com.face.platform.api.ApiException;
import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/legacy-import")
public class LegacyImportController {

    public static final String DESKTOP_SURFACE = "DESKTOP";
    private final LegacyImportService service;

    public LegacyImportController(LegacyImportService service) {
        this.service = service;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(HttpServletRequest request) {
        service.requireView(principal(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("FACE-SC7-legacy-import-template.xlsx", StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(LegacyImportWorkbook.template(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/preflight", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> preflight(
        @RequestParam long shopId,
        @RequestPart MultipartFile file,
        @RequestHeader(name = "X-FACE-Admin-Surface", required = false) String surface,
        HttpServletRequest request
    ) throws IOException {
        requireDesktop(surface);
        return ApiResponse.ok(service.preflight(principal(request), shopId, file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping("/batches")
    public ApiResponse<Map<String, Object>> batches(
        @RequestParam long shopId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.batches(principal(request), shopId, page, pageSize));
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<Map<String, Object>> batch(@PathVariable long batchId, HttpServletRequest request) {
        return ApiResponse.ok(service.batch(principal(request), batchId));
    }

    @GetMapping(value = "/batches/{batchId}/issues", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> issues(@PathVariable long batchId, HttpServletRequest request) {
        byte[] bytes = service.issueReport(principal(request), batchId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=legacy-import-issues-" + batchId + ".csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(bytes);
    }

    @PostMapping("/batches/{batchId}/execute")
    public ApiResponse<Map<String, Object>> execute(
        @PathVariable long batchId,
        @Valid @RequestBody LegacyImportExecuteRequest body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        @RequestHeader(name = "X-FACE-Admin-Surface", required = false) String surface,
        HttpServletRequest request
    ) {
        requireDesktop(surface);
        return ApiResponse.ok(service.execute(principal(request), batchId, body.shopId(), idempotencyKey));
    }

    static void requireDesktop(String surface) {
        if (!DESKTOP_SURFACE.equalsIgnoreCase(surface == null ? "" : surface.trim())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "批量导入只能通过桌面端运营管理平台执行");
        }
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) throw new IllegalStateException("租户上下文未建立");
        return principal;
    }
}
