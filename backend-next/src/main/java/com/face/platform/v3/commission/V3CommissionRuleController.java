package com.face.platform.v3.commission;

import com.face.platform.commission.CommissionRuleApplicationService;
import com.face.platform.commission.CommissionRuleApplicationService.RuleDraft;
import com.face.platform.commission.CommissionRuleApplicationService.RuleScope;
import com.face.platform.commission.CommissionSourceSnapshotService;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/commission")
public class V3CommissionRuleController {

    private final CommissionRuleApplicationService ruleService;
    private final CommissionSourceSnapshotService sourceSnapshotService;
    private final CommandIdempotencyService idempotencyService;

    public V3CommissionRuleController(
        CommissionRuleApplicationService ruleService,
        CommissionSourceSnapshotService sourceSnapshotService,
        CommandIdempotencyService idempotencyService
    ) {
        this.ruleService = ruleService;
        this.sourceSnapshotService = sourceSnapshotService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/rules")
    public V3ApiResponse<List<Map<String, Object>>> rules(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(name = "source_type", required = false) String sourceType,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return success(
            ruleService.list(principal(request), shopId, sourceType, status),
            request
        );
    }

    @GetMapping("/rules/{ruleId}")
    public V3ApiResponse<Map<String, Object>> rule(
        @PathVariable long ruleId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(ruleService.detail(principal(request), shopId, ruleId), request);
    }

    @PostMapping("/rules")
    public V3ApiResponse<Map<String, Object>> createRule(
        @Valid @RequestBody RuleDraftBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredIdempotencyKey(idempotencyKey);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            "COMMISSION_RULE_CREATE",
            RequestHash.of(
                body.shop_id(),
                body.rule_code(),
                body.rule_name(),
                body.source_type(),
                body.calculation_type(),
                body.rate_value(),
                body.fixed_amount(),
                body.floor_amount(),
                body.cap_amount(),
                body.priority(),
                body.effective_from(),
                body.effective_to(),
                body.scopes()
            ),
            () -> result[0] = ruleService.createDraft(
                principal,
                body.shop_id(),
                body.toDraft()
            )
        );
        return success(
            result[0] == null
                ? ruleService.latestByCode(principal, body.shop_id(), body.rule_code())
                : result[0],
            request
        );
    }

    @PatchMapping("/rules/{ruleId}")
    public V3ApiResponse<Map<String, Object>> updateRule(
        @PathVariable long ruleId,
        @Valid @RequestBody RuleDraftBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            requiredIdempotencyKey(idempotencyKey),
            "COMMISSION_RULE_UPDATE",
            RequestHash.of(ruleId, body.version(), body),
            () -> result[0] = ruleService.updateDraft(
                principal,
                body.shop_id(),
                ruleId,
                requiredVersion(body.version()),
                body.toDraft()
            )
        );
        return success(
            result[0] == null
                ? ruleService.detail(principal, body.shop_id(), ruleId)
                : result[0],
            request
        );
    }

    @PostMapping("/rules/{ruleId}/publish")
    public V3ApiResponse<Map<String, Object>> publishRule(
        @PathVariable long ruleId,
        @Valid @RequestBody RuleActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return runRuleAction(
            "COMMISSION_RULE_PUBLISH",
            ruleId,
            body,
            idempotencyKey,
            request,
            true
        );
    }

    @PostMapping("/rules/{ruleId}/retire")
    public V3ApiResponse<Map<String, Object>> retireRule(
        @PathVariable long ruleId,
        @Valid @RequestBody RuleActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return runRuleAction(
            "COMMISSION_RULE_RETIRE",
            ruleId,
            body,
            idempotencyKey,
            request,
            false
        );
    }

    @PostMapping("/simulations")
    public V3ApiResponse<Map<String, Object>> simulate(
        @Valid @RequestBody SimulationBody body,
        HttpServletRequest request
    ) {
        return success(
            ruleService.simulate(
                principal(request),
                body.shop_id(),
                body.rule_id(),
                body.base_amount()
            ),
            request
        );
    }

    @GetMapping("/source-snapshots/{snapshotId}")
    public V3ApiResponse<Map<String, Object>> sourceSnapshot(
        @PathVariable long snapshotId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            sourceSnapshotService.detail(principal(request), shopId, snapshotId),
            request
        );
    }

    private V3ApiResponse<Map<String, Object>> runRuleAction(
        String operation,
        long ruleId,
        RuleActionBody body,
        String idempotencyKey,
        HttpServletRequest request,
        boolean publish
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            requiredIdempotencyKey(idempotencyKey),
            operation,
            RequestHash.of(ruleId, body.shop_id(), body.version()),
            () -> result[0] = publish
                ? ruleService.publish(principal, body.shop_id(), ruleId, body.version())
                : ruleService.retire(principal, body.shop_id(), ruleId, body.version())
        );
        return success(
            result[0] == null
                ? ruleService.detail(principal, body.shop_id(), ruleId)
                : result[0],
            request
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private int requiredVersion(Integer version) {
        if (version == null || version < 0) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "version 参数不正确"
            );
        }
        return version;
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

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record ScopeBody(
        @NotBlank String scope_type,
        @NotBlank @Size(max = 100) String scope_key
    ) {
        RuleScope toScope() {
            return new RuleScope(scope_type, scope_key);
        }
    }

    public record RuleDraftBody(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 80) String rule_code,
        @NotBlank @Size(max = 120) String rule_name,
        @NotBlank String source_type,
        @NotBlank String calculation_type,
        @NotNull @DecimalMin("0.000000") @Digits(integer = 3, fraction = 6)
        BigDecimal rate_value,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
        BigDecimal fixed_amount,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
        BigDecimal floor_amount,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
        BigDecimal cap_amount,
        int priority,
        @NotNull Instant effective_from,
        Instant effective_to,
        @Valid List<ScopeBody> scopes,
        Integer version
    ) {
        RuleDraft toDraft() {
            return new RuleDraft(
                rule_code,
                rule_name,
                source_type,
                calculation_type,
                rate_value,
                fixed_amount,
                floor_amount,
                cap_amount,
                priority,
                effective_from,
                effective_to,
                scopes == null ? List.of() : scopes.stream().map(ScopeBody::toScope).toList()
            );
        }
    }

    public record RuleActionBody(
        @NotNull Long shop_id,
        @NotNull Integer version
    ) {
    }

    public record SimulationBody(
        @NotNull Long shop_id,
        @NotNull Long rule_id,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
        BigDecimal base_amount
    ) {
    }
}
