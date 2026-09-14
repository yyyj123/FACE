package com.face.platform.transaction;

import com.face.platform.api.ApiException;
import com.face.platform.checkout.PaymentCompletionApplicationService;
import com.face.platform.inventory.InventoryService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.payment.PaymentAdapterRegistry;
import com.face.platform.payment.PaymentChannelAdapter;
import com.face.platform.payment.PaymentChannelPolicy;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.store.MallApplicationService;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final TransactionService transactionService;
    private final InventoryService inventoryService;
    private final OutboxEventService outboxEventService;
    private final PaymentAdapterRegistry adapterRegistry;
    private final ObjectMapper objectMapper;
    private final PaymentCompletionApplicationService completionService;
    private final MallApplicationService mallService;

    public PaymentApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        TransactionService transactionService,
        InventoryService inventoryService,
        OutboxEventService outboxEventService,
        PaymentAdapterRegistry adapterRegistry,
        ObjectMapper objectMapper,
        PaymentCompletionApplicationService completionService,
        MallApplicationService mallService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.transactionService = transactionService;
        this.inventoryService = inventoryService;
        this.outboxEventService = outboxEventService;
        this.adapterRegistry = adapterRegistry;
        this.objectMapper = objectMapper;
        this.completionService = completionService;
        this.mallService = mallService;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        long orderId,
        PaymentRequest request
    ) {
        String method = normalize(request.paymentMethod());
        final String mode;
        try {
            mode = PaymentChannelPolicy.executionMode(method);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if ("LOCAL_LEDGER".equals(mode)) {
            return transactionService.pay(principal, orderId, request);
        }

        long shopId = accessService.requireShopPermission(
            principal,
            request.shopId(),
            "order:manage"
        );
        PaymentChannelAdapter adapter = adapterRegistry.requireConfigured(method);
        String requestHash = paymentRequestHash(orderId, request);
        Map<String, Object> replay = findByIdempotency(principal.tenantId(), request.idempotencyKey());
        if (replay != null) {
            if (number(replay.get("orderId")) != orderId
                || !requestHash.equals(replay.remove("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "收款幂等键已用于不同请求");
            }
            return replay;
        }

        Map<String, Object> order = lockOrder(principal.tenantId(), shopId, orderId);
        requireVersion(order, request.version());
        if (!TransactionStatusPolicy.canAcceptPayment(order.get("status").toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "当前订单状态不能收款");
        }
        BigDecimal amount = money(request.amount());
        BigDecimal outstanding = decimal(order.get("payableAmount"))
            .subtract(decimal(order.get("paidAmount")));
        if (amount.compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收款金额不能超过剩余应付金额");
        }

        String paymentNo = businessNo("PAY");
        PaymentChannelAdapter.PaymentInitiation initiation =
            adapter.initiate(paymentNo, amount, "CNY");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO payment_transaction (
                    tenant_id, shop_id, order_id, payment_no, payment_method,
                    channel_code, amount, refunded_amount, currency_code,
                    external_transaction_no, channel_status, channel_request_no,
                    idempotency_key, request_hash, status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'CNY', NULL, ?, ?, ?, ?, 'PENDING', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, orderId);
            statement.setString(4, paymentNo);
            statement.setString(5, method);
            statement.setString(6, method);
            statement.setBigDecimal(7, amount);
            statement.setString(8, initiation.channelStatus());
            statement.setString(9, initiation.channelRequestNo());
            statement.setString(10, request.idempotencyKey().trim());
            statement.setString(11, requestHash);
            statement.setLong(12, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "支付请求创建失败");
        }
        long paymentId = generated.longValue();
        audit(
            principal.tenantId(), shopId, principal.accountId(),
            "ORDER_PAYMENT_PENDING", paymentId, paymentNo, amount, method
        );
        outboxEventService.append(
            principal,
            shopId,
            "PAYMENT_TRANSACTION",
            String.valueOf(paymentId),
            "PaymentPending",
            Map.of("paymentId", paymentId, "orderId", orderId)
        );
        return paymentResult(paymentId);
    }

    @Transactional
    public Map<String, Object> handleCallback(
        String channelCode,
        long timestamp,
        String eventId,
        String signature,
        String rawBody
    ) {
        String channel = normalize(channelCode);
        String safeEventId = required(eventId, "通道事件号");
        PaymentChannelAdapter adapter = adapterRegistry.requireConfigured(channel);
        if (!adapter.verifyCallback(timestamp, signature, rawBody, Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "支付回调验签失败或已过期");
        }
        CallbackPayload callback = callbackPayload(rawBody);
        if (!safeEventId.equals(callback.eventId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "回调事件号不一致");
        }

        if (mallService.isMallPayment(channel, callback.paymentNo())) {
            return mallService.handleVerifiedPaymentCallback(
                channel, safeEventId, callback.paymentNo(), callback.status(),
                callback.amount(), callback.externalTransactionNo(), callback.channelStatus(),
                callback.failureCode(), SessionTokenCodec.sha256(rawBody == null ? "" : rawBody)
            );
        }

        Map<String, Object> payment = lockPaymentByNo(channel, callback.paymentNo());
        long paymentId = number(payment.get("id"));
        String payloadHash = SessionTokenCodec.sha256(rawBody == null ? "" : rawBody);
        List<Map<String, Object>> priorEvents = jdbcTemplate.queryForList(
            """
            SELECT payload_hash AS payloadHash
            FROM payment_callback_event
            WHERE channel_code = ? AND channel_event_id = ?
            """,
            channel,
            safeEventId
        );
        if (!priorEvents.isEmpty()) {
            if (!payloadHash.equals(priorEvents.getFirst().get("payloadHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "同一回调事件号载荷不一致");
            }
            return paymentResult(paymentId);
        }
        jdbcTemplate.update(
            """
            INSERT INTO payment_callback_event (
                tenant_id, shop_id, payment_id, channel_code, channel_event_id,
                payload_hash, signature_valid, processing_status
            ) VALUES (?, ?, ?, ?, ?, ?, 1, 'VERIFIED')
            """,
            number(payment.get("tenantId")),
            number(payment.get("shopId")),
            paymentId,
            channel,
            safeEventId,
            payloadHash
        );

        String target;
        try {
            target = PaymentChannelPolicy.callbackTarget(
                payment.get("status").toString(),
                callback.status()
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
        if (target.equals(payment.get("status"))) {
            markEvent(channel, safeEventId, "IGNORED", null);
            return paymentResult(paymentId);
        }
        if ("FAILED".equals(target)) {
            jdbcTemplate.update(
                """
                UPDATE payment_transaction
                SET status = 'FAILED', channel_status = ?, channel_event_id = ?,
                    confirmed_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status = 'PENDING'
                """,
                callback.channelStatus(),
                safeEventId,
                paymentId
            );
            markEvent(channel, safeEventId, "PROCESSED", callback.failureCode());
            completionService.releaseFailed(paymentId, "PAYMENT_FAILED");
            return paymentResult(paymentId);
        }

        BigDecimal callbackAmount = money(callback.amount());
        if (callbackAmount.compareTo(decimal(payment.get("amount"))) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "回调金额与支付请求不一致");
        }
        String externalTransactionNo = required(
            callback.externalTransactionNo(),
            "通道交易号"
        );
        long tenantId = number(payment.get("tenantId"));
        long shopId = number(payment.get("shopId"));
        long orderId = number(payment.get("orderId"));
        Map<String, Object> order = lockOrder(tenantId, shopId, orderId);
        BigDecimal newPaid = decimal(order.get("paidAmount")).add(callbackAmount);
        if (newPaid.compareTo(decimal(order.get("payableAmount"))) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "通道支付会导致订单超收");
        }
        String orderStatus = TransactionStatusPolicy.paymentStatus(
            decimal(order.get("payableAmount")),
            newPaid
        );
        int paymentChanged = jdbcTemplate.update(
            """
            UPDATE payment_transaction
            SET status = 'SUCCESS', external_transaction_no = ?,
                channel_status = ?, channel_event_id = ?,
                paid_at = CURRENT_TIMESTAMP(3), confirmed_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND status = 'PENDING'
            """,
            externalTransactionNo,
            callback.channelStatus(),
            safeEventId,
            paymentId
        );
        requireChanged(paymentChanged, "支付状态已被其他回调处理");
        int orderChanged = jdbcTemplate.update(
            """
            UPDATE sales_order
            SET paid_amount = ?, payment_method = ?, status = ?,
                paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP(3) ELSE paid_at END,
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            newPaid,
            channel,
            orderStatus,
            orderStatus,
            number(payment.get("createdBy")),
            orderId,
            tenantId,
            shopId,
            number(order.get("version"))
        );
        requireChanged(orderChanged, "订单已被其他操作修改");
        TenantPrincipal systemPrincipal = systemPrincipal(payment);
        if ("PAID".equals(orderStatus)) {
            inventoryService.deductPaidOrder(systemPrincipal, shopId, orderId);
        }
        audit(
            tenantId, shopId, systemPrincipal.accountId(),
            "ORDER_PAYMENT_SUCCESS", paymentId,
            payment.get("paymentNo").toString(), callbackAmount, channel
        );
        outboxEventService.append(
            systemPrincipal,
            shopId,
            "PAYMENT_TRANSACTION",
            String.valueOf(paymentId),
            "PaymentSucceeded",
            Map.of("paymentId", paymentId, "orderId", orderId)
        );
        markEvent(channel, safeEventId, "PROCESSED", null);
        if ("PAID".equals(orderStatus)) {
            completionService.complete(paymentId);
        }
        return paymentResult(paymentId);
    }

    private CallbackPayload callbackPayload(String rawBody) {
        try {
            JsonNode json = objectMapper.readTree(rawBody);
            return new CallbackPayload(
                text(json, "event_id"),
                text(json, "payment_no"),
                text(json, "status"),
                new BigDecimal(text(json, "amount")),
                text(json, "external_transaction_no"),
                optionalText(json, "channel_status", "CONFIRMED"),
                optionalText(json, "failure_code", null)
            );
        } catch (JacksonException | NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "支付回调载荷格式不正确");
        }
    }

    private String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "支付回调缺少字段 " + field);
        }
        return value.asText().trim();
    }

    private String optionalText(JsonNode json, String field, String fallback) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
            ? fallback
            : value.asText().trim();
    }

    private Map<String, Object> lockPaymentByNo(String channel, String paymentNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, tenant_id AS tenantId, shop_id AS shopId, order_id AS orderId,
                   payment_no AS paymentNo, payment_method AS paymentMethod,
                   channel_code AS channelCode, amount, status, created_by AS createdBy
            FROM payment_transaction
            WHERE channel_code = ? AND payment_no = ?
            FOR UPDATE
            """,
            channel,
            paymentNo
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "支付请求不存在");
        return rows.getFirst();
    }

    private Map<String, Object> lockOrder(long tenantId, long shopId, long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, payable_amount AS payableAmount,
                   paid_amount AS paidAmount, payment_method AS paymentMethod,
                   status, version
            FROM sales_order
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            orderId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        return rows.getFirst();
    }

    private Map<String, Object> findByIdempotency(long tenantId, String key) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_no AS paymentNo,
                   payment_method AS paymentMethod, channel_code AS channelCode,
                   amount, refunded_amount AS refundedAmount,
                   request_hash AS requestHash, status,
                   channel_status AS channelStatus,
                   channel_request_no AS channelRequestNo, paid_at AS paidAt
            FROM payment_transaction
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            tenantId,
            required(key, "支付幂等键")
        );
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
    }

    private Map<String, Object> paymentResult(long paymentId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT id, order_id AS orderId, payment_no AS paymentNo,
                       payment_method AS paymentMethod, channel_code AS channelCode,
                       amount, refunded_amount AS refundedAmount, status,
                       channel_status AS channelStatus,
                       channel_request_no AS channelRequestNo,
                       external_transaction_no AS externalTransactionNo,
                       paid_at AS paidAt, confirmed_at AS confirmedAt
                FROM payment_transaction
                WHERE id = ?
                """,
                paymentId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "支付请求不存在");
        }
    }

    private void markEvent(String channel, String eventId, String status, String errorCode) {
        jdbcTemplate.update(
            """
            UPDATE payment_callback_event
            SET processing_status = ?, error_code = ?, processed_at = CURRENT_TIMESTAMP(3)
            WHERE channel_code = ? AND channel_event_id = ?
            """,
            status,
            errorCode,
            channel,
            eventId
        );
    }

    private TenantPrincipal systemPrincipal(Map<String, Object> payment) {
        long accountId = number(payment.get("createdBy"));
        long tenantId = number(payment.get("tenantId"));
        long shopId = number(payment.get("shopId"));
        return new TenantPrincipal(
            accountId,
            tenantId,
            shopId,
            "payment-channel",
            List.of("SYSTEM"),
            Set.of(),
            Set.of(shopId),
            false
        );
    }

    private void audit(
        long tenantId,
        long shopId,
        long accountId,
        String action,
        long entityId,
        String businessNo,
        BigDecimal amount,
        String note
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id,
                after_data, metadata
            ) VALUES (?, ?, ?, ?, 'PAYMENT_TRANSACTION', ?,
                      JSON_OBJECT('businessNo', ?, 'amount', ?),
                      JSON_OBJECT('note', ?))
            """,
            tenantId,
            shopId,
            accountId,
            action,
            entityId,
            businessNo,
            amount,
            note
        );
    }

    private String paymentRequestHash(long orderId, PaymentRequest request) {
        return SessionTokenCodec.sha256(
            orderId + "|" + request.shopId() + "|" + normalize(request.paymentMethod())
                + "|" + money(request.amount()) + "|" + request.version()
        );
    }

    private void requireVersion(Map<String, Object> order, int expected) {
        if (number(order.get("version")) != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "订单版本已变化，请刷新后重试");
        }
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "金额不能为空");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "金额必须大于零");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "金额最多两位小数");
        }
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
            ? decimal
            : new BigDecimal(value.toString());
    }

    private long number(Object value) {
        return value instanceof Number number
            ? number.longValue()
            : Long.parseLong(value.toString());
    }

    private String normalize(String value) {
        return required(value, "支付通道").toUpperCase(Locale.ROOT);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能为空");
        }
        return value.trim();
    }

    private String businessNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private record CallbackPayload(
        String eventId,
        String paymentNo,
        String status,
        BigDecimal amount,
        String externalTransactionNo,
        String channelStatus,
        String failureCode
    ) {
    }
}
