package com.face.platform.commission;

import com.face.platform.api.ApiException;
import com.face.platform.idempotency.RequestHash;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CommissionEntryApplicationService {

    private static final Set<String> MANAGEMENT_ROLES = Set.of(
        "OWNER", "MANAGER", "FINANCE", "REGIONAL_MANAGER"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final CommissionSourceSnapshotService snapshotService;
    private final OutboxEventService outboxEventService;

    public CommissionEntryApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        CommissionSourceSnapshotService snapshotService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.snapshotService = snapshotService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public String accrueForOrderEvent(
        TenantPrincipal principal,
        long orderId,
        long outboxEventId
    ) {
        Map<String, Object> source = orderSource(principal, orderId);
        String orderStatus = text(source.get("orderStatus"));
        if (!List.of("PAID", "PARTIALLY_REFUNDED", "REFUNDED").contains(orderStatus)) {
            return "WAITING_PAYMENT";
        }
        Long serviceRecordId = nullableNumber(source.get("serviceRecordId"));
        if (serviceRecordId != null && !"COMPLETED".equals(source.get("serviceStatus"))) {
            return "WAITING_SERVICE";
        }
        Long staffId = nullableNumber(source.get("staffId"));
        if (staffId == null) {
            return "NO_STAFF";
        }
        Instant occurredAt = sourceInstant(source);
        if (occurredAt == null) {
            return "WAITING_SOURCE_TIME";
        }
        String sourceType = serviceRecordId == null ? "SALE" : "SERVICE";
        long sourceId = serviceRecordId == null ? orderId : serviceRecordId;
        BigDecimal baseAmount = money(source.get("payableAmount"));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("orderId", orderId);
        facts.put("staffId", staffId);
        if (serviceRecordId != null) facts.put("serviceRecordId", serviceRecordId);

        Map<String, Object> snapshot = snapshotService.captureInternal(
            principal,
            new CommissionSourceSnapshotService.SnapshotCommand(
                number(source.get("shopId")),
                sourceType,
                sourceId,
                staffId,
                nullableNumber(source.get("memberId")),
                serviceRecordId == null
                    ? text(source.get("orderNo"))
                    : text(source.get("recordNo")),
                baseAmount,
                occurredAt,
                facts
            )
        );
        long snapshotId = number(snapshot.get("id"));
        Map<String, Object> rule = matchingRule(
            principal.tenantId(),
            number(source.get("shopId")),
            sourceType,
            occurredAt,
            staffId,
            orderId
        );
        if (rule == null) {
            return "NO_RULE";
        }
        long ruleId = number(rule.get("id"));
        String sourceKey = "ACCRUAL:" + snapshotId + ":" + ruleId;
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id FROM commission_entry
            WHERE tenant_id = ? AND source_entry_key = ?
            """,
            principal.tenantId(),
            sourceKey
        );
        if (!replay.isEmpty()) return "REPLAY";

        BigDecimal amount;
        try {
            amount = CommissionRulePolicy.calculate(
                baseAmount,
                money(rule.get("rateValue")),
                money(rule.get("fixedAmount")),
                nullableMoney(rule.get("floorAmount")),
                nullableMoney(rule.get("capAmount"))
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
        if (amount.signum() <= 0) return "ZERO_COMMISSION";

        long entryId = insertEntry(
            principal,
            number(source.get("shopId")),
            sourceKey,
            staffId,
            snapshotId,
            ruleId,
            null,
            null,
            "ACCRUAL",
            baseAmount,
            amount
        );
        String historyKey = "projection:accrual:" + outboxEventId + ":" + entryId;
        insertHistory(
            principal,
            number(source.get("shopId")),
            entryId,
            "ACCRUED",
            null,
            "PENDING",
            amount,
            null,
            historyKey,
            RequestHash.of(sourceKey, amount)
        );
        audit(principal, number(source.get("shopId")), "COMMISSION_ACCRUED", entryId);
        outboxEventService.append(
            principal,
            number(source.get("shopId")),
            "COMMISSION_ENTRY",
            Long.toString(entryId),
            "CommissionAccrued",
            Map.of("commissionEntryId", entryId)
        );
        return "ACCRUED";
    }

    @Transactional
    public String reverseForRefundEvent(
        TenantPrincipal principal,
        long refundId,
        long outboxEventId
    ) {
        Map<String, Object> refund = refundSource(principal, refundId);
        if (!"SUCCESS".equals(refund.get("status"))) return "WAITING_REFUND";
        long orderId = number(refund.get("orderId"));
        BigDecimal refundAmount = money(refund.get("amount"));
        BigDecimal paidAmount = money(refund.get("paidAmount"));
        List<Map<String, Object>> originals = originalEntries(
            principal.tenantId(),
            orderId
        );
        if (originals.isEmpty()) return "NO_ACCRUAL";

        int created = 0;
        for (Map<String, Object> original : originals) {
            long originalId = number(original.get("id"));
            String sourceKey = "REFUND:" + refundId + ":" + originalId;
            Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM commission_entry
                WHERE tenant_id = ? AND source_entry_key = ?
                """,
                Integer.class,
                principal.tenantId(),
                sourceKey
            );
            if (exists != null && exists > 0) continue;
            BigDecimal originalAmount = money(original.get("amount"));
            BigDecimal alreadyReversed = money(original.get("alreadyReversed"));
            BigDecimal reversal;
            try {
                reversal = CommissionEntryPolicy.reversalAmount(
                    originalAmount,
                    alreadyReversed,
                    refundAmount,
                    paidAmount
                );
            } catch (IllegalArgumentException exception) {
                if ("原提成已全部冲正".equals(exception.getMessage())) continue;
                throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
            }
            long entryId = insertEntry(
                principal,
                number(refund.get("shopId")),
                sourceKey,
                number(original.get("staffId")),
                number(original.get("sourceSnapshotId")),
                number(original.get("ruleVersionId")),
                originalId,
                refundId,
                "REVERSAL",
                refundAmount,
                reversal
            );
            insertHistory(
                principal,
                number(refund.get("shopId")),
                entryId,
                "REVERSED",
                null,
                "PENDING",
                reversal,
                null,
                "projection:refund:" + outboxEventId + ":" + originalId,
                RequestHash.of(sourceKey, reversal)
            );
            audit(principal, number(refund.get("shopId")), "COMMISSION_REVERSED", entryId);
            outboxEventService.append(
                principal,
                number(refund.get("shopId")),
                "COMMISSION_ENTRY",
                Long.toString(entryId),
                "CommissionReversed",
                Map.of(
                    "commissionEntryId", entryId,
                    "originalCommissionEntryId", originalId,
                    "refundId", refundId
                )
            );
            created++;
        }
        return created == 0 ? "REPLAY" : "REVERSED_" + created;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        long shopId,
        Long staffId,
        String status,
        int page,
        int pageSize
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:entry:view");
        Long effectiveStaffId = staffId;
        if (selfOnly(principal)) {
            long ownStaffId = accessService.requireStaffId(principal);
            if (staffId != null && staffId != ownStaffId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "提成流水不存在");
            }
            effectiveStaffId = ownStaffId;
        }
        String normalizedStatus = optionalStatus(status);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        StringBuilder filters = new StringBuilder();
        if (effectiveStaffId != null) {
            filters.append(" AND ce.staff_id = ?");
            args.add(effectiveStaffId);
        }
        if (normalizedStatus != null) {
            filters.append(" AND ce.status = ?");
            args.add(normalizedStatus);
        }
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT ce.id, ce.entry_no AS entryNo, ce.staff_id AS staffId,
                   st.name AS staffName, ce.entry_type AS entryType,
                   ce.base_amount AS baseAmount, ce.amount, ce.status, ce.version,
                   css.source_type AS sourceType, css.business_no AS businessNo,
                   crv.rule_code AS ruleCode, crv.version_no AS ruleVersionNo,
                   ce.original_entry_id AS originalEntryId, ce.refund_id AS refundId,
                   ce.created_at AS createdAt
            FROM commission_entry ce
            JOIN staff st ON st.id = ce.staff_id
            JOIN commission_source_snapshot css ON css.id = ce.source_snapshot_id
            JOIN commission_rule_version crv ON crv.id = ce.rule_version_id
            WHERE ce.tenant_id = ? AND ce.shop_id = ?
            %s
            ORDER BY ce.created_at DESC, ce.id DESC
            LIMIT ? OFFSET ?
            """.formatted(filters),
            listArgs.toArray()
        );
        records.forEach(this::moneyFields);
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM commission_entry ce
            WHERE ce.tenant_id = ? AND ce.shop_id = ?
            %s
            """.formatted(filters),
            Long.class,
            args.toArray()
        );
        return Map.of(
            "records", records,
            "total", total == null ? 0 : total,
            "page", safePage,
            "pageSize", safeSize
        );
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long entryId
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:entry:view");
        Map<String, Object> result = entry(principal.tenantId(), shopId, entryId);
        if (selfOnly(principal)
            && number(result.get("staffId")) != accessService.requireStaffId(principal)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "提成流水不存在");
        }
        result.put(
            "history",
            jdbcTemplate.queryForList(
                """
                SELECT id, action, from_status AS fromStatus, to_status AS toStatus,
                       amount_delta AS amountDelta, reason, created_at AS createdAt
                FROM commission_entry_history
                WHERE tenant_id = ? AND entry_id = ?
                ORDER BY created_at, id
                """,
                principal.tenantId(),
                entryId
            )
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
            (List<Map<String, Object>>) result.get("history");
        history.forEach(row -> row.put("amountDelta", moneyText(row.get("amountDelta"))));
        if ("ACCRUAL".equals(result.get("entryType"))) {
            BigDecimal reproduced = CommissionRulePolicy.calculate(
                money(result.get("baseAmount")),
                money(result.get("rateValue")),
                money(result.get("fixedAmount")),
                nullableMoney(result.get("floorAmount")),
                nullableMoney(result.get("capAmount"))
            );
            result.put("recalculatedAmount", reproduced.toPlainString());
            result.put(
                "reproducible",
                reproduced.compareTo(money(result.get("amount"))) == 0
            );
        }
        moneyFields(result);
        result.remove("rateValue");
        result.remove("fixedAmount");
        result.remove("floorAmount");
        result.remove("capAmount");
        return result;
    }

    @Transactional
    public Map<String, Object> freeze(
        TenantPrincipal principal,
        long shopId,
        long entryId,
        int version,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        return changeStatus(
            principal, shopId, entryId, version, reason,
            idempotencyKey, requestHash, "FREEZE"
        );
    }

    @Transactional
    public Map<String, Object> unfreeze(
        TenantPrincipal principal,
        long shopId,
        long entryId,
        int version,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        return changeStatus(
            principal, shopId, entryId, version, reason,
            idempotencyKey, requestHash, "UNFREEZE"
        );
    }

    private Map<String, Object> changeStatus(
        TenantPrincipal principal,
        long shopId,
        long entryId,
        int version,
        String reason,
        String idempotencyKey,
        String requestHash,
        String action
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:freeze");
        String safeReason = required(reason, "操作原因", 500);
        Map<String, Object> current = lockEntry(principal.tenantId(), shopId, entryId);
        if (number(current.get("version")) != version) {
            throw new ApiException(HttpStatus.CONFLICT, "提成流水版本已变化，请刷新后重试");
        }
        String fromStatus = text(current.get("status"));
        String toStatus;
        try {
            toStatus = CommissionEntryPolicy.transition(fromStatus, action);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
        boolean freezing = "FREEZE".equals(action);
        int changed = jdbcTemplate.update(
            freezing
                ? """
                  UPDATE commission_entry
                  SET status = 'FROZEN', freeze_reason = ?, frozen_by = ?,
                      frozen_at = CURRENT_TIMESTAMP(3), version = version + 1
                  WHERE id = ? AND tenant_id = ? AND shop_id = ?
                    AND version = ? AND status = 'PENDING'
                  """
                : """
                  UPDATE commission_entry
                  SET status = 'PENDING', freeze_reason = NULL, unfrozen_by = ?,
                      unfrozen_at = CURRENT_TIMESTAMP(3), version = version + 1
                  WHERE id = ? AND tenant_id = ? AND shop_id = ?
                    AND version = ? AND status = 'FROZEN'
                  """,
            freezing
                ? new Object[] {
                    safeReason, principal.accountId(), entryId,
                    principal.tenantId(), shopId, version
                }
                : new Object[] {
                    principal.accountId(), entryId,
                    principal.tenantId(), shopId, version
                }
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "提成流水状态已变化，请刷新后重试");
        }
        insertHistory(
            principal,
            shopId,
            entryId,
            freezing ? "FROZEN" : "UNFROZEN",
            fromStatus,
            toStatus,
            BigDecimal.ZERO,
            safeReason,
            required(idempotencyKey, "幂等键", 100),
            required(requestHash, "请求摘要", 64)
        );
        audit(
            principal,
            shopId,
            freezing ? "COMMISSION_FROZEN" : "COMMISSION_UNFROZEN",
            entryId
        );
        return detail(principal, shopId, entryId);
    }

    private Map<String, Object> matchingRule(
        long tenantId,
        long shopId,
        String sourceType,
        Instant occurredAt,
        long staffId,
        long orderId
    ) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
            """
            SELECT id, rule_code AS ruleCode, version_no AS versionNo,
                   rate_value AS rateValue, fixed_amount AS fixedAmount,
                   floor_amount AS floorAmount, cap_amount AS capAmount,
                   priority
            FROM commission_rule_version
            WHERE tenant_id = ? AND shop_id = ? AND source_type = ?
              AND status = 'PUBLISHED'
              AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to > ?)
            ORDER BY priority DESC, version_no DESC, id DESC
            """,
            tenantId,
            shopId,
            sourceType,
            occurredAt,
            occurredAt
        );
        if (candidates.isEmpty()) return null;
        MatchFacts facts = matchFacts(tenantId, shopId, staffId, orderId);
        for (Map<String, Object> candidate : candidates) {
            if (matchesRuleScopes(tenantId, number(candidate.get("id")), facts)) {
                return candidate;
            }
        }
        return null;
    }

    private MatchFacts matchFacts(long tenantId, long shopId, long staffId, long orderId) {
        Set<String> roles = new HashSet<>(jdbcTemplate.queryForList(
            """
            SELECT DISTINCT r.role_code
            FROM account a
            JOIN account_shop_role ar
              ON ar.account_id = a.id AND ar.tenant_id = a.tenant_id
            JOIN role_definition r ON r.id = ar.role_id
            WHERE a.tenant_id = ? AND a.staff_id = ? AND ar.shop_id = ?
              AND ar.status = 'ACTIVE' AND r.status = 'ACTIVE'
            """,
            String.class,
            tenantId,
            staffId,
            shopId
        ));
        Set<String> services = new HashSet<>();
        Set<String> products = new HashSet<>();
        Set<String> packages = new HashSet<>();
        for (Map<String, Object> item : jdbcTemplate.queryForList(
            """
            SELECT service_id AS serviceId, product_id AS productId,
                   package_product_id AS packageId
            FROM sales_order_item WHERE order_id = ?
            """,
            orderId
        )) {
            addIdentifier(services, item.get("serviceId"));
            addIdentifier(products, item.get("productId"));
            addIdentifier(packages, item.get("packageId"));
        }
        return new MatchFacts(
            Long.toString(shopId),
            Long.toString(staffId),
            roles,
            services,
            products,
            packages
        );
    }

    private boolean matchesRuleScopes(long tenantId, long ruleId, MatchFacts facts) {
        Map<String, Set<String>> scopes = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            """
            SELECT scope_type AS scopeType, scope_key AS scopeKey
            FROM commission_rule_scope
            WHERE tenant_id = ? AND rule_version_id = ?
            """,
            tenantId,
            ruleId
        )) {
            scopes.computeIfAbsent(text(row.get("scopeType")), ignored -> new HashSet<>())
                .add(text(row.get("scopeKey")));
        }
        for (Map.Entry<String, Set<String>> scope : scopes.entrySet()) {
            Set<String> actual = switch (scope.getKey()) {
                case "SHOP" -> Set.of(facts.shopId());
                case "ROLE" -> facts.roles();
                case "STAFF" -> Set.of(facts.staffId());
                case "SERVICE" -> facts.services();
                case "PRODUCT" -> facts.products();
                case "PACKAGE" -> facts.packages();
                default -> Set.of();
            };
            if (scope.getValue().stream().noneMatch(actual::contains)) return false;
        }
        return true;
    }

    private long insertEntry(
        TenantPrincipal principal,
        long shopId,
        String sourceKey,
        long staffId,
        long snapshotId,
        long ruleId,
        Long originalEntryId,
        Long refundId,
        String entryType,
        BigDecimal baseAmount,
        BigDecimal amount
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO commission_entry (
                        tenant_id, shop_id, entry_no, source_entry_key, staff_id,
                        source_snapshot_id, rule_version_id, original_entry_id,
                        refund_id, entry_type, base_amount, amount, status, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, shopId);
                statement.setString(3, businessNo("COM"));
                statement.setString(4, sourceKey);
                statement.setLong(5, staffId);
                statement.setLong(6, snapshotId);
                statement.setLong(7, ruleId);
                statement.setObject(8, originalEntryId);
                statement.setObject(9, refundId);
                statement.setString(10, entryType);
                statement.setBigDecimal(11, baseAmount);
                statement.setBigDecimal(12, amount);
                statement.setLong(13, principal.accountId());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "提成流水正在被其他事件创建");
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "提成流水创建失败");
        }
        return key.longValue();
    }

    private void insertHistory(
        TenantPrincipal principal,
        long shopId,
        long entryId,
        String action,
        String fromStatus,
        String toStatus,
        BigDecimal amountDelta,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO commission_entry_history (
                tenant_id, shop_id, entry_id, action, from_status, to_status,
                amount_delta, reason, idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            entryId,
            action,
            fromStatus,
            toStatus,
            amountDelta,
            reason,
            idempotencyKey,
            requestHash,
            principal.accountId()
        );
    }

    private Map<String, Object> orderSource(TenantPrincipal principal, long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT so.id, so.shop_id AS shopId, so.member_id AS memberId,
                   so.order_no AS orderNo, so.payable_amount AS payableAmount,
                   so.status AS orderStatus, so.paid_at AS paidAt,
                   so.service_record_id AS serviceRecordId,
                   sr.staff_id AS serviceStaffId, sr.record_no AS recordNo,
                   sr.status AS serviceStatus, sr.actual_end_at AS serviceCompletedAt,
                   creator.staff_id AS saleStaffId
            FROM sales_order so
            LEFT JOIN service_record sr
              ON sr.id = so.service_record_id AND sr.tenant_id = so.tenant_id
            LEFT JOIN account creator
              ON creator.id = so.created_by AND creator.tenant_id = so.tenant_id
            WHERE so.id = ? AND so.tenant_id = ?
            FOR UPDATE
            """,
            orderId,
            principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成来源订单不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put(
            "staffId",
            result.get("serviceStaffId") == null
                ? result.get("saleStaffId")
                : result.get("serviceStaffId")
        );
        return result;
    }

    private Map<String, Object> refundSource(TenantPrincipal principal, long refundId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT rt.id, rt.shop_id AS shopId, rt.order_id AS orderId,
                   rt.amount, rt.status, so.paid_amount AS paidAmount
            FROM refund_transaction rt
            JOIN sales_order so
              ON so.id = rt.order_id AND so.tenant_id = rt.tenant_id
            WHERE rt.id = ? AND rt.tenant_id = ?
            FOR UPDATE
            """,
            refundId,
            principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "退款提成来源不存在");
        return rows.getFirst();
    }

    private List<Map<String, Object>> originalEntries(long tenantId, long orderId) {
        return jdbcTemplate.queryForList(
            """
            SELECT ce.id, ce.staff_id AS staffId,
                   ce.source_snapshot_id AS sourceSnapshotId,
                   ce.rule_version_id AS ruleVersionId, ce.amount,
                   COALESCE((
                     SELECT SUM(ABS(rev.amount))
                     FROM commission_entry rev
                     WHERE rev.tenant_id = ce.tenant_id
                       AND rev.original_entry_id = ce.id
                       AND rev.entry_type = 'REVERSAL'
                   ), 0) AS alreadyReversed
            FROM commission_entry ce
            JOIN commission_source_snapshot css ON css.id = ce.source_snapshot_id
            WHERE ce.tenant_id = ? AND ce.entry_type = 'ACCRUAL'
              AND (
                (css.source_type = 'SALE' AND css.source_id = ?)
                OR (
                  css.source_type = 'SERVICE'
                  AND EXISTS (
                    SELECT 1 FROM sales_order so
                    WHERE so.id = ? AND so.service_record_id = css.source_id
                      AND so.tenant_id = ce.tenant_id
                  )
                )
              )
            ORDER BY ce.id
            FOR UPDATE
            """,
            tenantId,
            orderId,
            orderId
        );
    }

    private Map<String, Object> lockEntry(long tenantId, long shopId, long entryId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, status, version FROM commission_entry
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            entryId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成流水不存在");
        return rows.getFirst();
    }

    private Map<String, Object> entry(long tenantId, long shopId, long entryId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT ce.id, ce.entry_no AS entryNo, ce.staff_id AS staffId,
                   st.name AS staffName, ce.entry_type AS entryType,
                   ce.base_amount AS baseAmount, ce.amount, ce.status,
                   ce.freeze_reason AS freezeReason, ce.version,
                   ce.original_entry_id AS originalEntryId, ce.refund_id AS refundId,
                   ce.created_at AS createdAt,
                   css.id AS sourceSnapshotId, css.source_type AS sourceType,
                   css.source_id AS sourceId, css.business_no AS businessNo,
                   css.snapshot_hash AS sourceSnapshotHash,
                   crv.id AS ruleVersionId, crv.rule_code AS ruleCode,
                   crv.version_no AS ruleVersionNo, crv.rate_value AS rateValue,
                   crv.fixed_amount AS fixedAmount, crv.floor_amount AS floorAmount,
                   crv.cap_amount AS capAmount
            FROM commission_entry ce
            JOIN staff st ON st.id = ce.staff_id
            JOIN commission_source_snapshot css ON css.id = ce.source_snapshot_id
            JOIN commission_rule_version crv ON crv.id = ce.rule_version_id
            WHERE ce.id = ? AND ce.tenant_id = ? AND ce.shop_id = ?
            """,
            entryId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成流水不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        long entryId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'COMMISSION_ENTRY', ?)
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entryId
        );
    }

    private boolean selfOnly(TenantPrincipal principal) {
        return principal.roles().contains("BEAUTICIAN")
            && principal.roles().stream().noneMatch(MANAGEMENT_ROLES::contains);
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "FROZEN", "SETTLED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "提成流水状态不正确");
        }
        return normalized;
    }

    private Instant sourceInstant(Map<String, Object> source) {
        Object value = source.get("serviceRecordId") == null
            ? source.get("paidAt")
            : source.get("serviceCompletedAt");
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        return Instant.parse(value.toString());
    }

    private void addIdentifier(Set<String> target, Object value) {
        if (value != null) target.add(Long.toString(number(value)));
    }

    private void moneyFields(Map<String, Object> row) {
        if (row.containsKey("baseAmount")) {
            row.put("baseAmount", moneyText(row.get("baseAmount")));
        }
        if (row.containsKey("amount")) {
            row.put("amount", moneyText(row.get("amount")));
        }
    }

    private BigDecimal money(Object value) {
        return value instanceof BigDecimal decimal
            ? decimal
            : new BigDecimal(value.toString());
    }

    private BigDecimal nullableMoney(Object value) {
        return value == null ? null : money(value);
    }

    private String moneyText(Object value) {
        return money(value).toPlainString();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String required(String value, String label, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空或过长");
        }
        return safe;
    }

    private String businessNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString()
            .replace("-", "")
            .substring(0, 24)
            .toUpperCase(Locale.ROOT);
    }

    private record MatchFacts(
        String shopId,
        String staffId,
        Set<String> roles,
        Set<String> services,
        Set<String> products,
        Set<String> packages
    ) {
    }
}
