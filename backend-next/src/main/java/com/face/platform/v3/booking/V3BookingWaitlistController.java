package com.face.platform.v3.booking;

import com.face.platform.booking.BookingWaitlistService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
public class V3BookingWaitlistController {

    private final BookingWaitlistService service;

    public V3BookingWaitlistController(BookingWaitlistService service) {
        this.service = service;
    }

    @PostMapping("/api/v3/booking/waitlist")
    public V3ApiResponse<Map<String, Object>> join(
        @Valid @RequestBody JoinRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.join(V3RequestSupport.principal(request), body.command()),
            V3RequestSupport.requestId(request)
        );
    }

    @GetMapping("/api/v3/booking/waitlist/mine")
    public V3ApiResponse<List<Map<String, Object>>> mine(HttpServletRequest request) {
        return V3ApiResponse.success(
            service.mine(V3RequestSupport.principal(request)),
            V3RequestSupport.requestId(request)
        );
    }

    @DeleteMapping("/api/v3/booking/waitlist/{waitlistId}")
    public V3ApiResponse<Map<String, Object>> cancel(
        @PathVariable long waitlistId,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.cancel(V3RequestSupport.principal(request), waitlistId),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/booking/waitlist/{waitlistId}/confirm")
    public V3ApiResponse<Map<String, Object>> confirm(
        @PathVariable long waitlistId,
        @Valid @RequestBody ConfirmRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.confirm(
                V3RequestSupport.principal(request), waitlistId,
                body.terms_version(), body.terms_confirmed(), body.member_note()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/booking/waitlist/process-vacancy")
    public V3ApiResponse<Map<String, Object>> processVacancy(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @Valid @RequestBody VacancyRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.processVacancy(
                V3RequestSupport.principal(request), shopId,
                body.service_id(), body.staff_id(), body.start_at()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    public record JoinRequest(
        Long shop_id,
        @Min(1) long service_id,
        Long requested_staff_id,
        @NotNull LocalDate date_from,
        @NotNull LocalDate date_to,
        @NotNull LocalTime time_from,
        @NotNull LocalTime time_to,
        @Min(0) @Max(240) int flexibility_minutes,
        boolean accept_other_staff
    ) {
        BookingWaitlistService.JoinCommand command() {
            return new BookingWaitlistService.JoinCommand(
                shop_id, service_id, requested_staff_id, date_from, date_to,
                time_from, time_to, flexibility_minutes, accept_other_staff
            );
        }
    }

    public record ConfirmRequest(
        @Min(1) int terms_version,
        boolean terms_confirmed,
        @Size(max = 1000) String member_note
    ) {
    }

    public record VacancyRequest(
        @Min(1) long service_id,
        @Min(1) long staff_id,
        @NotNull LocalDateTime start_at
    ) {
    }
}
