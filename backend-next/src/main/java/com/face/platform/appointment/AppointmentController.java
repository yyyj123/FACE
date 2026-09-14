package com.face.platform.appointment;

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
@RequestMapping("/api/v2/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(appointmentService.list(
            principal(request), shopId, fromDate, toDate, status, keyword, page, pageSize
        ));
    }

    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> resources(
        @RequestParam long shopId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(appointmentService.resources(principal(request), shopId, date));
    }

    @GetMapping("/availability")
    public ApiResponse<Map<String, Object>> availability(
        @RequestParam long shopId,
        @RequestParam long staffId,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam List<Long> serviceIds,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(appointmentService.availability(
            principal(request), shopId, staffId, date, serviceIds
        ));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody AppointmentCreateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(appointmentService.create(principal(request), body));
    }

    @PutMapping("/{appointmentId}/reschedule")
    public ApiResponse<Map<String, Object>> reschedule(
        @PathVariable long appointmentId,
        @Valid @RequestBody AppointmentRescheduleRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(appointmentService.reschedule(
            principal(request), appointmentId, body
        ));
    }

    @PostMapping("/{appointmentId}/status")
    public ApiResponse<Map<String, Object>> changeStatus(
        @PathVariable long appointmentId,
        @Valid @RequestBody AppointmentStatusRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(appointmentService.changeStatus(
            principal(request), appointmentId, body
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

