package com.face.platform.approval;

import com.face.platform.api.ApiException;
import com.face.platform.commission.CommissionAdjustmentApplicationService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ApprovalApplicationService {

    private static final Set<String> MANAGEMENT_ROLES = Set.of(
        "OWNER", "MANAGER", "REGIONAL_MANAGER"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;
    private final ObjectProvider<CommissionAdjustmentApplicationService> adjustmentService;

    public ApprovalApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService,
        ObjectProvider<CommissionAdjustmentApplicationService> adjustmentService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
        this.adjustmentService = adjustmentService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal, long shopId, String status, int page, int pageSize
    ) {
        accessService.requireShopPermission(principal, shopId, "approval:view");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String normalized = optionalStatus(status);
        StringBuilder sql = new StringBuilder(
            """
            SELECT ai.id, ai.approval_no AS approvalNo,
                   ai.business_type AS businessType, ai.business_id AS businessId,
                   ai.approval_type AS approvalType, ai.safe_summary AS safeSummary,
                   ai.requester_account_id AS requesterAccountId,
                   ai.status, ai.version, ai.decided_by AS decidedBy,
                   ai.decided_at AS decidedAt, ai.created_at AS createdAt
            FROM approval_instance ai
            WHERE ai.tenant_id = ? AND ai.shop_id = ?
            """
        );
        List<Object> args = new ArrayList<>(List.of(principal.tenantId(), shopId));
        if (!management(principal)) {
            sql.append(" AND ai.requester_account_id = ?");
            args.add(principal.accountId());
        }
        if (normalized != null) {
            sql.append(" AND ai.status = ?");
            args.add(normalized);
        }
        sql.append(" ORDER BY ai.id DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        return Map.of(
            "records", jdbcTemplate.queryForList(sql.toString(), args.toArray()),
            "page", safePage,
            "pageSize", safeSize
        );
    }

    public Map<String, Object> detail(
        TenantPrincipal principal, long shopId, long approvalId
    ) {
        accessService.requireShopPermission(principal, shopId, "approval:view");
        Map<String, Object> instance = requireInstance(principal, shopId, approvalId, false);
        if (!management(principal)
            && number(instance.get("requesterAccountId")) != principal.accountId()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "审批记录不存在");
        }
        List<Map<String, Object>> steps = jdbcTemplate.queryForList(
            """
            SELECT id, step_no AS stepNo, candidate_permission AS candidatePermission,
                   status, decision, decision_reason AS decisionReason,
                   decided_by AS decidedBy, decided_at AS decidedAt
            FROM approval_step
            WHERE tenant_id = ? AND shop_id = ? AND approval_instance_id = ?
            ORDER BY step_no
            """,
            principal.tenantId(), shopId, approvalId
        );
        Map<String, Object> result = new LinkedHashMap<>(instance);
        result.put("steps", steps);
        return result;
    }

    @Transactional
    public Map<String, Object> createInternal(
        TenantPrincipal principal,
        long shopId,
        String businessType,
        long businessId,
        String approvalType,
        String safeSummary,
        String candidatePermission,
        String idempotencyKey,
        String requestHash
    ) {
        String type = required(businessType, "业务类型", 40).toUpperCase(Locale.ROOT);
        String approval = required(approvalType, "审批类型", 40).toUpperCase(Locale.ROOT);
        String summary = required(safeSummary, "审批摘要", 500);
        String candidate = required(candidatePermission, "候选权限", 100);
        List<Long> replay = jdbcTemplate.queryForList(
            """
            SELECT id FROM approval_instance
            WHERE tenant_id = ? AND create_idempotency_key = ?
              AND create_request_hash = ?
            """,
            Long.class,
            principal.tenantId(), idempotencyKey, requestHash
        );
        if (!replay.isEmpty()) {
            return requireInstance(principal, shopId, replay.getFirst(), false);
        }
        String approvalNo = "AP" + System.currentTimeMillis()
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6)
            .toUpperCase(Locale.ROOT);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO approval_instance (
                    tenant_id, shop_id, approval_no, business_type, business_id,
                    approval_type, safe_summary, requester_account_id,
                    create_idempotency_key, create_request_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, approvalNo);
            statement.setString(4, type);
            statement.setLong(5, businessId);
            statement.setString(6, approval);
            statement.setString(7, summary);
            statement.setLong(8, principal.accountId());
            statement.setString(9, idempotencyKey);
            statement.setString(10, requestHash);
            return statement;
        }, holder);
        long approvalId = holder.getKey().longValue();
        jdbcTemplate.update(
            """
            INSERT INTO approval_step (
                tenant_id, shop_id, approval_instance_id, step_no,
                candidate_permission, status
            ) VALUES (?, ?, ?, 1, ?, 'PENDING')
            """,
            principal.tenantId(), shopId, approvalId, candidate
        );
        audit(principal, shopId, approvalId, "APPROVAL_CREATED");
        outboxEventService.append(
            principal, shopId, "APPROVAL", Long.toString(approvalId),
            "ApprovalRequested", Map.of("approvalId", approvalId)
        );
        return requireInstance(principal, shopId, approvalId, false);
    }

    @Transactional
    public Map<String, Object> decide(
        TenantPrincipal principal,
        long shopId,
        long approvalId,
        int version,
        String action,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "approval:decide");
        Map<String, Object> instance = requireInstance(principal, shopId, approvalId, true);
        if (((Number) instance.get("version")).intValue() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "审批版本冲突");
        }
        if (!ApprovalPolicy.canDecide(
            principal.accountId(), number(instance.get("requesterAccountId"))
        )) {
            throw new ApiException(HttpStatus.FORBIDDEN, "申请人与审批人必须分离");
        }
        String target = switch (required(action, "审批动作", 20).toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> "APPROVED";
            case "REJECT", "REJECTED" -> "REJECTED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "审批动作不正确");
        };
        if (!ApprovalPolicy.canTransition(instance.get("status").toString(), target)) {
            throw new ApiException(HttpStatus.CONFLICT, "审批已决定，不能覆盖");
        }
        String safeReason = trim(reason, 500);
        if ("REJECTED".equals(target) && safeReason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "拒绝审批必须填写原因");
        }
        Map<String, Object> step = jdbcTemplate.queryForMap(
            """
            SELECT id, candidate_permission AS candidatePermission
            FROM approval_step
            WHERE tenant_id = ? AND shop_id = ? AND approval_instance_id = ?
              AND status = 'PENDING'
            FOR UPDATE
            """,
            principal.tenantId(), shopId, approvalId
        );
        accessService.requireShopPermission(
            principal, shopId, step.get("candidatePermission").toString()
        );
        int stepChanged = jdbcTemplate.update(
            """
            UPDATE approval_step
            SET status = 'DECIDED', decision = ?, decision_reason = ?,
                decided_by = ?, decided_at = CURRENT_TIMESTAMP(3),
                decision_idempotency_key = ?, decision_request_hash = ?
            WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
            """,
            target, safeReason, principal.accountId(), idempotencyKey, requestHash,
            number(step.get("id")), principal.tenantId()
        );
        if (stepChanged != 1) throw new ApiException(HttpStatus.CONFLICT, "审批步骤已被处理");
        int changed = jdbcTemplate.update(
            """
            UPDATE approval_instance
            SET status = ?, active_flag = 0, decided_by = ?,
                decided_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'PENDING' AND version = ?
            """,
            target, principal.accountId(), approvalId, principal.tenantId(), shopId, version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "审批已被处理");
        handleDecision(principal, instance, approvalId, target);
        audit(principal, shopId, approvalId, "APPROVAL_" + target);
        outboxEventService.append(
            principal, shopId, "APPROVAL", Long.toString(approvalId),
            "ApprovalDecided", Map.of("approvalId", approvalId)
        );
        return requireInstance(principal, shopId, approvalId, false);
    }

    @Transactional
    public Map<String, Object> cancel(
        TenantPrincipal principal,
        long shopId,
        long approvalId,
        int version,
        String reason
    ) {
        Map<String, Object> instance = requireInstance(principal, shopId, approvalId, true);
        if (number(instance.get("requesterAccountId")) != principal.accountId()
            && !management(principal)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "审批记录不存在");
        }
        if (((Number) instance.get("version")).intValue() != version
            || !ApprovalPolicy.canTransition(instance.get("status").toString(), "CANCELLED")) {
            throw new ApiException(HttpStatus.CONFLICT, "审批状态或版本已变化");
        }
        jdbcTemplate.update(
            """
            UPDATE approval_step
            SET status = 'CANCELLED'
            WHERE tenant_id = ? AND approval_instance_id = ? AND status = 'PENDING'
            """,
            principal.tenantId(), approvalId
        );
        jdbcTemplate.update(
            """
            UPDATE approval_instance
            SET status = 'CANCELLED', active_flag = 0, decided_by = ?,
                decided_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND status = 'PENDING' AND version = ?
            """,
            principal.accountId(), approvalId, principal.tenantId(), version
        );
        handleDecision(principal, instance, approvalId, "CANCELLED");
        audit(principal, shopId, approvalId, "APPROVAL_CANCELLED");
        return requireInstance(principal, shopId, approvalId, false);
    }

    private void handleDecision(
        TenantPrincipal principal,
        Map<String, Object> instance,
        long approvalId,
        String decision
    ) {
        if ("COMMISSION_ADJUSTMENT".equals(instance.get("businessType"))) {
            adjustmentService.getObject().handleDecision(
                principal,
                number(instance.get("shopId")),
                number(instance.get("businessId")),
                approvalId,
                decision
            );
        }
    }

    private Map<String, Object> requireInstance(
        TenantPrincipal principal, long shopId, long approvalId, boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, approval_no AS approvalNo,
                   business_type AS businessType, business_id AS businessId,
                   approval_type AS approvalType, safe_summary AS safeSummary,
                   requester_account_id AS requesterAccountId,
                   status, version, previous_instance_id AS previousInstanceId,
                   decided_by AS decidedBy, decided_at AS decidedAt,
                   created_at AS createdAt
            FROM approval_instance
            WHERE id = ? AND tenant_id = ? AND shop_id = ?%s
            """.formatted(lock ? " FOR UPDATE" : ""),
            approvalId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "审批记录不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private boolean management(TenantPrincipal principal) {
        return principal.roles().stream().anyMatch(MANAGEMENT_ROLES::contains);
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED")
            .contains(result)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "审批状态不正确");
        }
        return result;
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
        }
        return value.trim();
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "原因过长");
        }
        return value.trim();
    }

    private void audit(
        TenantPrincipal principal, long shopId, long approvalId, String action
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'APPROVAL', ?)
            """,
            principal.tenantId(), shopId, principal.accountId(), action, approvalId
        );
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
