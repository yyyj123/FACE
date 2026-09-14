package com.face.platform.transaction;

import com.face.platform.api.ApiResponse;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final RefundApplicationService refundService;
    private final CommandIdempotencyService idempotencyService;

    public TransactionController(
        TransactionService transactionService,
        RefundApplicationService refundService,
        CommandIdempotencyService idempotencyService
    ) {
        this.transactionService = transactionService;
        this.refundService = refundService;
        this.idempotencyService = idempotencyService;
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
        @RequestParam(defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(transactionService.list(
            principal(request), shopId, fromDate, toDate, status, keyword, page, pageSize
        ));
    }

    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> resources(
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(transactionService.resources(principal(request), shopId));
    }

    @GetMapping("/members/{memberId}/accounts")
    public ApiResponse<Map<String, Object>> memberAccounts(
        @PathVariable long memberId,
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            transactionService.memberAccounts(principal(request), shopId, memberId)
        );
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createOrder(
        @Valid @RequestBody OrderCreateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(transactionService.createOrder(principal(request), body));
    }

    @PostMapping("/{orderId}/payments")
    public ApiResponse<Map<String, Object>> pay(
        @PathVariable long orderId,
        @Valid @RequestBody PaymentRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(transactionService.pay(principal(request), orderId, body));
    }

    @PostMapping("/{orderId}/refunds")
    public ApiResponse<Map<String, Object>> createRefund(
        @PathVariable long orderId,
        @Valid @RequestBody RefundCreateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            refundService.request(
                principal(request),
                body.shopId(),
                orderId,
                body.paymentId(),
                body.amount(),
                body.reason(),
                body.idempotencyKey(),
                RequestHash.of(
                    body.paymentId(),
                    body.shopId(),
                    orderId,
                    body.amount(),
                    body.reason()
                )
            )
        );
    }

    @PostMapping("/refunds/{refundId}/decision")
    public ApiResponse<Map<String, Object>> decideRefund(
        @PathVariable long refundId,
        @Valid @RequestBody RefundDecisionRequest body,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            "v2-refund-decision-" + refundId + "-" + body.version() + "-" + body.action(),
            "REFUND_DECISION",
            RequestHash.of(
                refundId,
                body.shopId(),
                body.version(),
                body.action(),
                body.decisionNote()
            ),
            () -> result[0] = refundService.decide(
                principal,
                body.shopId(),
                refundId,
                body.version(),
                body.action(),
                body.decisionNote()
            )
        );
        return ApiResponse.ok(
            result[0] == null
                ? refundService.detail(principal, body.shopId(), refundId)
                : result[0]
        );
    }

    @PostMapping("/refunds/{refundId}/execute")
    public ApiResponse<Map<String, Object>> executeRefund(
        @PathVariable long refundId,
        @Valid @RequestBody RefundExecuteRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            refundService.execute(
                principal(request),
                body.shopId(),
                refundId,
                body.version(),
                body.idempotencyKey(),
                RequestHash.of(refundId, body.shopId(), body.version())
            )
        );
    }

    @PostMapping("/{orderId}/void")
    public ApiResponse<Map<String, Object>> voidOrder(
        @PathVariable long orderId,
        @Valid @RequestBody OrderVoidRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            transactionService.voidOrder(principal(request), orderId, body)
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
