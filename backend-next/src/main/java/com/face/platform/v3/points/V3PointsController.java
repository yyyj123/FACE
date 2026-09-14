package com.face.platform.v3.points;

import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import com.face.platform.v3.auth.SessionTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v3")
public class V3PointsController {

    private final PointsApplicationService service;

    public V3PointsController(PointsApplicationService service) {
        this.service = service;
    }

    @GetMapping("/client/points")
    public V3ApiResponse<Map<String, Object>> mine(HttpServletRequest request) {
        return success(service.mine(principal(request)), request);
    }

    @PostMapping("/client/points/check-in")
    public V3ApiResponse<Map<String, Object>> checkin(
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        return success(
            service.checkin(principal(request), key, SessionTokenCodec.sha256("CHECKIN:" + key)),
            request
        );
    }

    @GetMapping("/admin/points")
    public V3ApiResponse<Map<String, Object>> administration(
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(service.administration(principal(request), shopId), request);
    }

    @PostMapping("/admin/points/grants")
    public V3ApiResponse<Map<String, Object>> grant(
        @Valid @RequestBody GrantBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredKey(idempotencyKey);
        PointsApplicationService.GrantCommand command = new PointsApplicationService.GrantCommand(
            body.shop_id(), body.member_id(), body.points(), body.source_type(),
            body.reference_type(), body.reference_id(), body.reason(), body.validity_months()
        );
        return success(
            service.grant(principal(request), command, key, SessionTokenCodec.sha256(command.toString())),
            request
        );
    }

    @PutMapping("/admin/points/rules/{ruleId}")
    public V3ApiResponse<Map<String, Object>> updateRule(
        @PathVariable long ruleId,
        @Valid @RequestBody RuleBody body,
        HttpServletRequest request
    ) {
        return success(service.updateRule(
            principal(request), body.shop_id(), ruleId,
            new PointsApplicationService.RuleUpdateCommand(
                body.points_per_yuan(), body.points_per_currency(), body.minimum_points(),
                body.step_points(), body.max_discount_ratio(), body.max_discount_amount(),
                body.single_cap_points(), body.member_period_cap_points(),
                body.validity_months(), body.status(), body.version()
            )
        ), request);
    }

    @PutMapping("/admin/points/tasks/{taskId}")
    public V3ApiResponse<Map<String, Object>> updateTask(
        @PathVariable long taskId,
        @Valid @RequestBody TaskBody body,
        HttpServletRequest request
    ) {
        return success(service.updateTask(
            principal(request), body.shop_id(), taskId,
            new PointsApplicationService.TaskUpdateCommand(
                body.reward_points(), body.cycle_days(), body.daily_rewards(),
                body.cycle_bonus_points(), body.repeat_cycle(), body.member_period_cap_points(),
                body.status(), body.version()
            )
        ), request);
    }

    @PostMapping("/admin/points/expiry-reminders")
    public V3ApiResponse<Map<String, Object>> remind(
        @Valid @RequestBody ShopBody body,
        HttpServletRequest request
    ) {
        int created = service.sendExpiryReminders(principal(request), body.shop_id());
        return success(Map.of("created", created), request);
    }

    @PostMapping("/admin/points/references/{referenceType}/{referenceId}/restore")
    public V3ApiResponse<Map<String, Object>> restore(
        @PathVariable String referenceType,
        @PathVariable long referenceId,
        @Valid @RequestBody RestoreBody body,
        HttpServletRequest request
    ) {
        int restored = service.restoreReference(
            principal(request), body.shop_id(), referenceType, referenceId, body.reason()
        );
        return success(Map.of("restored", restored), request);
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 80) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED",
                "关键写操作必须提供有效的 Idempotency-Key"
            );
        }
        return value.trim();
    }

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record GrantBody(
        @Positive long shop_id,
        @Positive long member_id,
        @Positive long points,
        @NotBlank @Size(max = 30) String source_type,
        @NotBlank @Size(max = 40) String reference_type,
        Long reference_id,
        @NotBlank @Size(max = 255) String reason,
        @Min(1) @Max(60) Integer validity_months
    ) {
    }

    public record ShopBody(@Positive long shop_id) {
    }

    public record RestoreBody(
        @Positive long shop_id,
        @NotBlank @Size(max = 255) String reason
    ) {
    }

    public record RuleBody(
        @Positive long shop_id,
        @Positive BigDecimal points_per_yuan,
        @Positive int points_per_currency,
        @Min(0) int minimum_points,
        @Positive int step_points,
        @Min(0) @Max(100) BigDecimal max_discount_ratio,
        BigDecimal max_discount_amount,
        Integer single_cap_points,
        Integer member_period_cap_points,
        @Min(1) @Max(60) int validity_months,
        @NotBlank String status,
        @Min(1) int version
    ) {
    }

    public record TaskBody(
        @Positive long shop_id,
        @Min(0) int reward_points,
        @Min(1) @Max(365) int cycle_days,
        List<Integer> daily_rewards,
        @Min(0) int cycle_bonus_points,
        boolean repeat_cycle,
        Integer member_period_cap_points,
        @NotBlank String status,
        @Min(1) int version
    ) {
    }
}
