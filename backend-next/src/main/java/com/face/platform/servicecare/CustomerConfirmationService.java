package com.face.platform.servicecare;

import com.face.platform.api.ApiException;
import com.face.platform.benefit.BenefitApplicationService;
import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerConfirmationService {

    private static final Set<String> ACTIONS = Set.of("CONFIRMED", "REJECTED");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final BenefitApplicationService benefitService;
    private final PointsApplicationService pointsService;

    public CustomerConfirmationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        BenefitApplicationService benefitService,
        PointsApplicationService pointsService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.benefitService = benefitService;
        this.pointsService = pointsService;
    }

    public void createPending(
        TenantPrincipal principal,
        long shopId,
        Long appointmentId,
        long serviceRecordId,
        long memberId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_confirmation (
                tenant_id, shop_id, appointment_id, service_record_id,
                member_id, confirmation_type, status, version,
                due_at, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, 'SERVICE_RESULT', 'PENDING', 0,
                      DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR), ?, ?)
            ON DUPLICATE KEY UPDATE due_at = COALESCE(due_at, VALUES(due_at))
            """,
            principal.tenantId(),
            shopId,
            appointmentId,
            serviceRecordId,
            memberId,
            principal.accountId(),
            principal.accountId()
        );
        jdbcTemplate.update(
            "UPDATE service_record SET fulfillment_status = 'PENDING_CUSTOMER_CONFIRMATION' WHERE id = ? AND tenant_id = ?",
            serviceRecordId, principal.tenantId()
        );
        if (appointmentId != null) {
            jdbcTemplate.update(
                "UPDATE appointment SET fulfillment_status = 'PENDING_CUSTOMER_CONFIRMATION' WHERE id = ? AND tenant_id = ?",
                appointmentId, principal.tenantId()
            );
        }
    }

    public List<Map<String, Object>> listMine(TenantPrincipal principal) {
        long memberId = tenantAccessService.requireMemberId(principal);
        return jdbcTemplate.queryForList(
            """
            SELECT cc.id, cc.shop_id AS shopId,
                   cc.appointment_id AS appointmentId,
                   cc.service_record_id AS serviceRecordId,
                   cc.confirmation_type AS confirmationType,
                   cc.status, cc.reject_reason AS rejectReason,
                   cc.due_at AS dueAt, cc.finalization_source AS finalizationSource,
                   cc.after_sale_case_id AS afterSaleCaseId,
                   cc.version, cc.acted_at AS actedAt,
                   cc.created_at AS createdAt,
                   sr.record_no AS recordNo,
                   sr.service_summary AS serviceSummary,
                   sr.next_visit_recommendation AS nextVisitRecommendation,
                   sr.actual_end_at AS completedAt,
                   st.name AS staffName,
                   cr.skin_type AS skinType,
                   cr.concerns,
                   cr.observations,
                   cr.home_care_advice AS homeCareAdvice,
                   cr.next_recommended_at AS nextRecommendedAt,
                   (SELECT GROUP_CONCAT(
                      sri.service_name_snapshot ORDER BY sri.sort_order SEPARATOR '、')
                    FROM service_record_item sri
                    WHERE sri.service_record_id = sr.id) AS serviceNames
            FROM customer_confirmation cc
            JOIN service_record sr
              ON sr.id = cc.service_record_id
             AND sr.tenant_id = cc.tenant_id
            JOIN staff st ON st.id = sr.staff_id
            LEFT JOIN care_record cr ON cr.service_record_id = sr.id
            WHERE cc.tenant_id = ? AND cc.member_id = ?
            ORDER BY cc.created_at DESC, cc.id DESC
            """,
            principal.tenantId(),
            memberId
        );
    }

    @Transactional
    public Map<String, Object> act(
        TenantPrincipal principal,
        long confirmationId,
        CustomerConfirmationActionRequest request
    ) {
        String action = normalizeAction(request);
        String reason = normalizeReason(action, request.reason());
        long memberId = tenantAccessService.requireMemberId(principal);
        String idempotencyKey = request.idempotencyKey().trim();
        String requestHash = SessionTokenCodec.sha256(
            confirmationId + "|" + action + "|"
                + (reason == null ? "<null>" : reason) + "|" + request.version()
        );

        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, request_hash AS requestHash
            FROM customer_confirmation
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (!replay.isEmpty()) {
            Map<String, Object> existing = replay.getFirst();
            if (number(existing.get("id")) != confirmationId
                || number(existing.get("memberId")) != memberId
                || !requestHash.equals(existing.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "确认幂等键已用于不同请求");
            }
            return detail(principal.tenantId(), memberId, confirmationId);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, member_id AS memberId,
                   appointment_id AS appointmentId,
                   service_record_id AS serviceRecordId,
                   status, version, due_at AS dueAt
            FROM customer_confirmation
            WHERE id = ? AND tenant_id = ?
            FOR UPDATE
            """,
            confirmationId,
            principal.tenantId()
        );
        if (rows.isEmpty() || number(rows.getFirst().get("memberId")) != memberId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "待确认护理记录不存在");
        }
        Map<String, Object> row = rows.getFirst();
        if (!"PENDING".equals(row.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "护理结果已经处理");
        }
        if (number(row.get("version")) != request.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "确认记录版本已变化，请刷新后重试");
        }

        int changed = jdbcTemplate.update(
            """
            UPDATE customer_confirmation
            SET status = ?, reject_reason = ?, idempotency_key = ?,
                request_hash = ?, acted_by = ?, acted_at = CURRENT_TIMESTAMP(3),
                finalization_source = ?, finalized_at = CURRENT_TIMESTAMP(3),
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND member_id = ?
              AND version = ? AND status = 'PENDING'
            """,
            action,
            reason,
            idempotencyKey,
            requestHash,
            principal.accountId(),
            "CONFIRMED".equals(action) ? "MEMBER" : "DISPUTE",
            principal.accountId(),
            confirmationId,
            principal.tenantId(),
            memberId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "确认记录已变化，请刷新后重试");
        }
        if ("CONFIRMED".equals(action)) {
            finalizeConfirmed(
                principal, row, confirmationId, "CONFIRMED", idempotencyKey, requestHash
            );
        } else {
            openDispute(principal, row, confirmationId, reason, idempotencyKey, requestHash);
        }
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action,
                entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, 'CUSTOMER_CONFIRMATION_ACTION',
                      'CUSTOMER_CONFIRMATION', ?, JSON_OBJECT('status', ?))
            """,
            principal.tenantId(),
            number(row.get("shopId")),
            principal.accountId(),
            confirmationId,
            action
        );
        return detail(principal.tenantId(), memberId, confirmationId);
    }

    @Transactional
    public int processExpired(int limit) {
        int processed = 0;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT cc.id, cc.tenant_id AS tenantId, cc.shop_id AS shopId,
                   cc.member_id AS memberId, cc.appointment_id AS appointmentId,
                   cc.service_record_id AS serviceRecordId, cc.created_by AS createdBy,
                   cc.version
            FROM customer_confirmation cc
            WHERE cc.status = 'PENDING' AND cc.due_at <= CURRENT_TIMESTAMP(3)
            ORDER BY cc.due_at, cc.id LIMIT ?
            """,
            Math.max(1, Math.min(limit, 200))
        );
        for (Map<String, Object> row : rows) {
            long confirmationId = number(row.get("id"));
            String key = "system-auto-confirm:" + confirmationId;
            String requestHash = SessionTokenCodec.sha256(key);
            int changed = jdbcTemplate.update(
                """
                UPDATE customer_confirmation
                SET status = 'SYSTEM_AUTO_CONFIRMED', idempotency_key = ?,
                    request_hash = ?, acted_by = ?, acted_at = CURRENT_TIMESTAMP(3),
                    finalization_source = 'SYSTEM', finalized_at = CURRENT_TIMESTAMP(3),
                    version = version + 1, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
                  AND due_at <= CURRENT_TIMESTAMP(3) AND version = ?
                """,
                key, requestHash, number(row.get("createdBy")), number(row.get("createdBy")),
                confirmationId, number(row.get("tenantId")), number(row.get("version"))
            );
            if (changed == 0) continue;
            TenantPrincipal system = systemPrincipal(row);
            finalizeConfirmed(
                system, row, confirmationId, "SYSTEM_AUTO_CONFIRMED", key, requestHash
            );
            processed++;
        }
        return processed;
    }

    private void finalizeConfirmed(
        TenantPrincipal principal,
        Map<String, Object> row,
        long confirmationId,
        String outcome,
        String idempotencyKey,
        String requestHash
    ) {
        long shopId = number(row.get("shopId"));
        long serviceRecordId = number(row.get("serviceRecordId"));
        Long appointmentId = nullableNumber(row.get("appointmentId"));
        List<Long> orders = jdbcTemplate.queryForList(
            "SELECT id FROM sales_order WHERE tenant_id = ? AND shop_id = ? AND service_record_id = ? ORDER BY id LIMIT 1",
            Long.class, principal.tenantId(), shopId, serviceRecordId
        );
        Long orderId = orders.isEmpty() ? null : orders.getFirst();
        int consumed = 0;
        long pointsBatchId = 0;
        if (orderId != null) {
            TenantPrincipal system = systemPrincipal(principal, shopId);
            consumed += benefitService.consumeReservedBenefits(system, shopId, orderId);
            consumed += pointsService.consumeReference(system, shopId, "SALES_ORDER", orderId);
        }
        jdbcTemplate.update(
            "UPDATE service_record SET fulfillment_status = 'COMPLETED', customer_confirmed_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND tenant_id = ?",
            serviceRecordId, principal.tenantId()
        );
        if (appointmentId != null) {
            jdbcTemplate.update(
                "UPDATE appointment SET fulfillment_status = 'COMPLETED' WHERE id = ? AND tenant_id = ?",
                appointmentId, principal.tenantId()
            );
        }
        if (orderId != null) {
            pointsBatchId = pointsService.earnPaidSalesOrder(
                systemPrincipal(principal, shopId), shopId, orderId
            );
        }
        jdbcTemplate.update(
            """
            INSERT INTO service_fulfillment_fact (
                tenant_id, shop_id, confirmation_id, service_record_id,
                appointment_id, order_id, member_id, outcome,
                entitlement_consumed_count, points_batch_id,
                idempotency_key, request_hash, finalized_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = id
            """,
            principal.tenantId(), shopId, confirmationId, serviceRecordId,
            appointmentId, orderId, number(row.get("memberId")), outcome,
            consumed, pointsBatchId == 0 ? null : pointsBatchId,
            idempotencyKey + ":fulfillment", requestHash, principal.accountId()
        );
    }

    private void openDispute(
        TenantPrincipal principal,
        Map<String, Object> row,
        long confirmationId,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        long shopId = number(row.get("shopId"));
        long serviceRecordId = number(row.get("serviceRecordId"));
        List<Long> orders = jdbcTemplate.queryForList(
            "SELECT id FROM sales_order WHERE tenant_id = ? AND shop_id = ? AND service_record_id = ? ORDER BY id LIMIT 1",
            Long.class, principal.tenantId(), shopId, serviceRecordId
        );
        Long orderId = orders.isEmpty() ? null : orders.getFirst();
        String afterSaleKey = idempotencyKey + ":aftersale";
        jdbcTemplate.update(
            """
            INSERT INTO after_sale_case (
                tenant_id, shop_id, case_no, member_id, order_id,
                service_record_id, category, origin_type, priority, summary,
                entry_deadline_at, status, create_idempotency_key,
                create_request_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, 'SERVICE_QUALITY', 'SERVICE_DISPUTE',
                      'HIGH', ?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY),
                      'OPEN', ?, ?, ?)
            """,
            principal.tenantId(), shopId,
            "AS-DISPUTE-" + principal.tenantId() + "-" + confirmationId,
            number(row.get("memberId")), orderId, serviceRecordId, reason,
            afterSaleKey, requestHash, principal.accountId()
        );
        Long caseId = jdbcTemplate.queryForObject(
            "SELECT id FROM after_sale_case WHERE tenant_id = ? AND create_idempotency_key = ?",
            Long.class, principal.tenantId(), afterSaleKey
        );
        if (caseId == null) {
            throw new IllegalStateException("Failed to create after-sale case for disputed confirmation");
        }
        jdbcTemplate.update(
            """
            INSERT INTO after_sale_case_log (
                tenant_id, shop_id, case_id, action, from_status, to_status,
                safe_note, idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, 'CREATED', NULL, 'OPEN', ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, caseId, reason,
            afterSaleKey + ":log", requestHash, principal.accountId()
        );
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, 'AFTERSALE_CREATED_FROM_DISPUTE',
                      'AFTER_SALE_CASE', ?)
            """,
            principal.tenantId(), shopId, principal.accountId(), caseId
        );
        jdbcTemplate.update(
            "UPDATE customer_confirmation SET after_sale_case_id = ? WHERE id = ?",
            caseId, confirmationId
        );
        jdbcTemplate.update(
            "UPDATE service_record SET fulfillment_status = 'AFTER_SALES_PROCESSING' WHERE id = ? AND tenant_id = ?",
            serviceRecordId, principal.tenantId()
        );
        if (row.get("appointmentId") != null) {
            jdbcTemplate.update(
                "UPDATE appointment SET fulfillment_status = 'AFTER_SALES_PROCESSING' WHERE id = ? AND tenant_id = ?",
                number(row.get("appointmentId")), principal.tenantId()
            );
        }
        jdbcTemplate.update(
            """
            INSERT INTO service_fulfillment_fact (
                tenant_id, shop_id, confirmation_id, service_record_id,
                appointment_id, order_id, member_id, outcome,
                idempotency_key, request_hash, finalized_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'DISPUTED', ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = id
            """,
            principal.tenantId(), shopId, confirmationId, serviceRecordId,
            nullableNumber(row.get("appointmentId")), orderId,
            number(row.get("memberId")), idempotencyKey + ":fulfillment",
            requestHash, principal.accountId()
        );
    }

    private Map<String, Object> detail(long tenantId, long memberId, long confirmationId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT id, shop_id AS shopId, appointment_id AS appointmentId,
                   service_record_id AS serviceRecordId,
                   confirmation_type AS confirmationType,
                   status, reject_reason AS rejectReason,
                   due_at AS dueAt, finalization_source AS finalizationSource,
                   after_sale_case_id AS afterSaleCaseId,
                   version, acted_at AS actedAt, created_at AS createdAt
            FROM customer_confirmation
            WHERE id = ? AND tenant_id = ? AND member_id = ?
            """,
            confirmationId,
            tenantId,
            memberId
        );
    }

    private String normalizeAction(CustomerConfirmationActionRequest request) {
        if (request == null
            || request.action() == null
            || request.version() == null
            || request.idempotencyKey() == null
            || request.idempotencyKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "确认请求不完整");
        }
        String action = request.action().trim().toUpperCase();
        if (!ACTIONS.contains(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "确认结果无效");
        }
        return action;
    }

    private String normalizeReason(String action, String reason) {
        String normalized = reason == null || reason.isBlank() ? null : reason.trim();
        if ("REJECTED".equals(action) && normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "拒绝护理结果时必须填写原因");
        }
        return "REJECTED".equals(action) ? normalized : null;
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("顾客确认数据不完整");
        }
        return number.longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private TenantPrincipal systemPrincipal(Map<String, Object> row) {
        long shopId = number(row.get("shopId"));
        return new TenantPrincipal(
            number(row.get("createdBy")), number(row.get("tenantId")), shopId,
            "sc6-system", List.of("SUPER_ADMIN"), Set.of(), Set.of(shopId), true
        );
    }

    private TenantPrincipal systemPrincipal(TenantPrincipal principal, long shopId) {
        return new TenantPrincipal(
            principal.accountId(), principal.tenantId(), shopId,
            "sc6-fulfillment", List.of("SUPER_ADMIN"), Set.of(), Set.of(shopId), true
        );
    }
}
