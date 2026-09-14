package com.face.platform.v3.sc6;

import com.face.platform.sc6.Sc6AfterSaleApplicationService;
import com.face.platform.sc6.Sc6ReviewApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3Sc6Controller {

    private final Sc6ReviewApplicationService reviewService;
    private final Sc6AfterSaleApplicationService afterSaleService;

    public V3Sc6Controller(
        Sc6ReviewApplicationService reviewService,
        Sc6AfterSaleApplicationService afterSaleService
    ) {
        this.reviewService = reviewService;
        this.afterSaleService = afterSaleService;
    }

    @GetMapping("/reviews/mine")
    public V3ApiResponse<List<Map<String, Object>>> myReviews(HttpServletRequest request) {
        return success(reviewService.listMine(principal(request)), request);
    }

    @PostMapping("/reviews")
    public V3ApiResponse<Map<String, Object>> createReview(
        @Valid @RequestBody ReviewBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(reviewService.save(
            principal(request), body.service_record_id(), null,
            body.staff_rating(), body.effect_rating(), body.environment_rating(),
            body.visibility(), body.content(), body.wants_contact(), idempotencyKey
        ), request);
    }

    @PutMapping("/reviews/{reviewId}")
    public V3ApiResponse<Map<String, Object>> updateReview(
        @PathVariable long reviewId,
        @Valid @RequestBody ReviewBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(reviewService.save(
            principal(request), body.service_record_id(), body.version(),
            body.staff_rating(), body.effect_rating(), body.environment_rating(),
            body.visibility(), body.content(), body.wants_contact(), idempotencyKey
        ), request);
    }

    @DeleteMapping("/reviews/{reviewId}")
    public V3ApiResponse<Map<String, Object>> deleteReview(
        @PathVariable long reviewId,
        @RequestParam int version,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(reviewService.delete(
            principal(request), reviewId, version, idempotencyKey
        ), request);
    }

    @GetMapping("/admin/reviews")
    public V3ApiResponse<List<Map<String, Object>>> moderationQueue(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return success(reviewService.moderationQueue(principal(request), shopId, status), request);
    }

    @PostMapping("/admin/reviews/{reviewId}/moderation")
    public V3ApiResponse<Map<String, Object>> moderateReview(
        @PathVariable long reviewId,
        @Valid @RequestBody ModerationBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(reviewService.moderate(
            principal(request), body.shop_id(), reviewId, body.version(),
            body.action(), body.note(), idempotencyKey
        ), request);
    }

    @PostMapping("/after-sales/cases/{caseId}/solution")
    public V3ApiResponse<Map<String, Object>> decideAfterSale(
        @PathVariable long caseId,
        @Valid @RequestBody SolutionBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(afterSaleService.decide(
            principal(request), body.shop_id(), caseId, body.version(),
            body.resolution_type(), body.risk_amount(), body.note(),
            body.evidence(), idempotencyKey
        ), request);
    }

    @PostMapping("/after-sales/cases/{caseId}/customer-response")
    public V3ApiResponse<Map<String, Object>> customerResponse(
        @PathVariable long caseId,
        @Valid @RequestBody CustomerResponseBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(afterSaleService.customerResponse(
            principal(request), body.shop_id(), caseId, body.version(),
            body.accepted(), body.reason(), idempotencyKey
        ), request);
    }

    @GetMapping("/mall/returns")
    public V3ApiResponse<List<Map<String, Object>>> returns(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return success(afterSaleService.returns(principal(request), shopId, status), request);
    }

    @PostMapping("/mall/returns")
    public V3ApiResponse<Map<String, Object>> createReturn(
        @Valid @RequestBody ReturnBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(afterSaleService.createReturn(
            principal(request), body.shop_id(), body.mall_order_id(),
            body.reason_code(), body.reason_detail(),
            body.items().stream().map(item -> new Sc6AfterSaleApplicationService.ReturnLine(
                item.order_item_id(), item.quantity()
            )).toList(), idempotencyKey
        ), request);
    }

    @PostMapping("/mall/returns/{returnId}/review")
    public V3ApiResponse<Map<String, Object>> reviewReturn(
        @PathVariable long returnId,
        @Valid @RequestBody ReturnReviewBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(afterSaleService.reviewReturn(
            principal(request), body.shop_id(), returnId, body.version(),
            body.approved(), body.reason(), idempotencyKey
        ), request);
    }

    @PostMapping("/mall/returns/{returnId}/ship")
    public V3ApiResponse<Map<String, Object>> shipReturn(
        @PathVariable long returnId,
        @Valid @RequestBody ReturnShipBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(afterSaleService.shipReturn(
            principal(request), body.shop_id(), returnId, body.version(),
            body.tracking_no(), idempotencyKey
        ), request);
    }

    @PostMapping("/mall/returns/{returnId}/inspection")
    public V3ApiResponse<Map<String, Object>> inspectReturn(
        @PathVariable long returnId,
        @Valid @RequestBody InspectionBody body,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return success(afterSaleService.inspectReturn(
            principal(request), body.shop_id(), returnId, body.version(),
            body.passed(), body.stock_disposition(), body.reason(),
            body.evidence(), idempotencyKey
        ), request);
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record ReviewBody(
        @NotNull Long service_record_id,
        Integer version,
        @Min(1) @Max(5) int staff_rating,
        @Min(1) @Max(5) int effect_rating,
        @Min(1) @Max(5) int environment_rating,
        @NotBlank @Size(max = 20) String visibility,
        @Size(max = 1000) String content,
        boolean wants_contact
    ) {}

    public record ModerationBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 20) String action,
        @Size(max = 500) String note
    ) {}

    public record SolutionBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 30) String resolution_type,
        BigDecimal risk_amount,
        @NotBlank @Size(max = 500) String note,
        Object evidence
    ) {}

    public record CustomerResponseBody(
        @NotNull Long shop_id,
        @NotNull Integer version,
        boolean accepted,
        @Size(max = 500) String reason
    ) {}

    public record ReturnBody(
        @NotNull Long shop_id,
        @NotNull Long mall_order_id,
        @NotBlank @Size(max = 30) String reason_code,
        @NotBlank @Size(max = 500) String reason_detail,
        @NotEmpty List<@Valid ReturnItemBody> items
    ) {}

    public record ReturnItemBody(@NotNull Long order_item_id, @Min(1) int quantity) {}

    public record ReturnReviewBody(
        @NotNull Long shop_id, @NotNull Integer version,
        boolean approved, @Size(max = 500) String reason
    ) {}

    public record ReturnShipBody(
        @NotNull Long shop_id, @NotNull Integer version,
        @NotBlank @Size(max = 80) String tracking_no
    ) {}

    public record InspectionBody(
        @NotNull Long shop_id, @NotNull Integer version,
        boolean passed, @Size(max = 20) String stock_disposition,
        @NotBlank @Size(max = 500) String reason, Object evidence
    ) {}
}
