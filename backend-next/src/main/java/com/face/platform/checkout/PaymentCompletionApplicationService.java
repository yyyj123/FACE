package com.face.platform.checkout;

import com.face.platform.api.ApiException;
import com.face.platform.benefit.BenefitApplicationService;
import com.face.platform.booking.BookingTimeLockService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PaymentCompletionApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final BookingTimeLockService bookingTimeLockService;
    private final BenefitApplicationService benefitService;
    private final PointsApplicationService pointsService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public PaymentCompletionApplicationService(
        JdbcTemplate jdbcTemplate,
        BookingTimeLockService bookingTimeLockService,
        BenefitApplicationService benefitService,
        PointsApplicationService pointsService,
        OutboxEventService outboxEventService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.bookingTimeLockService = bookingTimeLockService;
        this.benefitService = benefitService;
        this.pointsService = pointsService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> complete(long paymentId) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_id AS paymentId,
                   appointment_id AS appointmentId, card_issued_count AS cardIssuedCount,
                   benefit_consumed_count AS benefitConsumedCount, status
            FROM checkout_fulfillment WHERE payment_id = ?
            """,
            paymentId
        );
        if (!existing.isEmpty()) return existing.getFirst();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pt.id AS paymentId, pt.tenant_id AS tenantId, pt.shop_id AS shopId,
                   pt.order_id AS orderId, pt.status AS paymentStatus,
                   pt.created_by AS createdBy, so.status AS orderStatus,
                   opd.booking_time_lock_id AS lockId,
                   opd.selection_snapshot_json AS selectionSnapshot
            FROM payment_transaction pt
            JOIN sales_order so ON so.id = pt.order_id
            JOIN order_pricing_decision opd ON opd.order_id = so.id
            WHERE pt.id = ? FOR UPDATE
            """,
            paymentId
        );
        if (rows.isEmpty()) return Map.of();
        Map<String, Object> row = rows.getFirst();
        if (!"SUCCESS".equals(row.get("paymentStatus"))
            || !"PAID".equals(row.get("orderStatus"))) {
            throw new ApiException(HttpStatus.CONFLICT, "支付或订单尚未成功，不能完成履约");
        }

        long tenantId = number(row.get("tenantId"));
        long shopId = number(row.get("shopId"));
        long orderId = number(row.get("orderId"));
        long accountId = number(row.get("createdBy"));
        TenantPrincipal principal = new TenantPrincipal(
            accountId, tenantId, shopId, "checkout-payment", List.of("MEMBER"),
            Set.of(), Set.of(shopId), false
        );

        Long appointmentId = null;
        if (row.get("lockId") != null) {
            Map<String, Object> lock = jdbcTemplate.queryForMap(
                """
                SELECT lock_token AS lockToken, terms_version AS termsVersion
                FROM booking_time_lock
                WHERE id = ? AND tenant_id = ? AND created_by = ?
                """,
                number(row.get("lockId")), tenantId, accountId
            );
            String note = selectionNote(row.get("selectionSnapshot"));
            Map<String, Object> appointment = bookingTimeLockService.confirmPaid(
                principal,
                lock.get("lockToken").toString(),
                ((Number) lock.get("termsVersion")).intValue(),
                note,
                orderId
            );
            appointmentId = number(appointment.get("appointmentId"));
            jdbcTemplate.update(
                "UPDATE sales_order SET appointment_id = ?, version = version + 1, updated_by = ? WHERE id = ? AND tenant_id = ?",
                appointmentId, accountId, orderId, tenantId
            );
        }

        int benefits = benefitService.completeOrderBenefits(principal, shopId, orderId);
        benefits += pointsService.consumeReference(principal, shopId, "SALES_ORDER", orderId);
        int cards = benefitService.issuePaidOrderCards(principal, shopId, orderId);
        pointsService.earnPaidSalesOrder(principal, shopId, orderId);
        String key = "CHECKOUT_FULFILLMENT:" + orderId;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        final Long finalAppointmentId = appointmentId;
        final int finalBenefits = benefits;
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO checkout_fulfillment (
                    tenant_id, shop_id, order_id, payment_id, booking_time_lock_id,
                    appointment_id, status, fulfillment_key, card_issued_count,
                    benefit_consumed_count
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tenantId);
            statement.setLong(2, shopId);
            statement.setLong(3, orderId);
            statement.setLong(4, paymentId);
            if (row.get("lockId") == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, number(row.get("lockId")));
            if (finalAppointmentId == null) statement.setNull(6, java.sql.Types.BIGINT);
            else statement.setLong(6, finalAppointmentId);
            statement.setString(7, key);
            statement.setInt(8, cards);
            statement.setInt(9, finalBenefits);
            return statement;
        }, keyHolder);
        Number fulfillmentId = keyHolder.getKey();
        if (fulfillmentId == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "支付履约记录创建失败");
        }
        outboxEventService.append(
            principal, shopId, "SALES_ORDER", String.valueOf(orderId),
            "CheckoutFulfilled", Map.of(
                "orderId", orderId,
                "paymentId", paymentId
            )
        );
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", fulfillmentId.longValue());
        result.put("orderId", orderId);
        result.put("paymentId", paymentId);
        result.put("appointmentId", appointmentId);
        result.put("cardIssuedCount", cards);
        result.put("benefitConsumedCount", benefits);
        result.put("status", "COMPLETED");
        return result;
    }

    @Transactional
    public void releaseFailed(long paymentId, String reason) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pt.tenant_id AS tenantId, pt.shop_id AS shopId, pt.order_id AS orderId,
                   pt.created_by AS createdBy, opd.booking_time_lock_id AS lockId
            FROM payment_transaction pt
            JOIN order_pricing_decision opd ON opd.order_id = pt.order_id
            WHERE pt.id = ? FOR UPDATE
            """,
            paymentId
        );
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.getFirst();
        long tenantId = number(row.get("tenantId"));
        long shopId = number(row.get("shopId"));
        long accountId = number(row.get("createdBy"));
        long orderId = number(row.get("orderId"));
        TenantPrincipal principal = new TenantPrincipal(
            accountId, tenantId, shopId, "checkout-payment", List.of("MEMBER"),
            Set.of(), Set.of(shopId), false
        );
        benefitService.releaseOrderBenefits(principal, shopId, orderId, reason);
        pointsService.releaseReference(principal, shopId, "SALES_ORDER", orderId, reason);
        if (row.get("lockId") != null) {
            jdbcTemplate.update(
                """
                UPDATE booking_time_lock
                SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP(3),
                    release_reason = ?, version = version + 1
                WHERE id = ? AND status = 'HELD'
                """,
                reason, number(row.get("lockId"))
            );
        }
        jdbcTemplate.update(
            "UPDATE sales_order SET status = 'VOID', version = version + 1, updated_by = ? WHERE id = ? AND tenant_id = ? AND status = 'UNPAID'",
            accountId, orderId, tenantId
        );
    }

    public List<Long> expiredPendingPaymentIds(int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT pt.id
            FROM payment_transaction pt
            JOIN order_pricing_decision opd ON opd.order_id = pt.order_id
            JOIN booking_time_lock btl ON btl.id = opd.booking_time_lock_id
            WHERE pt.status = 'PENDING' AND btl.status = 'HELD'
              AND btl.expires_at <= CURRENT_TIMESTAMP(3)
            ORDER BY pt.id LIMIT ?
            """,
            Long.class,
            limit
        );
    }

    @Transactional
    public boolean expirePending(long paymentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pt.status
            FROM payment_transaction pt
            JOIN order_pricing_decision opd ON opd.order_id = pt.order_id
            JOIN booking_time_lock btl ON btl.id = opd.booking_time_lock_id
            WHERE pt.id = ? AND btl.expires_at <= CURRENT_TIMESTAMP(3) FOR UPDATE
            """,
            paymentId
        );
        if (rows.isEmpty() || !"PENDING".equals(rows.getFirst().get("status"))) return false;
        int changed = jdbcTemplate.update(
            """
            UPDATE payment_transaction
            SET status = 'FAILED', channel_status = 'TIMEOUT', confirmed_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND status = 'PENDING'
            """,
            paymentId
        );
        if (changed == 1) releaseFailed(paymentId, "PAYMENT_TIMEOUT");
        return changed == 1;
    }

    private String selectionNote(Object snapshot) {
        if (snapshot == null) return null;
        try {
            JsonNode json = objectMapper.readTree(snapshot.toString());
            JsonNode value = json.get("member_note");
            return value == null || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText().trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
