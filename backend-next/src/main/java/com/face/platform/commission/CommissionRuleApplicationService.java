package com.face.platform.commission;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class CommissionRuleApplicationService {

    private static final Set<String> SOURCE_TYPES = Set.of("SERVICE", "SALE");
    private static final Set<String> CALCULATION_TYPES = Set.of(
        "PERCENTAGE",
        "FIXED",
        "PERCENTAGE_PLUS_FIXED"
    );
    private static final Set<String> SCOPE_TYPES = Set.of(
        "SHOP",
        "ROLE",
        "STAFF",
        "SERVICE",
        "PRODUCT",
        "PACKAGE"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;

    public CommissionRuleApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
    }

    public List<Map<String, Object>> list(
        TenantPrincipal principal,
        long shopId,
        String sourceType,
        String status
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:view");
        String safeSource = optionalEnum(sourceType, SOURCE_TYPES, "提成来源类型");
        String safeStatus = status == null || status.isBlank()
            ? null
            : ruleStatus(status);
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        StringBuilder sql = new StringBuilder(
            """
            SELECT id, shop_id AS shopId, rule_code AS ruleCode,
                   version_no AS versionNo, rule_name AS ruleName,
                   source_type AS sourceType, calculation_type AS calculationType,
                   rate_value AS rateValue, fixed_amount AS fixedAmount,
                   floor_amount AS floorAmount, cap_amount AS capAmount,
                   priority, effective_from AS effectiveFrom,
                   effective_to AS effectiveTo, status, version,
                   published_at AS publishedAt, retired_at AS retiredAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM commission_rule_version
            WHERE tenant_id = ? AND shop_id = ?
            """
        );
        if (safeSource != null) {
            sql.append(" AND source_type = ?");
            args.add(safeSource);
        }
        if (safeStatus != null) {
            sql.append(" AND status = ?");
            args.add(safeStatus);
        }
        sql.append(" ORDER BY rule_code, version_no DESC LIMIT 300");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray())
            .stream()
            .map(this::ruleResult)
            .toList();
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long ruleId
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:view");
        return requireRule(principal, shopId, ruleId, false);
    }

    public Map<String, Object> latestByCode(
        TenantPrincipal principal,
        long shopId,
        String ruleCode
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:view");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id
            FROM commission_rule_version
            WHERE tenant_id = ? AND shop_id = ? AND rule_code = ?
            ORDER BY version_no DESC
            LIMIT 1
            """,
            principal.tenantId(),
            shopId,
            requiredCode(ruleCode)
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成规则不存在");
        return requireRule(principal, shopId, number(rows.getFirst().get("id")), false);
    }

    @Transactional
    public Map<String, Object> createDraft(
        TenantPrincipal principal,
        long shopId,
        RuleDraft draft
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:manage");
        ValidatedRule validated = validate(draft, shopId);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT id, version_no AS versionNo, status
            FROM commission_rule_version
            WHERE tenant_id = ? AND shop_id = ? AND rule_code = ?
            ORDER BY version_no DESC
            FOR UPDATE
            """,
            principal.tenantId(),
            shopId,
            validated.ruleCode()
        );
        if (!existing.isEmpty() && "DRAFT".equals(existing.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "该规则已有未发布草稿");
        }
        int versionNo = existing.isEmpty()
            ? 1
            : integer(existing.getFirst().get("versionNo")) + 1;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO commission_rule_version (
                        tenant_id, shop_id, rule_code, version_no, rule_name,
                        source_type, calculation_type, rate_value, fixed_amount,
                        floor_amount, cap_amount, priority, effective_from,
                        effective_to, status, version, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 0, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, shopId);
                statement.setString(3, validated.ruleCode());
                statement.setInt(4, versionNo);
                statement.setString(5, validated.ruleName());
                statement.setString(6, validated.sourceType());
                statement.setString(7, validated.calculationType());
                statement.setBigDecimal(8, validated.rateValue());
                statement.setBigDecimal(9, validated.fixedAmount());
                statement.setBigDecimal(10, validated.floorAmount());
                statement.setBigDecimal(11, validated.capAmount());
                statement.setInt(12, validated.priority());
                statement.setObject(13, validated.effectiveFrom());
                statement.setObject(14, validated.effectiveTo());
                statement.setLong(15, principal.accountId());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "规则编号或版本已存在");
        }
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "提成规则创建失败");
        }
        long ruleId = generated.longValue();
        replaceDraftScopes(principal, ruleId, validated.scopes());
        audit(principal, shopId, "COMMISSION_RULE_CREATE", ruleId, "DRAFT", 0);
        return requireRule(principal, shopId, ruleId, false);
    }

    @Transactional
    public Map<String, Object> updateDraft(
        TenantPrincipal principal,
        long shopId,
        long ruleId,
        int version,
        RuleDraft draft
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:manage");
        Map<String, Object> current = requireRule(principal, shopId, ruleId, true);
        requireVersion(current, version);
        if (!CommissionRulePolicy.canEdit(current.get("status").toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "已发布或已退役规则不可修改");
        }
        ValidatedRule validated = validate(draft, shopId);
        if (!validated.ruleCode().equals(current.get("ruleCode"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "规则编号创建后不可修改");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE commission_rule_version
            SET rule_name = ?, source_type = ?, calculation_type = ?,
                rate_value = ?, fixed_amount = ?, floor_amount = ?, cap_amount = ?,
                priority = ?, effective_from = ?, effective_to = ?,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'DRAFT' AND version = ?
            """,
            validated.ruleName(),
            validated.sourceType(),
            validated.calculationType(),
            validated.rateValue(),
            validated.fixedAmount(),
            validated.floorAmount(),
            validated.capAmount(),
            validated.priority(),
            validated.effectiveFrom(),
            validated.effectiveTo(),
            ruleId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "提成规则版本已变化，请刷新后重试");
        replaceDraftScopes(principal, ruleId, validated.scopes());
        audit(principal, shopId, "COMMISSION_RULE_UPDATE", ruleId, "DRAFT", version + 1);
        return requireRule(principal, shopId, ruleId, false);
    }

    @Transactional
    public Map<String, Object> publish(
        TenantPrincipal principal,
        long shopId,
        long ruleId,
        int version
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:manage");
        Map<String, Object> current = requireRule(principal, shopId, ruleId, true);
        requireVersion(current, version);
        if (!CommissionRulePolicy.canTransition(current.get("status").toString(), "PUBLISHED")) {
            throw new ApiException(HttpStatus.CONFLICT, "只有草稿规则可以发布");
        }
        Integer conflicts = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT existing.id)
            FROM commission_rule_version existing
            JOIN commission_rule_scope existing_scope
              ON existing_scope.rule_version_id = existing.id
             AND existing_scope.tenant_id = existing.tenant_id
            JOIN commission_rule_scope current_scope
              ON current_scope.rule_version_id = ?
             AND current_scope.tenant_id = existing.tenant_id
             AND current_scope.scope_type = existing_scope.scope_type
             AND current_scope.scope_key = existing_scope.scope_key
            WHERE existing.tenant_id = ?
              AND existing.shop_id = ?
              AND existing.id <> ?
              AND existing.status = 'PUBLISHED'
              AND existing.source_type = ?
              AND existing.priority = ?
              AND existing.effective_from < COALESCE(?, TIMESTAMP('9999-12-31 23:59:59'))
              AND ? < COALESCE(existing.effective_to, TIMESTAMP('9999-12-31 23:59:59'))
            """,
            Integer.class,
            ruleId,
            principal.tenantId(),
            shopId,
            ruleId,
            current.get("sourceType"),
            integer(current.get("priority")),
            current.get("effectiveTo"),
            current.get("effectiveFrom")
        );
        if (conflicts != null && conflicts > 0) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "RULE_SCOPE_CONFLICT：同范围、同优先级的已发布规则生效区间重叠"
            );
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE commission_rule_version
            SET status = 'PUBLISHED', published_by = ?,
                published_at = CURRENT_TIMESTAMP(3), version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'DRAFT' AND version = ?
            """,
            principal.accountId(),
            ruleId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "提成规则发布状态已变化");
        audit(
            principal,
            shopId,
            "COMMISSION_RULE_PUBLISH",
            ruleId,
            "PUBLISHED",
            version + 1
        );
        outboxEventService.append(
            principal,
            shopId,
            "COMMISSION_RULE",
            Long.toString(ruleId),
            "CommissionRulePublished",
            Map.of("commissionRuleId", ruleId)
        );
        return requireRule(principal, shopId, ruleId, false);
    }

    @Transactional
    public Map<String, Object> retire(
        TenantPrincipal principal,
        long shopId,
        long ruleId,
        int version
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:manage");
        Map<String, Object> current = requireRule(principal, shopId, ruleId, true);
        requireVersion(current, version);
        if (!CommissionRulePolicy.canTransition(current.get("status").toString(), "RETIRED")) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已发布规则可以退役");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE commission_rule_version
            SET status = 'RETIRED', retired_by = ?,
                retired_at = CURRENT_TIMESTAMP(3), version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'PUBLISHED' AND version = ?
            """,
            principal.accountId(),
            ruleId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "提成规则退役状态已变化");
        audit(principal, shopId, "COMMISSION_RULE_RETIRE", ruleId, "RETIRED", version + 1);
        outboxEventService.append(
            principal,
            shopId,
            "COMMISSION_RULE",
            Long.toString(ruleId),
            "CommissionRuleRetired",
            Map.of("commissionRuleId", ruleId)
        );
        return requireRule(principal, shopId, ruleId, false);
    }

    public Map<String, Object> simulate(
        TenantPrincipal principal,
        long shopId,
        long ruleId,
        BigDecimal baseAmount
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:rule:view");
        Map<String, Object> rule = requireRule(principal, shopId, ruleId, false);
        BigDecimal amount;
        try {
            amount = CommissionRulePolicy.calculate(
                money(baseAmount),
                decimal(rule.get("rateValue")),
                decimal(rule.get("fixedAmount")),
                nullableDecimal(rule.get("floorAmount")),
                nullableDecimal(rule.get("capAmount"))
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("ruleCode", rule.get("ruleCode"));
        result.put("ruleVersion", rule.get("versionNo"));
        result.put("baseAmount", money(baseAmount).toPlainString());
        result.put("commissionAmount", amount.toPlainString());
        result.put("calculationType", rule.get("calculationType"));
        result.put("rateValue", moneyText(rule.get("rateValue")));
        result.put("fixedAmount", moneyText(rule.get("fixedAmount")));
        result.put("simulationOnly", true);
        return result;
    }

    private Map<String, Object> requireRule(
        TenantPrincipal principal,
        long shopId,
        long ruleId,
        boolean forUpdate
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, rule_code AS ruleCode,
                   version_no AS versionNo, rule_name AS ruleName,
                   source_type AS sourceType, calculation_type AS calculationType,
                   rate_value AS rateValue, fixed_amount AS fixedAmount,
                   floor_amount AS floorAmount, cap_amount AS capAmount,
                   priority, effective_from AS effectiveFrom,
                   effective_to AS effectiveTo, status, version,
                   published_at AS publishedAt, retired_at AS retiredAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM commission_rule_version
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            """.concat(forUpdate ? " FOR UPDATE" : ""),
            ruleId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成规则不存在");
        return ruleResult(rows.getFirst());
    }

    private Map<String, Object> ruleResult(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        long ruleId = number(source.get("id"));
        result.put(
            "scopes",
            jdbcTemplate.queryForList(
                """
                SELECT scope_type AS scopeType, scope_key AS scopeKey
                FROM commission_rule_scope
                WHERE rule_version_id = ?
                ORDER BY scope_type, scope_key
                """,
                ruleId
            )
        );
        for (String field : List.of("rateValue", "fixedAmount", "floorAmount", "capAmount")) {
            if (result.get(field) != null) result.put(field, moneyText(result.get(field)));
        }
        return result;
    }

    private ValidatedRule validate(RuleDraft draft, long shopId) {
        if (draft == null) throw new ApiException(HttpStatus.BAD_REQUEST, "规则内容不能为空");
        String ruleCode = requiredCode(draft.ruleCode());
        String ruleName = requiredText(draft.ruleName(), "规则名称", 120);
        String sourceType = requiredEnum(draft.sourceType(), SOURCE_TYPES, "提成来源类型");
        String calculationType = requiredEnum(
            draft.calculationType(),
            CALCULATION_TYPES,
            "提成计算方式"
        );
        BigDecimal rate = nonNegative(draft.rateValue(), "提成比例", 6);
        BigDecimal fixed = nonNegative(draft.fixedAmount(), "固定提成", 2);
        BigDecimal floor = optionalNonNegative(draft.floorAmount(), "最低提成");
        BigDecimal cap = optionalNonNegative(draft.capAmount(), "最高提成");
        if ("PERCENTAGE".equals(calculationType) && rate.signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "比例提成的比例必须大于零");
        }
        if ("FIXED".equals(calculationType) && fixed.signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "固定提成金额必须大于零");
        }
        if (floor != null && cap != null && cap.compareTo(floor) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "最高提成不能低于最低提成");
        }
        if (draft.effectiveFrom() == null
            || (draft.effectiveTo() != null
                && !draft.effectiveTo().isAfter(draft.effectiveFrom()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "规则生效时间范围不正确");
        }
        if (draft.priority() < -100000 || draft.priority() > 100000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "规则优先级超出允许范围");
        }
        TreeSet<RuleScope> scopes = new TreeSet<>(
            java.util.Comparator.comparing(RuleScope::scopeType)
                .thenComparing(RuleScope::scopeKey)
        );
        scopes.add(new RuleScope("SHOP", Long.toString(shopId)));
        if (draft.scopes() != null) {
            for (RuleScope scope : draft.scopes()) {
                if (scope == null) continue;
                String type = requiredEnum(scope.scopeType(), SCOPE_TYPES, "规则范围类型");
                String key = requiredText(scope.scopeKey(), "规则范围值", 100);
                if ("SHOP".equals(type) && !Long.toString(shopId).equals(key)) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "规则不能引用未授权门店范围");
                }
                scopes.add(new RuleScope(type, key));
            }
        }
        return new ValidatedRule(
            ruleCode,
            ruleName,
            sourceType,
            calculationType,
            rate,
            fixed,
            floor,
            cap,
            draft.priority(),
            draft.effectiveFrom(),
            draft.effectiveTo(),
            List.copyOf(scopes)
        );
    }

    private void replaceDraftScopes(
        TenantPrincipal principal,
        long ruleId,
        List<RuleScope> scopes
    ) {
        jdbcTemplate.update(
            "DELETE FROM commission_rule_scope WHERE tenant_id = ? AND rule_version_id = ?",
            principal.tenantId(),
            ruleId
        );
        for (RuleScope scope : scopes) {
            jdbcTemplate.update(
                """
                INSERT INTO commission_rule_scope (
                    tenant_id, rule_version_id, scope_type, scope_key
                ) VALUES (?, ?, ?, ?)
                """,
                principal.tenantId(),
                ruleId,
                scope.scopeType(),
                scope.scopeKey()
            );
        }
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        long ruleId,
        String status,
        int version
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id,
                after_data
            ) VALUES (?, ?, ?, ?, 'COMMISSION_RULE', ?,
                      JSON_OBJECT('status', ?, 'version', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            ruleId,
            status,
            version
        );
    }

    private void requireVersion(Map<String, Object> rule, int expected) {
        if (integer(rule.get("version")) != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "提成规则版本已变化，请刷新后重试");
        }
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private String ruleStatus(String value) {
        try {
            return CommissionRulePolicy.normalizeStatus(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private String requiredCode(String value) {
        String code = requiredText(value, "规则编号", 80).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,79}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "规则编号格式不正确");
        }
        return code;
    }

    private String requiredEnum(String value, Set<String> values, String label) {
        String normalized = requiredText(value, label, 40).toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
        }
        return normalized;
    }

    private String optionalEnum(String value, Set<String> values, String label) {
        if (value == null || value.isBlank()) return null;
        return requiredEnum(value, values, label);
    }

    private String requiredText(String value, String label, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空或过长");
        }
        return safe;
    }

    private BigDecimal money(BigDecimal value) {
        return nonNegative(value, "金额", 2);
    }

    private BigDecimal nonNegative(BigDecimal value, String label, int scale) {
        if (value == null || value.signum() < 0 || value.scale() > scale) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "格式不正确");
        }
        return value;
    }

    private BigDecimal optionalNonNegative(BigDecimal value, String label) {
        return value == null ? null : nonNegative(value, label, 2);
    }

    private String moneyText(Object value) {
        return decimal(value).toPlainString();
    }

    private BigDecimal nullableDecimal(Object value) {
        return value == null ? null : decimal(value);
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
            ? decimal
            : new BigDecimal(value.toString());
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    public record RuleScope(String scopeType, String scopeKey) {
    }

    public record RuleDraft(
        String ruleCode,
        String ruleName,
        String sourceType,
        String calculationType,
        BigDecimal rateValue,
        BigDecimal fixedAmount,
        BigDecimal floorAmount,
        BigDecimal capAmount,
        int priority,
        Instant effectiveFrom,
        Instant effectiveTo,
        List<RuleScope> scopes
    ) {
    }

    private record ValidatedRule(
        String ruleCode,
        String ruleName,
        String sourceType,
        String calculationType,
        BigDecimal rateValue,
        BigDecimal fixedAmount,
        BigDecimal floorAmount,
        BigDecimal capAmount,
        int priority,
        Instant effectiveFrom,
        Instant effectiveTo,
        List<RuleScope> scopes
    ) {
    }
}
