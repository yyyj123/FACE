package com.face.platform.sc6;

import com.face.platform.api.ApiException;
import com.face.platform.aftersale.AfterSaleApplicationService;
import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class Sc6AfterSaleApplicationService {

    private static final Set<String> RESOLUTIONS = Set.of(
        "REFUND", "REDO_SERVICE", "RESTORE_ENTITLEMENT", "COMPENSATION_COUPON", "REJECT"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final AfterSaleApplicationService afterSaleService;
    private final PointsApplicationService pointsService;
    private final ObjectMapper objectMapper;

    public Sc6AfterSaleApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        AfterSaleApplicationService afterSaleService,
        PointsApplicationService pointsService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.afterSaleService = afterSaleService;
        this.pointsService = pointsService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> decide(
        TenantPrincipal principal, long shopId, long caseId, int expectedVersion,
        String resolutionType, BigDecimal riskAmount, String note,
        Object evidence, String idempotencyKey
    ) {
        accessService.requireShopPermission(principal, shopId, "aftersale:manage");
        String resolution = enumValue(resolutionType, RESOLUTIONS, "售后方案");
        BigDecimal amount = riskAmount == null ? BigDecimal.ZERO : riskAmount;
        if (amount.signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "风险金额不能小于零");
        boolean elevated = Sc6Policy.requiresSuperAdmin(amount);
        if (elevated && !principal.roles().contains("SUPER_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "该资产操作超过普通管理员阈值，需超级管理员处理");
        }
        String safeNote = required(note, "处理结论", 500);
        Map<String, Object> afterSale = lockCase(principal, shopId, caseId);
        if (((Number) afterSale.get("version")).intValue() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "售后工单版本已变化");
        }
        if (!Set.of("OPEN", "TRIAGED", "PROCESSING", "REOPENED").contains(afterSale.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "当前售后状态不能给出处理方案");
        }
        String target = "REFUND".equals(resolution) ? "PROCESSING" : "WAITING_CUSTOMER";
        int changed = jdbcTemplate.update(
            """
            UPDATE after_sale_case
            SET status = ?, resolution_type = ?, resolution_note = ?, risk_amount = ?,
                requires_super_admin = ?, evidence_json = CAST(? AS JSON),
                customer_response_due_at = CASE WHEN ? = 'WAITING_CUSTOMER'
                  THEN DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 48 HOUR) ELSE NULL END,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            target, resolution, safeNote, amount, elevated, json(evidence), target,
            caseId, principal.tenantId(), shopId, expectedVersion
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "售后工单已被处理");
        String key = requiredKey(idempotencyKey);
        String requestHash = SessionTokenCodec.sha256(caseId + "|" + resolution + "|" + amount);
        log(principal, shopId, caseId, "SOLUTION_DECIDED", afterSale.get("status").toString(), target,
            safeNote, key, requestHash);
        if ("REFUND".equals(resolution)) {
            if (amount.signum() <= 0 || afterSale.get("orderId") == null) {
                throw new ApiException(HttpStatus.CONFLICT, "退款方案必须关联已支付订单并填写退款金额");
            }
            List<Long> payments = jdbcTemplate.queryForList(
                "SELECT id FROM payment_transaction WHERE tenant_id = ? AND shop_id = ? AND order_id = ? AND status = 'SUCCESS' AND amount > refunded_amount ORDER BY paid_at DESC, id DESC LIMIT 1",
                Long.class, principal.tenantId(), shopId, number(afterSale.get("orderId"))
            );
            if (payments.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "关联订单没有可执行退款的成功收款");
            }
            afterSaleService.requestRefund(
                principal, shopId, caseId, payments.getFirst(), amount, safeNote,
                key + ":request", requestHash
            );
        } else {
            appendAssetDecision(principal, shopId, caseId, resolution, amount, safeNote,
                key + ":asset", evidence);
        }
        audit(principal, shopId, caseId, "AFTERSALE_SOLUTION_DECIDED");
        return afterSaleService.detail(principal, shopId, caseId);
    }

    @Transactional
    public Map<String, Object> customerResponse(
        TenantPrincipal principal, long shopId, long caseId, int expectedVersion,
        boolean accepted, String reason, String idempotencyKey
    ) {
        long memberId = accessService.requireMemberId(principal);
        Map<String, Object> afterSale = lockCase(principal, shopId, caseId);
        if (number(afterSale.get("memberId")) != memberId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "售后工单不存在");
        }
        if (((Number) afterSale.get("version")).intValue() != expectedVersion
            || !"WAITING_CUSTOMER".equals(afterSale.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "售后工单已变化或尚未等待确认");
        }
        String target;
        if (accepted) {
            target = "CLOSED";
        } else {
            LocalDateTime deadline = dateTime(afterSale.get("entryDeadlineAt"));
            int reopenCount = ((Number) afterSale.get("reopenCount")).intValue();
            if (!Sc6Policy.mayReopen(reopenCount, deadline, LocalDateTime.now())) {
                throw new ApiException(HttpStatus.CONFLICT, "该售后已超过重开期限或已重开过一次");
            }
            target = "REOPENED";
        }
        String safeReason = accepted ? trim(reason, 500) : required(reason, "重开原因", 500);
        jdbcTemplate.update(
            """
            UPDATE after_sale_case
            SET status = ?, customer_confirmed_at = CASE WHEN ? = 'CLOSED'
                  THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                reopen_count = reopen_count + CASE WHEN ? = 'REOPENED' THEN 1 ELSE 0 END,
                customer_response_due_at = NULL, version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ? AND status = 'WAITING_CUSTOMER'
            """,
            target, target, target, caseId, principal.tenantId(), expectedVersion
        );
        log(principal, shopId, caseId, accepted ? "CUSTOMER_ACCEPTED" : "CUSTOMER_REOPENED",
            "WAITING_CUSTOMER", target, safeReason, requiredKey(idempotencyKey),
            SessionTokenCodec.sha256(caseId + "|" + target + "|" + safeReason));
        audit(principal, shopId, caseId, "AFTERSALE_" + target);
        return afterSaleService.detail(principal, shopId, caseId);
    }

    @Transactional
    public int closeExpiredCustomerResponses(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, tenant_id AS tenantId, shop_id AS shopId, created_by AS createdBy, version FROM after_sale_case WHERE status = 'WAITING_CUSTOMER' AND customer_response_due_at <= CURRENT_TIMESTAMP(3) ORDER BY customer_response_due_at, id LIMIT ?",
            Math.max(1, Math.min(limit, 200))
        );
        int closed = 0;
        for (Map<String, Object> row : rows) {
            int changed = jdbcTemplate.update(
                "UPDATE after_sale_case SET status = 'CLOSED', customer_confirmed_at = CURRENT_TIMESTAMP(3), customer_response_due_at = NULL, version = version + 1 WHERE id = ? AND tenant_id = ? AND version = ? AND status = 'WAITING_CUSTOMER'",
                row.get("id"), row.get("tenantId"), row.get("version")
            );
            if (changed == 0) continue;
            TenantPrincipal system = new TenantPrincipal(
                number(row.get("createdBy")), number(row.get("tenantId")), number(row.get("shopId")),
                "sc6-system", List.of("SUPER_ADMIN"), Set.of(), Set.of(number(row.get("shopId"))), true
            );
            log(system, number(row.get("shopId")), number(row.get("id")), "SYSTEM_AUTO_CLOSED",
                "WAITING_CUSTOMER", "CLOSED", "会员 48 小时内未操作，系统自动关单",
                "system-close:" + row.get("id"), SessionTokenCodec.sha256("system-close:" + row.get("id")));
            closed++;
        }
        return closed;
    }

    @Transactional
    public int releaseCompletedRefunds(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT ac.id, ac.tenant_id AS tenantId, ac.shop_id AS shopId, ac.created_by AS createdBy, ac.refund_id AS refundId, ac.version, rt.amount FROM after_sale_case ac JOIN refund_transaction rt ON rt.id = ac.refund_id AND rt.tenant_id = ac.tenant_id WHERE ac.status = 'PROCESSING' AND ac.resolution_type = 'REFUND' AND rt.status = 'SUCCESS' ORDER BY rt.refunded_at, ac.id LIMIT ?",
            Math.max(1, Math.min(limit, 200))
        );
        int released = 0;
        for (Map<String, Object> row : rows) {
            int changed = jdbcTemplate.update(
                "UPDATE after_sale_case SET status = 'WAITING_CUSTOMER', customer_response_due_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 48 HOUR), version = version + 1 WHERE id = ? AND tenant_id = ? AND version = ? AND status = 'PROCESSING'",
                row.get("id"), row.get("tenantId"), row.get("version")
            );
            if (changed == 0) continue;
            TenantPrincipal system = new TenantPrincipal(
                number(row.get("createdBy")), number(row.get("tenantId")), number(row.get("shopId")),
                "sc6-system", List.of("SUPER_ADMIN"), Set.of(), Set.of(number(row.get("shopId"))), true
            );
            insertAsset(system, number(row.get("shopId")), number(row.get("id")),
                "CASH", (BigDecimal) row.get("amount"), "REFUND_TRANSACTION",
                number(row.get("refundId")), "refund-success:" + row.get("refundId"),
                "退款渠道已确认成功");
            log(system, number(row.get("shopId")), number(row.get("id")), "REFUND_COMPLETED",
                "PROCESSING", "WAITING_CUSTOMER", "退款成功，进入 48 小时顾客确认",
                "refund-completed:" + row.get("refundId"),
                SessionTokenCodec.sha256("refund-completed:" + row.get("refundId")));
            released++;
        }
        return released;
    }

    public List<Map<String, Object>> returns(
        TenantPrincipal principal, long shopId, String status
    ) {
        Long memberId = principal.roles().contains("MEMBER")
            ? accessService.requireMemberId(principal) : null;
        if (memberId == null) accessService.requireShopPermission(principal, shopId, "aftersale:view");
        StringBuilder sql = new StringBuilder(
            "SELECT rr.id, rr.mall_order_id AS mallOrderId, mo.order_no AS orderNo, rr.after_sale_case_id AS afterSaleCaseId, rr.reason_code AS reasonCode, rr.reason_detail AS reasonDetail, rr.status, rr.return_tracking_no AS returnTrackingNo, rr.inspection_result AS inspectionResult, rr.inspection_reason AS inspectionReason, rr.version, rr.submitted_at AS submittedAt FROM mall_return_request rr JOIN mall_order mo ON mo.id = rr.mall_order_id WHERE rr.tenant_id = ? AND rr.shop_id = ?"
        );
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(List.of(principal.tenantId(), shopId));
        if (memberId != null) { sql.append(" AND rr.member_id = ?"); args.add(memberId); }
        if (status != null && !status.isBlank()) { sql.append(" AND rr.status = ?"); args.add(status.trim().toUpperCase(Locale.ROOT)); }
        sql.append(" ORDER BY rr.submitted_at DESC, rr.id DESC LIMIT 200");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> createReturn(
        TenantPrincipal principal, long shopId, long mallOrderId, String reasonCode,
        String reasonDetail, List<ReturnLine> lines, String idempotencyKey
    ) {
        long memberId = accessService.requireMemberId(principal);
        String key = requiredKey(idempotencyKey);
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id FROM mall_return_request WHERE tenant_id = ? AND idempotency_key = ?",
            principal.tenantId(), key
        );
        if (!replay.isEmpty()) return returnDetail(principal.tenantId(), number(replay.getFirst().get("id")));
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            "SELECT id, status FROM mall_order WHERE id = ? AND tenant_id = ? AND shop_id = ? AND member_id = ?",
            mallOrderId, principal.tenantId(), shopId, memberId
        );
        if (orders.isEmpty() || !Set.of("SHIPPED", "COMPLETED").contains(orders.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已发货或已完成的商城订单可以申请退换货");
        }
        if (lines == null || lines.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择退货商品");
        String summary = required(reasonDetail, "退换货说明", 500);
        String hash = SessionTokenCodec.sha256(mallOrderId + "|" + reasonCode + "|" + summary);
        Map<String, Object> afterSale = afterSaleService.create(
            principal, shopId, memberId, null, null, "PRODUCT", "NORMAL", summary,
            key + ":aftersale", hash
        );
        long caseId = number(afterSale.get("id"));
        jdbcTemplate.update(
            "UPDATE after_sale_case SET origin_type = 'MALL_RETURN', evidence_json = JSON_OBJECT('mall_order_id', ?) WHERE id = ?",
            mallOrderId, caseId
        );
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO mall_return_request (tenant_id, shop_id, member_id, mall_order_id, after_sale_case_id, reason_code, reason_detail, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setLong(4, mallOrderId);
            statement.setLong(5, caseId);
            statement.setString(6, required(reasonCode, "退换货原因", 30));
            statement.setString(7, summary);
            statement.setString(8, key);
            return statement;
        }, holder);
        long returnId = generated(holder, "退换货申请创建失败");
        for (ReturnLine line : lines) {
            Integer allowed = jdbcTemplate.queryForObject(
                "SELECT quantity FROM mall_order_item WHERE id = ? AND mall_order_id = ?",
                Integer.class, line.orderItemId(), mallOrderId
            );
            if (allowed == null || line.quantity() <= 0 || line.quantity() > allowed) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "退货数量超过原订单数量");
            }
            jdbcTemplate.update(
                "INSERT INTO mall_return_item (return_request_id, mall_order_item_id, quantity) VALUES (?, ?, ?)",
                returnId, line.orderItemId(), line.quantity()
            );
        }
        audit(principal, shopId, returnId, "MALL_RETURN_SUBMITTED");
        return returnDetail(principal.tenantId(), returnId);
    }

    @Transactional
    public Map<String, Object> reviewReturn(
        TenantPrincipal principal, long shopId, long returnId, int expectedVersion,
        boolean approved, String reason, String idempotencyKey
    ) {
        accessService.requireShopPermission(principal, shopId, "mall:return:inspect");
        Map<String, Object> row = lockReturn(principal, shopId, returnId);
        requireReturn(row, expectedVersion, "SUBMITTED");
        String target = approved ? "APPROVED" : "INSPECTION_FAILED";
        jdbcTemplate.update(
            "UPDATE mall_return_request SET status = ?, inspection_reason = ?, version = version + 1 WHERE id = ? AND version = ? AND status = 'SUBMITTED'",
            target, approved ? null : required(reason, "驳回原因", 500), returnId, expectedVersion
        );
        audit(principal, shopId, returnId, approved ? "MALL_RETURN_APPROVED" : "MALL_RETURN_REJECTED");
        return returnDetail(principal.tenantId(), returnId);
    }

    @Transactional
    public Map<String, Object> shipReturn(
        TenantPrincipal principal, long shopId, long returnId, int expectedVersion,
        String trackingNo, String idempotencyKey
    ) {
        long memberId = accessService.requireMemberId(principal);
        Map<String, Object> row = lockReturn(principal, shopId, returnId);
        if (number(row.get("memberId")) != memberId) throw new ApiException(HttpStatus.NOT_FOUND, "退货申请不存在");
        requireReturn(row, expectedVersion, "APPROVED");
        jdbcTemplate.update(
            "UPDATE mall_return_request SET status = 'PENDING_INSPECTION', return_tracking_no = ?, version = version + 1 WHERE id = ? AND version = ? AND status = 'APPROVED'",
            required(trackingNo, "退货物流单号", 80), returnId, expectedVersion
        );
        moveToReturnPending(principal, row, returnId);
        audit(principal, shopId, returnId, "MALL_RETURN_PENDING_INSPECTION");
        return returnDetail(principal.tenantId(), returnId);
    }

    @Transactional
    public Map<String, Object> inspectReturn(
        TenantPrincipal principal, long shopId, long returnId, int expectedVersion,
        boolean passed, String disposition, String reason, Object evidence,
        String idempotencyKey
    ) {
        accessService.requireShopPermission(principal, shopId, "mall:return:inspect");
        Map<String, Object> row = lockReturn(principal, shopId, returnId);
        requireReturn(row, expectedVersion, "PENDING_INSPECTION");
        String safeDisposition = passed
            ? enumValue(disposition, Set.of("RESTORE", "DAMAGED"), "库存处理") : null;
        String safeReason = required(reason, "验货说明", 500);
        jdbcTemplate.update(
            "UPDATE mall_return_request SET status = ?, inspection_result = ?, inspection_reason = ?, evidence_json = CAST(? AS JSON), inspected_at = CURRENT_TIMESTAMP(3), inspected_by = ?, version = version + 1 WHERE id = ? AND version = ? AND status = 'PENDING_INSPECTION'",
            passed ? "INSPECTION_PASSED" : "INSPECTION_FAILED", passed ? "PASS" : "FAIL",
            safeReason, json(evidence), principal.accountId(), returnId, expectedVersion
        );
        if (passed) {
            settleInspectedReturn(principal, row, returnId, safeDisposition, safeReason);
        }
        audit(principal, shopId, returnId, passed ? "MALL_RETURN_INSPECTION_PASSED" : "MALL_RETURN_INSPECTION_FAILED");
        return returnDetail(principal.tenantId(), returnId);
    }

    private void moveToReturnPending(TenantPrincipal principal, Map<String, Object> row, long returnId) {
        List<Map<String, Object>> items = returnInventoryLines(returnId);
        for (Map<String, Object> item : items) {
            int quantity = ((Number) item.get("quantity")).intValue();
            long inventoryId = number(item.get("inventoryId"));
            jdbcTemplate.update(
                "UPDATE mall_sku_inventory SET sold_quantity = sold_quantity - ?, return_pending_quantity = return_pending_quantity + ?, version = version + 1 WHERE id = ? AND sold_quantity >= ?",
                quantity, quantity, inventoryId, quantity
            );
            jdbcTemplate.update(
                "UPDATE mall_stock_reservation SET status = 'RETURN_PENDING' WHERE id = ? AND status = 'SHIPPED'",
                item.get("reservationId")
            );
            insertInventoryLedger(principal, row, returnId, item, "RETURN_PENDING", 0, -quantity, quantity, 0,
                "退货寄回，进入待验货库存");
        }
    }

    private void settleInspectedReturn(
        TenantPrincipal principal, Map<String, Object> row, long returnId,
        String disposition, String reason
    ) {
        List<Map<String, Object>> items = returnInventoryLines(returnId);
        for (Map<String, Object> item : items) {
            int quantity = ((Number) item.get("quantity")).intValue();
            long inventoryId = number(item.get("inventoryId"));
            if ("RESTORE".equals(disposition)) {
                jdbcTemplate.update(
                    "UPDATE mall_sku_inventory SET return_pending_quantity = return_pending_quantity - ?, available_quantity = available_quantity + ?, version = version + 1 WHERE id = ? AND return_pending_quantity >= ?",
                    quantity, quantity, inventoryId, quantity
                );
                insertInventoryLedger(principal, row, returnId, item, "RESTORE", quantity, 0, -quantity, 0, reason);
            } else {
                jdbcTemplate.update(
                    "UPDATE mall_sku_inventory SET return_pending_quantity = return_pending_quantity - ?, damaged_quantity = damaged_quantity + ?, version = version + 1 WHERE id = ? AND return_pending_quantity >= ?",
                    quantity, quantity, inventoryId, quantity
                );
                insertInventoryLedger(principal, row, returnId, item, "DAMAGE", 0, 0, -quantity, quantity, reason);
            }
            jdbcTemplate.update(
                "UPDATE mall_stock_reservation SET status = ? WHERE id = ? AND status = 'RETURN_PENDING'",
                "RESTORE".equals(disposition) ? "RESTORED" : "DAMAGED", item.get("reservationId")
            );
            jdbcTemplate.update(
                "UPDATE mall_return_item SET stock_disposition = ? WHERE id = ?",
                disposition, item.get("returnItemId")
            );
        }
        long orderId = number(row.get("mallOrderId"));
        pointsService.restoreReference(systemPrincipal(principal, number(row.get("shopId"))),
            number(row.get("shopId")), "MALL_ORDER", orderId, "退货验货通过");
        Map<String, Object> order = jdbcTemplate.queryForMap(
            "SELECT cash_amount AS cashAmount, points_amount AS pointsAmount FROM mall_order WHERE id = ?",
            orderId
        );
        appendReturnAssets(principal, row, returnId, order, reason);
    }

    private void appendReturnAssets(
        TenantPrincipal principal, Map<String, Object> row, long returnId,
        Map<String, Object> order, String reason
    ) {
        long caseId = number(row.get("afterSaleCaseId"));
        BigDecimal cash = (BigDecimal) order.get("cashAmount");
        long points = number(order.get("pointsAmount"));
        if (cash.signum() > 0) insertAsset(principal, number(row.get("shopId")), caseId,
            "CASH", cash, "MALL_RETURN", returnId, "return:" + returnId + ":cash", reason);
        if (points > 0) insertAsset(principal, number(row.get("shopId")), caseId,
            "POINTS", BigDecimal.valueOf(points), "MALL_RETURN", returnId, "return:" + returnId + ":points", reason);
        BigDecimal freight = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(freight_amount), 0) FROM mall_sub_order WHERE mall_order_id = ?",
            BigDecimal.class, row.get("mallOrderId")
        );
        if (freight != null && freight.signum() > 0) insertAsset(principal, number(row.get("shopId")), caseId,
            "FREIGHT", freight, "MALL_RETURN", returnId, "return:" + returnId + ":freight", reason);
    }

    private void appendAssetDecision(
        TenantPrincipal principal, long shopId, long caseId, String resolution,
        BigDecimal amount, String reason, String key, Object evidence
    ) {
        String assetType = switch (resolution) {
            case "RESTORE_ENTITLEMENT" -> "ENTITLEMENT";
            case "COMPENSATION_COUPON" -> "COUPON";
            case "REDO_SERVICE" -> "ENTITLEMENT";
            default -> "STOCK";
        };
        String direction = "REJECT".equals(resolution) ? "REJECT" : "RESTORE";
        jdbcTemplate.update(
            "INSERT INTO after_sale_asset_ledger (tenant_id, shop_id, case_id, asset_type, direction, amount, reference_type, reference_id, business_key, reason, evidence_json, created_by) VALUES (?, ?, ?, ?, ?, ?, 'AFTER_SALE_CASE', ?, ?, ?, CAST(? AS JSON), ?)",
            principal.tenantId(), shopId, caseId, assetType, direction, amount,
            caseId, key, reason, json(evidence), principal.accountId()
        );
    }

    private void insertAsset(
        TenantPrincipal principal, long shopId, long caseId, String assetType,
        BigDecimal amount, String referenceType, long referenceId, String key, String reason
    ) {
        jdbcTemplate.update(
            "INSERT INTO after_sale_asset_ledger (tenant_id, shop_id, case_id, asset_type, direction, amount, reference_type, reference_id, business_key, reason, created_by) VALUES (?, ?, ?, ?, 'RETURN', ?, ?, ?, ?, ?, ?)",
            principal.tenantId(), shopId, caseId, assetType, amount, referenceType,
            referenceId, key, reason, principal.accountId()
        );
    }

    private List<Map<String, Object>> returnInventoryLines(long returnId) {
        return jdbcTemplate.queryForList(
            """
            SELECT ri.id AS returnItemId, ri.quantity,
                   msr.id AS reservationId, msr.inventory_id AS inventoryId
            FROM mall_return_item ri
            JOIN mall_stock_reservation msr ON msr.order_item_id = ri.mall_order_item_id
            WHERE ri.return_request_id = ?
            ORDER BY ri.id
            """,
            returnId
        );
    }

    private void insertInventoryLedger(
        TenantPrincipal principal, Map<String, Object> row, long returnId,
        Map<String, Object> item, String movement, int availableDelta,
        int soldDelta, int returnPendingDelta, int damagedDelta, String reason
    ) {
        Map<String, Object> balance = jdbcTemplate.queryForMap(
            "SELECT available_quantity AS available, reserved_quantity AS reserved, sold_quantity AS sold FROM mall_sku_inventory WHERE id = ?",
            item.get("inventoryId")
        );
        String suffix = movement.toLowerCase(Locale.ROOT) + ":" + item.get("returnItemId");
        jdbcTemplate.update(
            """
            INSERT INTO mall_inventory_ledger (
                tenant_id, shop_id, inventory_id, movement_type,
                available_delta, sold_delta, return_pending_delta, damaged_delta,
                available_after, reserved_after, sold_after, reference_type,
                reference_id, audit_no, business_key, reason, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MALL_RETURN', ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), row.get("shopId"), item.get("inventoryId"), movement,
            availableDelta, soldDelta, returnPendingDelta, damagedDelta,
            balance.get("available"), balance.get("reserved"), balance.get("sold"), returnId,
            "SC6-" + UUID.randomUUID(), "mall-return:" + returnId + ":" + suffix,
            reason, principal.accountId()
        );
    }

    private Map<String, Object> lockCase(TenantPrincipal principal, long shopId, long caseId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, member_id AS memberId, order_id AS orderId, status, version, entry_deadline_at AS entryDeadlineAt, reopen_count AS reopenCount FROM after_sale_case WHERE id = ? AND tenant_id = ? AND shop_id = ? FOR UPDATE",
            caseId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "售后工单不存在");
        return rows.getFirst();
    }

    private Map<String, Object> lockReturn(TenantPrincipal principal, long shopId, long returnId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, tenant_id AS tenantId, shop_id AS shopId, member_id AS memberId, mall_order_id AS mallOrderId, after_sale_case_id AS afterSaleCaseId, status, version FROM mall_return_request WHERE id = ? AND tenant_id = ? AND shop_id = ? FOR UPDATE",
            returnId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "退货申请不存在");
        return rows.getFirst();
    }

    private void requireReturn(Map<String, Object> row, int version, String status) {
        if (((Number) row.get("version")).intValue() != version || !status.equals(row.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "退货状态已变化，请刷新后重试");
        }
    }

    private Map<String, Object> returnDetail(long tenantId, long returnId) {
        Map<String, Object> result = new LinkedHashMap<>(jdbcTemplate.queryForMap(
            "SELECT id, shop_id AS shopId, member_id AS memberId, mall_order_id AS mallOrderId, after_sale_case_id AS afterSaleCaseId, reason_code AS reasonCode, reason_detail AS reasonDetail, status, return_tracking_no AS returnTrackingNo, inspection_result AS inspectionResult, inspection_reason AS inspectionReason, version, submitted_at AS submittedAt FROM mall_return_request WHERE id = ? AND tenant_id = ?",
            returnId, tenantId
        ));
        result.put("items", jdbcTemplate.queryForList(
            "SELECT id, mall_order_item_id AS orderItemId, quantity, stock_disposition AS stockDisposition FROM mall_return_item WHERE return_request_id = ? ORDER BY id",
            returnId
        ));
        return result;
    }

    private TenantPrincipal systemPrincipal(TenantPrincipal principal, long shopId) {
        return new TenantPrincipal(principal.accountId(), principal.tenantId(), shopId,
            "sc6-aftersale", List.of("SUPER_ADMIN"), Set.of(), Set.of(shopId), true);
    }

    private String enumValue(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
        return normalized;
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
        }
        return value.trim();
    }

    private String requiredKey(String value) {
        return required(value, "幂等键", 100);
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return required(value, "说明", max);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "证据格式不正确"); }
    }

    private LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private long generated(KeyHolder holder, String message) {
        Number key = holder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        return key.longValue();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private void log(
        TenantPrincipal principal, long shopId, long caseId, String action,
        String from, String to, String note, String key, String requestHash
    ) {
        jdbcTemplate.update(
            "INSERT INTO after_sale_case_log (tenant_id, shop_id, case_id, action, from_status, to_status, safe_note, idempotency_key, request_hash, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            principal.tenantId(), shopId, caseId, action, from, to, note, key, requestHash, principal.accountId()
        );
    }

    private void audit(TenantPrincipal principal, long shopId, long id, String action) {
        jdbcTemplate.update(
            "INSERT INTO audit_log (tenant_id, shop_id, account_id, action, entity_type, entity_id) VALUES (?, ?, ?, ?, 'SC6_AFTERSALE', ?)",
            principal.tenantId(), shopId, principal.accountId(), action, id
        );
    }

    public record ReturnLine(long orderItemId, int quantity) {
    }
}
