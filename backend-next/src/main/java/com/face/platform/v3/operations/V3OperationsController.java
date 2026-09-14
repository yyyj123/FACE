package com.face.platform.v3.operations;

import com.face.platform.appointment.AppointmentCreateRequest;
import com.face.platform.appointment.AppointmentService;
import com.face.platform.appointment.AppointmentStatusRequest;
import com.face.platform.client.ClientPortalService;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.servicecare.CareCorrectionRequest;
import com.face.platform.servicecare.CustomerConfirmationActionRequest;
import com.face.platform.servicecare.CustomerConfirmationService;
import com.face.platform.servicecare.ServiceCompleteRequest;
import com.face.platform.servicecare.ServiceConsumptionRequest;
import com.face.platform.servicecare.ServiceRecordCorrectionService;
import com.face.platform.servicecare.ServiceRecordService;
import com.face.platform.servicecare.ServiceStartRequest;
import com.face.platform.transaction.OrderCreateRequest;
import com.face.platform.transaction.OrderItemRequest;
import com.face.platform.transaction.PaymentApplicationService;
import com.face.platform.transaction.PaymentRequest;
import com.face.platform.transaction.TransactionService;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import com.face.platform.v3.auth.SessionTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3OperationsController {

    private final AppointmentService appointmentService;
    private final ServiceRecordService serviceRecordService;
    private final ServiceRecordCorrectionService correctionService;
    private final CustomerConfirmationService confirmationService;
    private final TransactionService transactionService;
    private final PaymentApplicationService paymentApplicationService;
    private final ClientPortalService clientPortalService;
    private final CommandIdempotencyService idempotencyService;

    public V3OperationsController(
        AppointmentService appointmentService,
        ServiceRecordService serviceRecordService,
        ServiceRecordCorrectionService correctionService,
        CustomerConfirmationService confirmationService,
        TransactionService transactionService,
        PaymentApplicationService paymentApplicationService,
        ClientPortalService clientPortalService,
        CommandIdempotencyService idempotencyService
    ) {
        this.appointmentService = appointmentService;
        this.serviceRecordService = serviceRecordService;
        this.correctionService = correctionService;
        this.confirmationService = confirmationService;
        this.transactionService = transactionService;
        this.paymentApplicationService = paymentApplicationService;
        this.clientPortalService = clientPortalService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/appointments")
    public V3ApiResponse<Map<String, Object>> appointments(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "50") int pageSize,
        HttpServletRequest request
    ) {
        return success(
            appointmentService.list(
                principal(request), shopId, from, to, status, keyword, page, pageSize
            ),
            request
        );
    }

    @PostMapping("/appointments")
    public V3ApiResponse<Map<String, Object>> createAppointment(
        @Valid @RequestBody AppointmentCreateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            idempotencyKey,
            "APPOINTMENT_CREATE",
            hash(body),
            () -> result[0] = appointmentService.create(
                principal,
                new AppointmentCreateRequest(
                    body.shop_id(),
                    body.member_id(),
                    body.staff_id(),
                    body.service_ids(),
                    body.resource_ids(),
                    body.start_at(),
                    body.source(),
                    body.member_note(),
                    body.internal_note()
                )
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
            request
        );
    }

    @PostMapping("/appointments/{appointmentId}/confirm")
    public V3ApiResponse<Map<String, Object>> confirmAppointment(
        @PathVariable long appointmentId,
        @Valid @RequestBody AppointmentActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return appointmentAction(
            appointmentId, body, idempotencyKey, "CONFIRMED", "APPOINTMENT_CONFIRM", request
        );
    }

    @PostMapping("/appointments/{appointmentId}/check-in")
    public V3ApiResponse<Map<String, Object>> checkInAppointment(
        @PathVariable long appointmentId,
        @Valid @RequestBody AppointmentActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return appointmentAction(
            appointmentId, body, idempotencyKey, "CHECKED_IN", "APPOINTMENT_CHECK_IN", request
        );
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public V3ApiResponse<Map<String, Object>> cancelAppointment(
        @PathVariable long appointmentId,
        @Valid @RequestBody AppointmentActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        if (body.reason() == null || body.reason().isBlank()) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "取消预约必须填写原因"
            );
        }
        return appointmentAction(
            appointmentId, body, idempotencyKey, "CANCELLED", "APPOINTMENT_CANCEL", request
        );
    }

    @PostMapping("/appointments/{appointmentId}/start")
    public V3ApiResponse<Map<String, Object>> startService(
        @PathVariable long appointmentId,
        @Valid @RequestBody StartServiceBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            idempotencyKey,
            "SERVICE_RECORD_START",
            hash(appointmentId, body),
            () -> result[0] = serviceRecordService.start(
                principal,
                new ServiceStartRequest(
                    body.shop_id(),
                    appointmentId,
                    body.appointment_version()
                )
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
            request
        );
    }

    @GetMapping("/service-records/{serviceRecordId}")
    public V3ApiResponse<Map<String, Object>> serviceRecord(
        @PathVariable long serviceRecordId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            serviceRecordService.detail(principal(request), shopId, serviceRecordId),
            request
        );
    }

    @PostMapping("/service-records/{serviceRecordId}/complete")
    public V3ApiResponse<Map<String, Object>> completeService(
        @PathVariable long serviceRecordId,
        @Valid @RequestBody CompleteServiceBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(
            serviceRecordService.complete(
                principal(request),
                serviceRecordId,
                new ServiceCompleteRequest(
                    body.shop_id(),
                    body.version(),
                    body.service_summary(),
                    body.next_visit_recommendation(),
                    body.skin_type(),
                    body.concerns(),
                    body.observations(),
                    body.home_care_advice(),
                    body.next_recommended_at(),
                    body.consumptions(),
                    requiredIdempotencyKey(idempotencyKey)
                )
            ),
            request
        );
    }

    @GetMapping("/service-records/{serviceRecordId}/corrections")
    public V3ApiResponse<List<Map<String, Object>>> corrections(
        @PathVariable long serviceRecordId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            correctionService.list(principal(request), shopId, serviceRecordId),
            request
        );
    }

    @PostMapping("/service-records/{serviceRecordId}/corrections")
    public V3ApiResponse<Map<String, Object>> appendCorrection(
        @PathVariable long serviceRecordId,
        @Valid @RequestBody CorrectionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(
            correctionService.append(
                principal(request),
                serviceRecordId,
                new CareCorrectionRequest(
                    body.shop_id(),
                    body.service_record_version(),
                    body.reason(),
                    body.corrected_fields(),
                    requiredIdempotencyKey(idempotencyKey)
                )
            ),
            request
        );
    }

    @GetMapping("/customer-confirmations")
    public V3ApiResponse<List<Map<String, Object>>> myConfirmations(
        HttpServletRequest request
    ) {
        return success(confirmationService.listMine(principal(request)), request);
    }

    @PostMapping("/customer-confirmations/{confirmationId}/action")
    public V3ApiResponse<Map<String, Object>> actOnConfirmation(
        @PathVariable long confirmationId,
        @Valid @RequestBody ConfirmationBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(
            confirmationService.act(
                principal(request),
                confirmationId,
                new CustomerConfirmationActionRequest(
                    body.action(),
                    body.reason(),
                    body.version(),
                    requiredIdempotencyKey(idempotencyKey)
                )
            ),
            request
        );
    }

    @PostMapping("/orders")
    public V3ApiResponse<Map<String, Object>> createOrder(
        @Valid @RequestBody OrderBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(
            transactionService.createOrder(
                principal(request),
                new OrderCreateRequest(
                    body.shop_id(),
                    body.member_id(),
                    body.appointment_id(),
                    body.items(),
                    body.notes()
                ),
                requiredIdempotencyKey(idempotencyKey)
            ),
            request
        );
    }

    @PostMapping("/orders/{orderId}/payments")
    public V3ApiResponse<Map<String, Object>> pay(
        @PathVariable long orderId,
        @Valid @RequestBody PaymentBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(
            paymentApplicationService.create(
                principal(request),
                orderId,
                new PaymentRequest(
                    body.shop_id(),
                    body.payment_method(),
                    body.amount(),
                    body.version(),
                    requiredIdempotencyKey(idempotencyKey),
                    body.external_transaction_no()
                )
            ),
            request
        );
    }

    @GetMapping("/technician/workbench")
    public V3ApiResponse<Map<String, Object>> technicianWorkbench(
        HttpServletRequest request
    ) {
        return success(clientPortalService.dashboard(principal(request)), request);
    }

    private V3ApiResponse<Map<String, Object>> appointmentAction(
        long appointmentId,
        AppointmentActionBody body,
        String idempotencyKey,
        String status,
        String operation,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            idempotencyKey,
            operation,
            hash(appointmentId, status, body),
            () -> result[0] = appointmentService.changeStatus(
                principal,
                appointmentId,
                new AppointmentStatusRequest(
                    body.shop_id(),
                    status,
                    body.version(),
                    body.reason()
                )
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
            request
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private String requiredIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 80) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED",
                "关键写操作必须提供有效的 Idempotency-Key"
            );
        }
        return value.trim();
    }

    private String hash(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : value.toString();
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        return SessionTokenCodec.sha256(canonical.toString());
    }

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record AppointmentCreateBody(
        @NotNull Long shop_id,
        @NotNull Long member_id,
        @NotNull Long staff_id,
        @NotEmpty List<Long> service_ids,
        List<Long> resource_ids,
        @NotNull LocalDateTime start_at,
        @NotBlank String source,
        String member_note,
        String internal_note
    ) {
    }

    public record AppointmentActionBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        String reason
    ) {
    }

    public record StartServiceBody(
        @NotNull Long shop_id,
        @NotNull Integer appointment_version
    ) {
    }

    public record CompleteServiceBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank String service_summary,
        String next_visit_recommendation,
        String skin_type,
        List<String> concerns,
        @NotBlank String observations,
        String home_care_advice,
        LocalDate next_recommended_at,
        List<ServiceConsumptionRequest> consumptions
    ) {
    }

    public record CorrectionBody(
        @NotNull Long shop_id,
        @NotNull Integer service_record_version,
        @NotBlank String reason,
        @NotEmpty Map<String, Object> corrected_fields
    ) {
    }

    public record ConfirmationBody(
        @NotBlank String action,
        String reason,
        @NotNull Integer version
    ) {
    }

    public record OrderBody(
        @NotNull Long shop_id,
        @NotNull Long member_id,
        Long appointment_id,
        List<OrderItemRequest> items,
        String notes
    ) {
    }

    public record PaymentBody(
        @NotNull Long shop_id,
        @NotBlank String payment_method,
        @NotNull BigDecimal amount,
        @NotNull Integer version,
        String external_transaction_no
    ) {
    }
}
