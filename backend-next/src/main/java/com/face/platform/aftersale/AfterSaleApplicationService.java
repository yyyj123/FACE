package com.face.platform.aftersale;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.transaction.RefundApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AfterSaleApplicationService {

    private static final Set<String> MANAGEMENT_ROLES = Set.of(
        "OWNER", "MANAGER", "REGIONAL_MANAGER"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final RefundApplicationService refundService;
    private final OutboxEventService outboxEventService;

    public AfterSaleApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        RefundApplicationService refundService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.refundService = refundService;
        this.outboxEventService = outboxEventService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        long shopId,
        String status,
        int page,
        int pageSize
    ) {
        Long memberId = viewScope(principal, shopId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String normalized = optionalStatus(status);
        StringBuilder sql = new StringBuilder(
            """
            SELECT id, case_no AS caseNo, member_id AS memberId,
                   order_id AS orderId, service_record_id AS serviceRecordId,
                   category, origin_type AS originType, priority, summary,
                   entry_deadline_at AS entryDeadlineAt,
                   resolution_type AS resolutionType, resolution_note AS resolutionNote,
                   risk_amount AS riskAmount, requires_super_admin AS requiresSuperAdmin,
                   customer_response_due_at AS customerResponseDueAt,
                   customer_confirmed_at AS customerConfirmedAt,
                   reopen_count AS reopenCount, status,
                   assignee_account_id AS assigneeAccountId,
                   refund_id AS refundId, version, created_at AS createdAt,
                   updated_at AS updatedAt
            FROM after_sale_case
            WHERE tenant_id = ? AND shop_id = ?
            """
        );
        List<Object> args = new ArrayList<>(List.of(principal.tenantId(), shopId));
        if (memberId != null) {
            sql.append(" AND member_id = ?");
            args.add(memberId);
        }
        if (normalized != null) {
            sql.append(" AND status = ?");
            args.add(normalized);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        return Map.of(
            "records", jdbcTemplate.queryForList(sql.toString(), args.toArray()),
            "page", safePage,
            "pageSize", safeSize
        );
    }

    public Map<String, Object> detail(
        TenantPrincipal principal, long shopId, long caseId
    ) {
        Long memberId = viewScope(principal, shopId);
        Map<String, Object> afterSale = requireCase(principal, shopId, caseId, false);
        if (memberId != null && number(afterSale.get("memberId")) != memberId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "售后工单不存在");
        }
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
            """
            SELECT id, action, from_status AS fromStatus, to_status AS toStatus,
                   safe_note AS safeNote, created_by AS createdBy,
                   created_at AS createdAt
            FROM after_sale_case_log
            WHERE tenant_id = ? AND shop_id = ? AND case_id = ?
            ORDER BY id
            """,
            principal.tenantId(), shopId, caseId
        );
        Map<String, Object> result = new LinkedHashMap<>(afterSale);
        result.put("logs", logs);
        return result;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        long shopId,
        Long memberId,
        Long orderId,
        Long serviceRecordId,
        String category,
        String priority,
        String summary,
        String idempotencyKey,
        String requestHash
    ) {
        long scopedMemberId;
        if (principal.roles().contains("MEMBER")) {
            scopedMemberId = accessService.requireMemberId(principal);
            if (memberId != null && memberId.longValue() != scopedMemberId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在");
            }
        } else {
            accessService.requireShopPermission(principal, shopId, "aftersale:create");
            if (memberId == null || memberId <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "会员不能为空");
            }
            scopedMemberId = memberId;
        }
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, create_request_hash AS requestHash
            FROM after_sale_case
            WHERE tenant_id = ? AND create_idempotency_key = ?
            """,
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "售后幂等键已用于不同请求");
            }
            return detail(principal, shopId, number(replay.getFirst().get("id")));
        }
        validateReferences(principal, shopId, scopedMemberId, orderId, serviceRecordId);
        LocalDateTime serviceFinalizedAt = null;
        boolean lateServiceEntry = false;
        if (serviceRecordId != null) {
            List<Timestamp> finalized = jdbcTemplate.queryForList(
                """
                SELECT finalized_at
                FROM customer_confirmation
                WHERE tenant_id = ? AND service_record_id = ?
                  AND status IN ('CONFIRMED', 'SYSTEM_AUTO_CONFIRMED', 'REJECTED')
                LIMIT 1
                """,
                Timestamp.class, principal.tenantId(), serviceRecordId
            );
            if (!finalized.isEmpty() && finalized.getFirst() != null) {
                serviceFinalizedAt = finalized.getFirst().toLocalDateTime();
                lateServiceEntry = LocalDateTime.now().isAfter(serviceFinalizedAt.plusDays(7));
                if (lateServiceEntry && principal.roles().contains("MEMBER")) {
                    throw new ApiException(HttpStatus.CONFLICT, "该护理已超过 7 天售后申请期限");
                }
                if (lateServiceEntry && !principal.roles().contains("SUPER_ADMIN")) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "超期服务售后仅超级管理员可代建并留存原因");
                }
            }
        }
        String safeCategory = enumValue(
            category,
            Set.of("SERVICE_QUALITY", "REFUND", "PACKAGE", "ACCOUNT", "PRODUCT", "OTHER"),
            "售后类别"
        );
        String safePriority = enumValue(
            priority == null ? "NORMAL" : priority,
            Set.of("LOW", "NORMAL", "HIGH", "URGENT"),
            "售后优先级"
        );
        String safeSummary = required(summary, "售后摘要", 500);
        String caseNo = "AS" + System.currentTimeMillis()
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6)
            .toUpperCase(Locale.ROOT);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO after_sale_case (
                    tenant_id, shop_id, case_no, member_id, order_id,
                    service_record_id, category, priority, summary, status,
                    create_idempotency_key, create_request_hash, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, caseNo);
            statement.setLong(4, scopedMemberId);
            nullableLong(statement, 5, orderId);
            nullableLong(statement, 6, serviceRecordId);
            statement.setString(7, safeCategory);
            statement.setString(8, safePriority);
            statement.setString(9, safeSummary);
            statement.setString(10, idempotencyKey);
            statement.setString(11, requestHash);
            statement.setLong(12, principal.accountId());
            return statement;
        }, holder);
        long caseId = holder.getKey().longValue();
        if (serviceFinalizedAt != null) {
            jdbcTemplate.update(
                "UPDATE after_sale_case SET entry_deadline_at = ?, late_create_reason = ? WHERE id = ?",
                Timestamp.valueOf(serviceFinalizedAt.plusDays(7)),
                lateServiceEntry ? safeSummary : null,
                caseId
            );
        }
        log(
            principal, shopId, caseId, "CREATED", null, "OPEN",
            null, idempotencyKey + ":log", requestHash
        );
        audit(principal, shopId, caseId, "AFTERSALE_CREATED");
        outboxEventService.append(
            principal, shopId, "AFTER_SALE_CASE", Long.toString(caseId),
            "AfterSaleCaseCreated", Map.of("afterSaleCaseId", caseId)
        );
        return detail(principal, shopId, caseId);
    }

    @Transactional
    public Map<String, Object> action(
        TenantPrincipal principal,
        long shopId,
        long caseId,
        int version,
        String targetStatus,
        String note,
        Long assigneeAccountId,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "aftersale:manage");
        Map<String, Object> afterSale = requireCase(principal, shopId, caseId, true);
        if (((Number) afterSale.get("version")).intValue() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "售后工单版本冲突");
        }
        String from = afterSale.get("status").toString();
        String target = optionalStatus(targetStatus);
        if (target == null || !AfterSalePolicy.canTransition(from, target)
            || "REOPENED".equals(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "售后状态不允许执行该动作");
        }
        String safeNote = trim(note, 500);
        if (Set.of("REJECTED", "RESOLVED", "CLOSED").contains(target) && safeNote == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该售后动作必须填写原因");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE after_sale_case
            SET status = ?, assignee_account_id = COALESCE(?, assignee_account_id),
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = ? AND version = ?
            """,
            target, assigneeAccountId, caseId, principal.tenantId(),
            shopId, from, version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "售后工单已被处理");
        log(
            principal, shopId, caseId, "STATUS_CHANGED", from, target,
            safeNote, idempotencyKey, requestHash
        );
        audit(principal, shopId, caseId, "AFTERSALE_" + target);
        outboxEventService.append(
            principal, shopId, "AFTER_SALE_CASE", Long.toString(caseId),
            "AfterSaleStatusChanged", Map.of("afterSaleCaseId", caseId)
        );
        return detail(principal, shopId, caseId);
    }

    @Transactional
    public Map<String, Object> reopen(
        TenantPrincipal principal,
        long shopId,
        long caseId,
        int version,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        Map<String, Object> afterSale = requireCase(principal, shopId, caseId, true);
        if (principal.roles().contains("MEMBER")) {
            long memberId = accessService.requireMemberId(principal);
            if (number(afterSale.get("memberId")) != memberId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "售后工单不存在");
            }
        } else {
            accessService.requireShopPermission(principal, shopId, "aftersale:manage");
        }
        if (((Number) afterSale.get("version")).intValue() != version
            || !AfterSalePolicy.canTransition(afterSale.get("status").toString(), "REOPENED")
            || ((Number) afterSale.get("reopenCount")).intValue() >= 1
            || afterSale.get("entryDeadlineAt") == null) {
            throw new ApiException(HttpStatus.CONFLICT, "售后工单不能重开");
        }
        String safeReason = required(reason, "重开原因", 500);
        int changed = jdbcTemplate.update(
            """
            UPDATE after_sale_case
            SET status = 'REOPENED', reopen_count = reopen_count + 1,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'RESOLVED' AND version = ? AND reopen_count = 0
              AND entry_deadline_at >= CURRENT_TIMESTAMP(3)
            """,
            caseId, principal.tenantId(), shopId, version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "售后已超过重开期限或已重开过一次");
        }
        log(
            principal, shopId, caseId, "REOPENED", "RESOLVED", "REOPENED",
            safeReason, idempotencyKey, requestHash
        );
        audit(principal, shopId, caseId, "AFTERSALE_REOPENED");
        outboxEventService.append(
            principal, shopId, "AFTER_SALE_CASE", Long.toString(caseId),
            "AfterSaleCaseReopened", Map.of("afterSaleCaseId", caseId)
        );
        return detail(principal, shopId, caseId);
    }

    @Transactional
    public Map<String, Object> requestRefund(
        TenantPrincipal principal,
        long shopId,
        long caseId,
        long paymentId,
        BigDecimal amount,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "refund:request");
        Map<String, Object> afterSale = requireCase(principal, shopId, caseId, true);
        if (afterSale.get("orderId") == null) {
            throw new ApiException(HttpStatus.CONFLICT, "售后工单未关联可退款订单");
        }
        if (afterSale.get("refundId") != null) {
            return detail(principal, shopId, caseId);
        }
        Map<String, Object> refund = refundService.request(
            principal, shopId, number(afterSale.get("orderId")), paymentId,
            amount, required(reason, "退款原因", 500),
            idempotencyKey + ":refund", requestHash
        );
        long refundId = number(refund.get("id"));
        jdbcTemplate.update(
            """
            UPDATE after_sale_case
            SET refund_id = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND refund_id IS NULL
            """,
            refundId, caseId, principal.tenantId(), shopId
        );
        log(
            principal, shopId, caseId, "REFUND_REQUESTED",
            afterSale.get("status").toString(), afterSale.get("status").toString(),
            null, idempotencyKey, requestHash
        );
        audit(principal, shopId, caseId, "AFTERSALE_REFUND_REQUESTED");
        outboxEventService.append(
            principal, shopId, "AFTER_SALE_CASE", Long.toString(caseId),
            "AfterSaleRefundRequested",
            Map.of("afterSaleCaseId", caseId, "refundId", refundId)
        );
        return detail(principal, shopId, caseId);
    }

    private Long viewScope(TenantPrincipal principal, long shopId) {
        if (principal.roles().contains("MEMBER")) {
            return accessService.requireMemberId(principal);
        }
        accessService.requireShopPermission(principal, shopId, "aftersale:view");
        return null;
    }

    private Map<String, Object> requireCase(
        TenantPrincipal principal, long shopId, long caseId, boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, case_no AS caseNo, member_id AS memberId,
                   order_id AS orderId, service_record_id AS serviceRecordId,
                   category, origin_type AS originType, priority, summary,
                   entry_deadline_at AS entryDeadlineAt,
                   late_create_reason AS lateCreateReason,
                   resolution_type AS resolutionType, resolution_note AS resolutionNote,
                   risk_amount AS riskAmount, requires_super_admin AS requiresSuperAdmin,
                   customer_response_due_at AS customerResponseDueAt,
                   customer_confirmed_at AS customerConfirmedAt,
                   reopen_count AS reopenCount, evidence_json AS evidence, status,
                   assignee_account_id AS assigneeAccountId,
                   refund_id AS refundId, version, created_by AS createdBy,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM after_sale_case
            WHERE id = ? AND tenant_id = ? AND shop_id = ?%s
            """.formatted(lock ? " FOR UPDATE" : ""),
            caseId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "售后工单不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void validateReferences(
        TenantPrincipal principal, long shopId, long memberId,
        Long orderId, Long serviceRecordId
    ) {
        Integer member = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM member WHERE id = ? AND tenant_id = ?",
            Integer.class, memberId, principal.tenantId()
        );
        if (member == null || member == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在");
        }
        if (orderId != null) {
            Integer order = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM sales_order
                WHERE id = ? AND tenant_id = ? AND shop_id = ? AND member_id = ?
                """,
                Integer.class, orderId, principal.tenantId(), shopId, memberId
            );
            if (order == null || order == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
            }
        }
        if (serviceRecordId != null) {
            Integer service = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM service_record sr
                JOIN appointment a ON a.id = sr.appointment_id AND a.tenant_id = sr.tenant_id
                WHERE sr.id = ? AND sr.tenant_id = ? AND sr.shop_id = ? AND a.member_id = ?
                """,
                Integer.class, serviceRecordId, principal.tenantId(), shopId, memberId
            );
            if (service == null || service == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "服务记录不存在");
            }
        }
    }

    private void log(
        TenantPrincipal principal, long shopId, long caseId, String action,
        String from, String to, String note, String key, String requestHash
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO after_sale_case_log (
                tenant_id, shop_id, case_id, action, from_status, to_status,
                safe_note, idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, caseId, action, from, to, note,
            key, requestHash, principal.accountId()
        );
    }

    private void audit(
        TenantPrincipal principal, long shopId, long caseId, String action
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'AFTER_SALE_CASE', ?)
            """,
            principal.tenantId(), shopId, principal.accountId(), action, caseId
        );
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        return enumValue(
            value,
            Set.of("OPEN", "TRIAGED", "PROCESSING", "WAITING_CUSTOMER",
                "RESOLVED", "CLOSED", "REJECTED", "REOPENED"),
            "售后状态"
        );
    }

    private String enumValue(String value, Set<String> allowed, String label) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(result)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "售后说明过长");
        }
        return value.trim();
    }

    private void nullableLong(java.sql.PreparedStatement statement, int index, Long value)
        throws java.sql.SQLException {
        if (value == null) statement.setObject(index, null);
        else statement.setLong(index, value);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
