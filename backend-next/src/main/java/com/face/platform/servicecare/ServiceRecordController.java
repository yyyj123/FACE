package com.face.platform.servicecare;

import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/service-records")
public class ServiceRecordController {

    private final ServiceRecordService serviceRecordService;
    private final ServiceRecordCorrectionService correctionService;

    public ServiceRecordController(
        ServiceRecordService serviceRecordService,
        ServiceRecordCorrectionService correctionService
    ) {
        this.serviceRecordService = serviceRecordService;
        this.correctionService = correctionService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam long shopId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(serviceRecordService.list(
            principal(request), shopId, fromDate, toDate,
            status, keyword, page, pageSize
        ));
    }

    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> resources(
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(serviceRecordService.resources(principal(request), shopId));
    }

    @GetMapping("/{serviceRecordId}")
    public ApiResponse<Map<String, Object>> detail(
        @PathVariable long serviceRecordId,
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.detail(principal(request), shopId, serviceRecordId)
        );
    }

    @GetMapping("/members/{memberId}/history")
    public ApiResponse<List<Map<String, Object>>> memberHistory(
        @PathVariable long memberId,
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.memberHistory(principal(request), shopId, memberId)
        );
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(
        @Valid @RequestBody ServiceStartRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(serviceRecordService.start(principal(request), body));
    }

    @PostMapping("/{serviceRecordId}/complete")
    public ApiResponse<Map<String, Object>> complete(
        @PathVariable long serviceRecordId,
        @Valid @RequestBody ServiceCompleteRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.complete(principal(request), serviceRecordId, body)
        );
    }

    @PutMapping("/{serviceRecordId}/care")
    public ApiResponse<Map<String, Object>> updateCare(
        @PathVariable long serviceRecordId,
        @Valid @RequestBody CareUpdateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.updateCare(principal(request), serviceRecordId, body)
        );
    }

    @GetMapping("/{serviceRecordId}/corrections")
    public ApiResponse<List<Map<String, Object>>> corrections(
        @PathVariable long serviceRecordId,
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            correctionService.list(principal(request), shopId, serviceRecordId)
        );
    }

    @PostMapping("/{serviceRecordId}/corrections")
    public ApiResponse<Map<String, Object>> appendCorrection(
        @PathVariable long serviceRecordId,
        @Valid @RequestBody CareCorrectionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            correctionService.append(principal(request), serviceRecordId, body)
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return principal;
    }
}
