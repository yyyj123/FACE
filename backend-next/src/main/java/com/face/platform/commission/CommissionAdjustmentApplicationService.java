package com.face.platform.commission;

import com.face.platform.api.ApiException;
import com.face.platform.approval.ApprovalApplicationService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CommissionAdjustmentApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final ApprovalApplicationService approvalService;
    private final OutboxEventService outboxEventService;

    public CommissionAdjustmentApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        @Lazy ApprovalApplicationService approvalService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.approvalService = approvalService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Map<String, Object> request(
        TenantPrincipal principal,
        long shopId,
        long entryId,
        BigDecimal amount,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:entry:adjust");
        BigDecimal normalized;
        try {
            normalized = CommissionAdjustmentPolicy.amount(amount);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        String safeReason = required(reason, 500);
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, request_hash AS requestHash, approval_instance_id AS approvalId
            FROM commission_adjustment_request
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) {
            Map<String, Object> existing = replay.getFirst();
            if (!requestHash.equals(existing.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "提成调整幂等键已用于不同请求");
            }
            return result(principal, shopId, number(existing.get("id")));
        }
        Map<String, Object> original = requireOriginal(principal, shopId, entryId, true);
        if (!"ACCRUAL".equals(original.get("entryType"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只能对原始入账提成发起调整");
        }
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO commission_adjustment_request (
                    tenant_id, shop_id, original_entry_id, requested_amount,
                    reason, status, idempotency_key, request_hash, created_by
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, entryId);
            statement.setBigDecimal(4, normalized);
            statement.setString(5, safeReason);
            statement.setString(6, idempotencyKey);
            statement.setString(7, requestHash);
            statement.setLong(8, principal.accountId());
            return statement;
        }, holder);
        long requestId = holder.getKey().longValue();
        Map<String, Object> approval = approvalService.createInternal(
            principal, shopId, "COMMISSION_ADJUSTMENT", requestId,
            "COMMISSION_ADJUSTMENT", "提成调整申请 " + normalized.toPlainString(),
            "commission:entry:adjust", idempotencyKey + ":approval", requestHash
        );
        jdbcTemplate.update(
            """
            UPDATE commission_adjustment_request
            SET approval_instance_id = ?
            WHERE id = ? AND tenant_id = ? AND approval_instance_id IS NULL
            """,
            number(approval.get("id")), requestId, principal.tenantId()
        );
        audit(principal, shopId, requestId, "COMMISSION_ADJUSTMENT_REQUESTED");
        return result(principal, shopId, requestId);
    }

    @Transactional
    public void handleDecision(
        TenantPrincipal principal,
        long shopId,
        long requestId,
        long approvalId,
        String decision
    ) {
        Map<String, Object> request = lockRequest(principal, shopId, requestId);
        if (number(request.get("approvalId")) != approvalId) {
            throw new ApiException(HttpStatus.CONFLICT, "审批与提成调整申请不匹配");
        }
        if (!"PENDING".equals(request.get("status"))) {
            return;
        }
        if (!"APPROVED".equals(decision)) {
            String target = "CANCELLED".equals(decision) ? "CANCELLED" : "REJECTED";
            jdbcTemplate.update(
                """
                UPDATE commission_adjustment_request
                SET status = ?
                WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
                """,
                target, requestId, principal.tenantId()
            );
            return;
        }
        Map<String, Object> original = requireOriginal(
            principal, shopId, number(request.get("originalEntryId")), true
        );
        BigDecimal amount = decimal(request.get("requestedAmount"));
        String entryNo = "CA" + System.currentTimeMillis()
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6)
            .toUpperCase(Locale.ROOT);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO commission_entry (
                    tenant_id, shop_id, entry_no, source_entry_key, staff_id,
                    source_snapshot_id, rule_version_id, original_entry_id,
                    approval_instance_id, entry_type, base_amount, amount,
                    status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ADJUSTMENT', ?, ?, 'PENDING', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, entryNo);
            statement.setString(4, "ADJUSTMENT:APPROVAL:" + approvalId);
            statement.setLong(5, number(original.get("staffId")));
            statement.setLong(6, number(original.get("sourceSnapshotId")));
            statement.setLong(7, number(original.get("ruleVersionId")));
            statement.setLong(8, number(original.get("id")));
            statement.setLong(9, approvalId);
            statement.setBigDecimal(10, decimal(original.get("baseAmount")));
            statement.setBigDecimal(11, amount);
            statement.setLong(12, principal.accountId());
            return statement;
        }, holder);
        long entryId = holder.getKey().longValue();
        jdbcTemplate.update(
            """
            INSERT INTO commission_entry_history (
                tenant_id, shop_id, entry_id, action, from_status, to_status,
                amount_delta, reason, idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, 'ADJUSTED', NULL, 'PENDING', ?, ?,
                      ?, REPEAT('0', 64), ?)
            """,
            principal.tenantId(), shopId, entryId, amount, request.get("reason"),
            "approval-adjustment:" + approvalId, principal.accountId()
        );
        jdbcTemplate.update(
            """
            UPDATE commission_adjustment_request
            SET status = 'APPLIED', applied_entry_id = ?
            WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
            """,
            entryId, requestId, principal.tenantId()
        );
        audit(principal, shopId, entryId, "COMMISSION_ADJUSTMENT_APPLIED");
        outboxEventService.append(
            principal, shopId, "COMMISSION_ENTRY", Long.toString(entryId),
            "CommissionAdjustmentApplied",
            Map.of("commissionEntryId", entryId, "approvalId", approvalId)
        );
    }

    private Map<String, Object> result(
        TenantPrincipal principal, long shopId, long requestId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT car.id, car.original_entry_id AS originalEntryId,
                   car.requested_amount AS requestedAmount, car.reason,
                   car.status, car.approval_instance_id AS approvalId,
                   car.applied_entry_id AS appliedEntryId, car.created_at AS createdAt
            FROM commission_adjustment_request car
            WHERE car.id = ? AND car.tenant_id = ? AND car.shop_id = ?
            """,
            requestId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成调整申请不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("requestedAmount", decimal(result.get("requestedAmount")).toPlainString());
        return result;
    }

    private Map<String, Object> lockRequest(
        TenantPrincipal principal, long shopId, long requestId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, original_entry_id AS originalEntryId,
                   requested_amount AS requestedAmount, reason, status,
                   approval_instance_id AS approvalId
            FROM commission_adjustment_request
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            requestId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成调整申请不存在");
        return rows.getFirst();
    }

    private Map<String, Object> requireOriginal(
        TenantPrincipal principal, long shopId, long entryId, boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, staff_id AS staffId, source_snapshot_id AS sourceSnapshotId,
                   rule_version_id AS ruleVersionId, entry_type AS entryType,
                   base_amount AS baseAmount, amount, status
            FROM commission_entry
            WHERE id = ? AND tenant_id = ? AND shop_id = ?%s
            """.formatted(lock ? " FOR UPDATE" : ""),
            entryId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成流水不存在");
        return rows.getFirst();
    }

    private String required(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "调整原因不正确");
        }
        return value.trim();
    }

    private void audit(
        TenantPrincipal principal, long shopId, long entityId, String action
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'COMMISSION_ADJUSTMENT', ?)
            """,
            principal.tenantId(), shopId, principal.accountId(), action, entityId
        );
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
