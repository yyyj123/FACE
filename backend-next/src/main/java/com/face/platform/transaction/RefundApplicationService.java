package com.face.platform.transaction;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.packageaccount.MemberAccountApplicationService;
import com.face.platform.packageaccount.PackageAccountService;
import com.face.platform.payment.PaymentAdapterRegistry;
import com.face.platform.payment.PaymentChannelAdapter;
import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
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
public class RefundApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final MemberAccountApplicationService memberAccountService;
    private final PackageAccountService packageAccountService;
    private final OutboxEventService outboxEventService;
    private final PaymentAdapterRegistry paymentAdapterRegistry;
    private final PointsApplicationService pointsService;

    public RefundApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        MemberAccountApplicationService memberAccountService,
        PackageAccountService packageAccountService,
        OutboxEventService outboxEventService,
        PaymentAdapterRegistry paymentAdapterRegistry,
        PointsApplicationService pointsService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.memberAccountService = memberAccountService;
        this.packageAccountService = packageAccountService;
        this.outboxEventService = outboxEventService;
        this.paymentAdapterRegistry = paymentAdapterRegistry;
        this.pointsService = pointsService;
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long refundId
    ) {
        Map<String, Object> refund = refundResult(principal, refundId);
        if (principal.roles().contains("MEMBER")) {
            long memberId = accessService.requireMemberId(principal);
            if (number(refund.get("memberId")) != memberId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "退款记录不存在");
            }
        } else {
            accessService.requireShopPermission(principal, shopId, "order:view");
        }
        if (number(refund.get("shopId")) != shopId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "退款记录不存在");
        }
        return refund;
    }

    public List<Map<String, Object>> memberRefunds(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        if (principal.roles().contains("MEMBER")) {
            long selfMemberId = accessService.requireMemberId(principal);
            if (selfMemberId != memberId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "退款记录不存在");
            }
        } else {
            accessService.requireShopPermission(principal, shopId, "order:view");
        }
        return jdbcTemplate.queryForList(
            """
            SELECT rt.id, rt.shop_id AS shopId, rt.order_id AS orderId,
                   so.order_no AS orderNo, rt.payment_id AS paymentId,
                   pt.payment_method AS paymentMethod,
                   rt.refund_no AS refundNo, rt.amount, rt.reason,
                   rt.status, rt.version, rt.decision_note AS decisionNote,
                   rt.execution_mode AS executionMode,
                   rt.channel_status AS channelStatus,
                   rt.failure_code AS failureCode,
                   rt.reviewed_at AS reviewedAt, rt.refunded_at AS refundedAt,
                   rt.created_at AS createdAt
            FROM refund_transaction rt
            JOIN sales_order so
              ON so.id = rt.order_id
             AND so.tenant_id = rt.tenant_id
             AND so.shop_id = rt.shop_id
            JOIN payment_transaction pt
              ON pt.id = rt.payment_id
             AND pt.tenant_id = rt.tenant_id
             AND pt.shop_id = rt.shop_id
            WHERE rt.tenant_id = ? AND rt.shop_id = ? AND so.member_id = ?
            ORDER BY rt.created_at DESC, rt.id DESC
            LIMIT 100
            """,
            principal.tenantId(),
            shopId,
            memberId
        );
    }

    public Map<String, Object> paymentDetail(
        TenantPrincipal principal,
        long shopId,
        long paymentId
    ) {
        accessService.requireShopPermission(principal, shopId, "order:view");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pt.id, pt.order_id AS orderId, pt.payment_no AS paymentNo,
                   pt.payment_method AS paymentMethod, pt.amount,
                   pt.refunded_amount AS refundedAmount, pt.status,
                   pt.paid_at AS paidAt, so.member_id AS memberId,
                   so.order_no AS orderNo
            FROM payment_transaction pt
            JOIN sales_order so ON so.id = pt.order_id AND so.tenant_id = pt.tenant_id
            WHERE pt.id = ? AND pt.tenant_id = ? AND pt.shop_id = ?
            """,
            paymentId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "收款记录不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        BigDecimal reserved = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM refund_transaction
            WHERE tenant_id = ? AND payment_id = ?
              AND status IN ('PENDING', 'APPROVED', 'PROCESSING', 'FAILED')
            """,
            BigDecimal.class,
            principal.tenantId(),
            paymentId
        );
        result.put(
            "refundableAmount",
            RefundExecutionPolicy.refundableAmount(
                decimal(result.get("amount")),
                decimal(result.get("refundedAmount")),
                reserved == null ? BigDecimal.ZERO : reserved
            )
        );
        return result;
    }

    @Transactional
    public Map<String, Object> request(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        long paymentId,
        BigDecimal amount,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "refund:request");
        String key = required(idempotencyKey, "退款幂等键");
        String hash = requiredHash(requestHash);
        String safeReason = required(reason, "退款原因");
        Map<String, Object> replay = requestReplay(principal, key, hash, paymentId);
        if (replay != null) return replay;

        Map<String, Object> payment = lockPayment(
            principal, shopId, orderId, paymentId
        );
        if (!"SUCCESS".equals(payment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有成功收款可以申请退款");
        }
        BigDecimal requested = money(amount);
        BigDecimal reserved = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM refund_transaction
            WHERE tenant_id = ? AND payment_id = ?
              AND status IN ('PENDING', 'APPROVED', 'PROCESSING', 'FAILED')
            """,
            BigDecimal.class,
            principal.tenantId(),
            paymentId
        );
        try {
            RefundExecutionPolicy.requireReservable(
                decimal(payment.get("amount")),
                decimal(payment.get("refundedAmount")),
                reserved == null ? BigDecimal.ZERO : reserved,
                requested
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }

        String refundNo = businessNo("RF");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO refund_transaction (
                    tenant_id, shop_id, order_id, payment_id, refund_no,
                    amount, reason, idempotency_key, request_hash,
                    status, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, orderId);
            statement.setLong(4, paymentId);
            statement.setString(5, refundNo);
            statement.setBigDecimal(6, requested);
            statement.setString(7, safeReason);
            statement.setString(8, key);
            statement.setString(9, hash);
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "退款申请创建失败");
        }
        long refundId = generated.longValue();
        audit(principal, shopId, "REFUND_REQUEST", refundId);
        appendEvent(principal, shopId, refundId, "RefundRequested");
        return refundResult(principal, refundId);
    }

    @Transactional
    public Map<String, Object> decide(
        TenantPrincipal principal,
        long shopId,
        long refundId,
        int version,
        String action,
        String decisionNote
    ) {
        accessService.requireShopPermission(principal, shopId, "refund:approve");
        Map<String, Object> refund = lockRefund(principal, shopId, refundId);
        requireVersion(refund, version);
        if (principal.accountId() == number(refund.get("createdBy"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "退款申请人与审批人必须分离");
        }
        String normalized = required(action, "审批动作").toUpperCase(Locale.ROOT);
        String target = switch (normalized) {
            case "APPROVE", "APPROVED" -> "APPROVED";
            case "REJECT", "REJECTED" -> "REJECTED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的审批动作");
        };
        String note = trimToNull(decisionNote);
        if ("REJECTED".equals(target) && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "拒绝退款必须填写审核说明");
        }
        if (!RefundExecutionPolicy.canTransition(refund.get("status").toString(), target)) {
            throw new ApiException(HttpStatus.CONFLICT, "当前退款状态不能执行该审批");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE refund_transaction
            SET status = ?, decision_note = ?, approved_by = ?,
                reviewed_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'PENDING'
            """,
            target,
            note,
            principal.accountId(),
            refundId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "退款申请已被其他人处理");
        audit(
            principal,
            shopId,
            "APPROVED".equals(target) ? "REFUND_APPROVE" : "REFUND_REJECT",
            refundId
        );
        appendEvent(
            principal,
            shopId,
            refundId,
            "APPROVED".equals(target) ? "RefundApproved" : "RefundRejected"
        );
        return refundResult(principal, refundId);
    }

    @Transactional
    public Map<String, Object> execute(
        TenantPrincipal principal,
        long shopId,
        long refundId,
        int version,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "refund:execute");
        String key = required(idempotencyKey, "退款执行幂等键");
        String hash = requiredHash(requestHash);
        Map<String, Object> replay = executionReplay(principal, key, hash, refundId);
        if (replay != null) return replay;

        Map<String, Object> refund = lockRefund(principal, shopId, refundId);
        requireVersion(refund, version);
        String currentStatus = refund.get("status").toString();
        if (!RefundExecutionPolicy.canTransition(currentStatus, "PROCESSING")) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已审批或失败的退款可以执行");
        }
        long orderId = number(refund.get("orderId"));
        long paymentId = number(refund.get("paymentId"));
        Map<String, Object> payment = lockPayment(
            principal, shopId, orderId, paymentId
        );
        String paymentMethod = payment.get("paymentMethod").toString();
        boolean external = List.of("CARD", "WECHAT", "ALIPAY", "SANDBOX")
            .contains(paymentMethod);
        PaymentChannelAdapter externalAdapter = external
            ? paymentAdapterRegistry.requireConfigured(paymentMethod)
            : null;
        Map<String, Object> order = lockOrder(principal, shopId, orderId);
        BigDecimal refundAmount = decimal(refund.get("amount"));
        BigDecimal paymentAfter = decimal(payment.get("refundedAmount")).add(refundAmount);
        if (paymentAfter.compareTo(decimal(payment.get("amount"))) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "原收款可退金额已发生变化");
        }
        BigDecimal orderAfter = decimal(order.get("refundedAmount")).add(refundAmount);
        BigDecimal paidAmount = decimal(order.get("paidAmount"));
        if (orderAfter.compareTo(paidAmount) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "订单可退金额已发生变化");
        }
        String executionMode = external ? "EXTERNAL_ADAPTER" : "LOCAL_LEDGER";
        String processingChannelStatus = external ? "CHANNEL_PROCESSING" : "LOCAL_PROCESSING";
        int processing = jdbcTemplate.update(
            """
            UPDATE refund_transaction
            SET status = 'PROCESSING', execution_idempotency_key = ?,
                execution_request_hash = ?, execution_mode = ?,
                channel_status = ?, executed_by = ?,
                failure_code = NULL, failed_at = NULL, version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status IN ('APPROVED', 'FAILED')
            """,
            key,
            hash,
            executionMode,
            processingChannelStatus,
            principal.accountId(),
            refundId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(processing, "退款执行状态已变化，请刷新后重试");

        PaymentChannelAdapter.RefundResult externalResult = null;
        if (external) {
            externalResult = externalAdapter.refund(
                payment.get("paymentNo").toString(),
                payment.get("externalTransactionNo").toString(),
                refund.get("refundNo").toString(),
                refundAmount
            );
            if (!externalResult.successful()) {
                jdbcTemplate.update(
                    """
                    UPDATE refund_transaction
                    SET status = 'FAILED', channel_status = ?, failure_code = ?,
                        failed_at = CURRENT_TIMESTAMP(3), version = version + 1
                    WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'PROCESSING'
                    """,
                    externalResult.channelStatus(),
                    "CHANNEL_REJECTED",
                    refundId,
                    principal.tenantId(),
                    shopId
                );
                audit(principal, shopId, "REFUND_FAILED", refundId);
                return refundResult(principal, refundId);
            }
        }

        Integer packageLines = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM sales_order_item
            WHERE order_id = ? AND item_type = 'PACKAGE'
            """,
            Integer.class,
            orderId
        );
        if (packageLines != null && packageLines > 0) {
            if (orderAfter.compareTo(paidAmount) != 0) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "包含套餐的订单必须在全额退款时整体冲正未使用套餐"
                );
            }
            packageAccountService.cancelUnusedForRefund(
                principal, shopId, orderId, refundId, hash
            );
        }

        if ("BALANCE".equals(paymentMethod)) {
            memberAccountService.creditBalanceForRefund(
                principal,
                shopId,
                number(order.get("memberId")),
                refundAmount,
                refundId,
                orderId
            );
        }
        int paymentChanged = jdbcTemplate.update(
            """
            UPDATE payment_transaction
            SET refunded_amount = refunded_amount + ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND refunded_amount = ?
            """,
            refundAmount,
            paymentId,
            principal.tenantId(),
            shopId,
            decimal(payment.get("refundedAmount"))
        );
        requireChanged(paymentChanged, "原收款已被其他退款修改");
        String orderStatus = TransactionStatusPolicy.refundStatus(paidAmount, orderAfter);
        int orderChanged = jdbcTemplate.update(
            """
            UPDATE sales_order
            SET refunded_amount = ?, status = ?, version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            orderAfter,
            orderStatus,
            principal.accountId(),
            orderId,
            principal.tenantId(),
            shopId,
            number(order.get("version"))
        );
        requireChanged(orderChanged, "订单已被其他退款修改，请刷新后重试");
        String completedChannelStatus = external
            ? externalResult.channelStatus()
            : "LOCAL_CONFIRMED";
        String externalRefundNo = external
            ? externalResult.externalRefundNo()
            : null;
        int completed = jdbcTemplate.update(
            """
            UPDATE refund_transaction
            SET status = 'SUCCESS', channel_status = ?, external_refund_no = ?,
                refunded_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'PROCESSING' AND execution_idempotency_key = ?
            """,
            completedChannelStatus,
            externalRefundNo,
            refundId,
            principal.tenantId(),
            shopId,
            key
        );
        requireChanged(completed, "退款执行结果未能确认");
        if (orderAfter.compareTo(paidAmount) == 0) {
            pointsService.restoreReference(
                principal, shopId, "SALES_ORDER", orderId, "REFUND_COMPLETED"
            );
        }
        pointsService.clawbackEarnedForRefund(
            principal, shopId, orderId, refundId, refundAmount, paidAmount
        );
        audit(principal, shopId, "REFUND_SUCCESS", refundId);
        appendEvent(principal, shopId, refundId, "RefundCompleted");
        return refundResult(principal, refundId);
    }

    private Map<String, Object> requestReplay(
        TenantPrincipal principal,
        String key,
        String hash,
        long paymentId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, payment_id AS paymentId, request_hash AS requestHash
            FROM refund_transaction
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            key
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> existing = rows.getFirst();
        if (number(existing.get("paymentId")) != paymentId
            || !hash.equals(existing.get("requestHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "退款幂等键已用于不同请求");
        }
        return refundResult(principal, number(existing.get("id")));
    }

    private Map<String, Object> executionReplay(
        TenantPrincipal principal,
        String key,
        String hash,
        long refundId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, execution_request_hash AS requestHash
            FROM refund_transaction
            WHERE tenant_id = ? AND execution_idempotency_key = ?
            """,
            principal.tenantId(),
            key
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> existing = rows.getFirst();
        if (number(existing.get("id")) != refundId
            || !hash.equals(existing.get("requestHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "退款执行幂等键已用于不同请求");
        }
        return refundResult(principal, refundId);
    }

    private Map<String, Object> lockRefund(
        TenantPrincipal principal,
        long shopId,
        long refundId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, order_id AS orderId,
                   payment_id AS paymentId, refund_no AS refundNo, amount,
                   status, version, created_by AS createdBy
            FROM refund_transaction
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            refundId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "退款记录不存在");
        return rows.getFirst();
    }

    private Map<String, Object> lockPayment(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        long paymentId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_no AS paymentNo,
                   payment_method AS paymentMethod, external_transaction_no AS externalTransactionNo,
                   amount, refunded_amount AS refundedAmount, status
            FROM payment_transaction
            WHERE id = ? AND order_id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            paymentId,
            orderId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "原收款记录不存在");
        return rows.getFirst();
    }

    private Map<String, Object> lockOrder(
        TenantPrincipal principal,
        long shopId,
        long orderId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, paid_amount AS paidAmount,
                   refunded_amount AS refundedAmount, status, version
            FROM sales_order
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            orderId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        return rows.getFirst();
    }

    private Map<String, Object> refundResult(
        TenantPrincipal principal,
        long refundId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT rt.id, rt.shop_id AS shopId, rt.order_id AS orderId,
                   rt.payment_id AS paymentId, rt.refund_no AS refundNo,
                   rt.amount, rt.reason, rt.status, rt.version,
                   rt.decision_note AS decisionNote,
                   rt.execution_mode AS executionMode,
                   rt.external_refund_no AS externalRefundNo,
                   rt.channel_status AS channelStatus,
                   rt.failure_code AS failureCode,
                   rt.reviewed_at AS reviewedAt, rt.refunded_at AS refundedAt,
                   rt.created_by AS createdBy, rt.approved_by AS approvedBy,
                   rt.executed_by AS executedBy,
                   rt.created_at AS createdAt, so.member_id AS memberId
            FROM refund_transaction rt
            JOIN sales_order so ON so.id = rt.order_id AND so.tenant_id = rt.tenant_id
            WHERE rt.id = ? AND rt.tenant_id = ?
            """,
            refundId,
            principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "退款记录不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        long refundId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'REFUND_TRANSACTION', ?)
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            refundId
        );
    }

    private void appendEvent(
        TenantPrincipal principal,
        long shopId,
        long refundId,
        String eventType
    ) {
        outboxEventService.append(
            principal,
            shopId,
            "REFUND_TRANSACTION",
            Long.toString(refundId),
            eventType,
            Map.of("refundId", refundId)
        );
    }

    private void requireVersion(Map<String, Object> refund, int version) {
        if (number(refund.get("version")) != version) {
            throw new ApiException(HttpStatus.CONFLICT, "退款版本已变化，请刷新后重试");
        }
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private String requiredHash(String value) {
        String hash = required(value, "请求摘要");
        if (hash.length() != 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求摘要格式不正确");
        }
        return hash;
    }

    private String required(String value, String label) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return safe;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private BigDecimal money(BigDecimal value) {
        try {
            BigDecimal normalized = value.setScale(2);
            if (normalized.signum() <= 0) throw new ArithmeticException();
            return normalized;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "退款金额格式不正确");
        }
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
            ? decimal
            : new BigDecimal(value.toString());
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String businessNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 20).toUpperCase(Locale.ROOT);
    }
}
