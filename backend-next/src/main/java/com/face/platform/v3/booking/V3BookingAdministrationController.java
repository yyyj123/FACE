package com.face.platform.v3.booking;

import com.face.platform.booking.BookingAdministrationService;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import com.face.platform.v3.auth.SessionTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class V3BookingAdministrationController {

    private final BookingAdministrationService service;
    private final CommandIdempotencyService idempotencyService;

    public V3BookingAdministrationController(
        BookingAdministrationService service,
        CommandIdempotencyService idempotencyService
    ) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/api/v3/booking/admin/policies")
    public V3ApiResponse<List<Map<String, Object>>> policies(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.policies(V3RequestSupport.principal(request), shopId),
            V3RequestSupport.requestId(request)
        );
    }

    @PutMapping("/api/v3/booking/admin/policies/{serviceId}")
    public V3ApiResponse<Map<String, Object>> updatePolicy(
        @PathVariable long serviceId,
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @Valid @RequestBody PolicyRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.updatePolicy(
                V3RequestSupport.principal(request), shopId, serviceId, body.command()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    @GetMapping("/api/v3/booking/admin/schedules")
    public V3ApiResponse<Map<String, Object>> schedules(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam(name = "from") LocalDate from,
        @RequestParam(name = "to") LocalDate to,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.schedules(V3RequestSupport.principal(request), shopId, from, to),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/booking/admin/schedules/facts")
    public V3ApiResponse<Map<String, Object>> addDateFact(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @Valid @RequestBody DateFactRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        idempotencyService.run(
            principal, idempotencyKey, "SCHEDULE_FACT_CREATE", hash(shopId, body),
            () -> result.set(service.addDateFact(principal, shopId, body.command()))
        );
        return success(result, request);
    }

    @PutMapping("/api/v3/booking/admin/schedules/facts/{factId}")
    public V3ApiResponse<Map<String, Object>> updateDateFact(
        @PathVariable long factId,
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @Valid @RequestBody DateFactUpdateRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        idempotencyService.run(
            principal, idempotencyKey, "SCHEDULE_FACT_UPDATE", hash(factId, shopId, body),
            () -> result.set(service.updateDateFact(
                principal, shopId, factId, body.command(), body.version()
            ))
        );
        return success(result, request);
    }

    @DeleteMapping("/api/v3/booking/admin/schedules/facts/{factId}")
    public V3ApiResponse<Map<String, Object>> deactivateDateFact(
        @PathVariable long factId,
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam int version,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        idempotencyService.run(
            principal, idempotencyKey, "SCHEDULE_FACT_DEACTIVATE", hash(factId, shopId, version),
            () -> result.set(service.deactivateDateFact(principal, shopId, factId, version))
        );
        return success(result, request);
    }

    @PostMapping("/api/v3/booking/admin/schedules/rules")
    public V3ApiResponse<Map<String, Object>> addRecurringRule(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @Valid @RequestBody RecurringRuleRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        idempotencyService.run(
            principal, idempotencyKey, "SCHEDULE_RULE_CREATE", hash(shopId, body),
            () -> result.set(service.addRecurringRule(principal, shopId, body.command()))
        );
        return success(result, request);
    }

    @DeleteMapping("/api/v3/booking/admin/schedules/rules/{ruleId}")
    public V3ApiResponse<Map<String, Object>> deactivateRule(
        @PathVariable long ruleId,
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam int version,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        idempotencyService.run(
            principal, idempotencyKey, "SCHEDULE_RULE_DEACTIVATE", hash(ruleId, shopId, version),
            () -> result.set(service.deactivateRule(principal, shopId, ruleId, version))
        );
        return success(result, request);
    }

    @PostMapping("/api/v3/booking/admin/schedules/rules/{ruleId}/activate")
    public V3ApiResponse<Map<String, Object>> activateRule(
        @PathVariable long ruleId,
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam int version,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        idempotencyService.run(
            principal, idempotencyKey, "SCHEDULE_RULE_ACTIVATE", hash(ruleId, shopId, version),
            () -> result.set(service.activateRule(principal, shopId, ruleId, version))
        );
        return success(result, request);
    }

    public record PolicyRequest(
        @Min(60) @Max(60) int slot_interval_minutes,
        @Min(0) @Max(240) int buffer_before_minutes,
        @Min(0) @Max(240) int buffer_after_minutes,
        @Min(0) @Max(43200) int minimum_advance_minutes,
        boolean same_day_booking_allowed,
        @Min(0) @Max(43200) int free_cancel_minutes,
        @Min(0) @Max(43200) int reschedule_cutoff_minutes,
        @Min(0) @Max(20) int max_reschedules,
        @NotBlank @Size(max = 30) String late_cancel_policy,
        @NotNull BigDecimal late_cancel_value,
        @Size(max = 2000) String booking_notice
    ) {
        BookingAdministrationService.PolicyCommand command() {
            return new BookingAdministrationService.PolicyCommand(
                slot_interval_minutes, buffer_before_minutes, buffer_after_minutes,
                minimum_advance_minutes, same_day_booking_allowed, free_cancel_minutes,
                reschedule_cutoff_minutes, max_reschedules, late_cancel_policy,
                late_cancel_value, booking_notice
            );
        }
    }

    public record DateFactRequest(
        @Min(1) long staff_id,
        @NotNull LocalDate schedule_date,
        LocalTime start_time,
        LocalTime end_time,
        @NotBlank @Size(max = 20) String schedule_type,
        @Size(max = 255) String remark
    ) {
        BookingAdministrationService.DateFactCommand command() {
            return new BookingAdministrationService.DateFactCommand(
                staff_id, schedule_date, start_time, end_time, schedule_type, remark
            );
        }
    }

    public record DateFactUpdateRequest(
        @Min(1) long staff_id,
        @NotNull LocalDate schedule_date,
        LocalTime start_time,
        LocalTime end_time,
        @NotBlank @Size(max = 20) String schedule_type,
        @Size(max = 255) String remark,
        @Min(0) int version
    ) {
        BookingAdministrationService.DateFactCommand command() {
            return new BookingAdministrationService.DateFactCommand(
                staff_id, schedule_date, start_time, end_time, schedule_type, remark
            );
        }
    }

    public record RecurringRuleRequest(
        @Min(1) long staff_id,
        @Min(1) @Max(7) int day_of_week,
        @NotNull LocalTime start_time,
        @NotNull LocalTime end_time,
        @NotBlank @Size(max = 20) String rule_type,
        @NotNull LocalDate effective_from,
        LocalDate effective_to
    ) {
        BookingAdministrationService.RecurringRuleCommand command() {
            return new BookingAdministrationService.RecurringRuleCommand(
                staff_id, day_of_week, start_time, end_time, rule_type,
                effective_from, effective_to
            );
        }
    }

    private V3ApiResponse<Map<String, Object>> success(
        AtomicReference<Map<String, Object>> result,
        HttpServletRequest request
    ) {
        Map<String, Object> payload = result.get();
        if (payload == null) payload = Map.of("accepted", true, "replayed", true);
        return V3ApiResponse.success(payload, V3RequestSupport.requestId(request));
    }

    private String hash(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : value.toString();
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        return SessionTokenCodec.sha256(canonical.toString());
    }
}
