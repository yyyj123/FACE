package com.face.platform.v3.booking;

import com.face.platform.booking.BookingAvailabilityService;
import com.face.platform.booking.BookingTimeLockService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class V3BookingController {

    private final BookingAvailabilityService availabilityService;
    private final BookingTimeLockService timeLockService;

    public V3BookingController(
        BookingAvailabilityService availabilityService,
        BookingTimeLockService timeLockService
    ) {
        this.availabilityService = availabilityService;
        this.timeLockService = timeLockService;
    }

    @GetMapping("/api/v3/open/booking/availability")
    public V3ApiResponse<Map<String, Object>> availability(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam(name = "service_id") long serviceId,
        @RequestParam(name = "staff_id", required = false) Long staffId,
        @RequestParam(name = "from_date", required = false) LocalDate fromDate,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            availabilityService.availability(shopId, serviceId, staffId, fromDate),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/booking/locks")
    public V3ApiResponse<Map<String, Object>> hold(
        @Valid @RequestBody HoldRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            timeLockService.hold(
                V3RequestSupport.principal(request),
                body.command()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/booking/locks/{token}/replace")
    public V3ApiResponse<Map<String, Object>> replace(
        @PathVariable String token,
        @Valid @RequestBody HoldRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            timeLockService.replace(V3RequestSupport.principal(request), token, body.command()),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/booking/locks/{token}/confirm")
    public V3ApiResponse<Map<String, Object>> confirm(
        @PathVariable String token,
        @Valid @RequestBody ConfirmRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            timeLockService.confirm(
                V3RequestSupport.principal(request), token, body.terms_version(),
                body.terms_confirmed(), body.member_note()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    @DeleteMapping("/api/v3/booking/locks/{token}")
    public V3ApiResponse<Map<String, Object>> release(
        @PathVariable String token,
        @RequestParam(name = "reason", required = false) String reason,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            timeLockService.release(V3RequestSupport.principal(request), token, reason),
            V3RequestSupport.requestId(request)
        );
    }

    public record HoldRequest(
        Long shop_id,
        @Min(1) long service_id,
        Long staff_id,
        @NotBlank @Size(max = 20) String assignment_mode,
        @NotNull LocalDateTime start_at,
        Long member_id,
        Boolean bypass_minimum_advance,
        @Size(max = 500) String proxy_reason
    ) {
        BookingTimeLockService.HoldCommand command() {
            return new BookingTimeLockService.HoldCommand(
                shop_id, service_id, staff_id, assignment_mode, start_at,
                member_id, Boolean.TRUE.equals(bypass_minimum_advance), proxy_reason, null
            );
        }
    }

    public record ConfirmRequest(
        @Min(1) int terms_version,
        boolean terms_confirmed,
        @Size(max = 1000) String member_note
    ) {
    }
}
