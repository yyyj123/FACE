package com.face.platform.commission;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CommissionEventProjectionService {

    private static final Set<String> EVENT_TYPES = Set.of(
        "PaymentSucceeded",
        "ServiceRecordCompleted",
        "RefundCompleted"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CommissionEntryApplicationService entryService;

    public CommissionEventProjectionService(
        JdbcTemplate jdbcTemplate,
        CommissionEntryApplicationService entryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.entryService = entryService;
    }

    public List<Long> pendingEventIds(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return jdbcTemplate.queryForList(
            """
            SELECT oe.id
            FROM outbox_event oe
            LEFT JOIN commission_event_projection cep
              ON cep.tenant_id = oe.tenant_id
             AND cep.outbox_event_id = oe.id
            WHERE oe.event_type IN (
              'PaymentSucceeded',
              'ServiceRecordCompleted',
              'RefundCompleted'
            )
              AND (
                cep.id IS NULL
                OR (cep.status = 'FAILED' AND cep.attempt_count < 10)
              )
            ORDER BY oe.id
            LIMIT ?
            """,
            Long.class,
            safeLimit
        );
    }

    @Transactional
    public String processEvent(long outboxEventId) {
        Map<String, Object> event = lockEvent(outboxEventId);
        String eventType = text(event.get("eventType"));
        if (!EVENT_TYPES.contains(eventType)) return "IGNORED_EVENT";
        Integer completed = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM commission_event_projection
            WHERE tenant_id = ? AND outbox_event_id = ? AND status = 'COMPLETED'
            """,
            Integer.class,
            number(event.get("tenantId")),
            outboxEventId
        );
        if (completed != null && completed > 0) return "REPLAY";
        jdbcTemplate.update(
            """
            INSERT INTO commission_event_projection (
                tenant_id, outbox_event_id, event_id, event_type,
                status, attempt_count, last_error_code
            ) VALUES (?, ?, ?, ?, 'PENDING', 1, NULL)
            ON DUPLICATE KEY UPDATE
                status = 'PENDING',
                attempt_count = attempt_count + 1,
                last_error_code = NULL
            """,
            number(event.get("tenantId")),
            outboxEventId,
            event.get("eventId"),
            eventType
        );
        TenantPrincipal principal = systemPrincipal(event);
        String resultCode = switch (eventType) {
            case "PaymentSucceeded" -> entryService.accrueForOrderEvent(
                principal,
                requiredNumber(event.get("orderId"), "支付成功事件缺少订单标识"),
                outboxEventId
            );
            case "ServiceRecordCompleted" -> {
                long serviceRecordId = requiredNumber(
                    event.get("serviceRecordId"),
                    "服务完成事件缺少服务记录标识"
                );
                Long orderId = jdbcTemplate.query(
                    """
                    SELECT id FROM sales_order
                    WHERE tenant_id = ? AND service_record_id = ?
                    LIMIT 1
                    """,
                    resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                    principal.tenantId(),
                    serviceRecordId
                );
                yield orderId == null
                    ? "NO_ORDER"
                    : entryService.accrueForOrderEvent(
                        principal,
                        orderId,
                        outboxEventId
                    );
            }
            case "RefundCompleted" -> entryService.reverseForRefundEvent(
                principal,
                requiredNumber(event.get("refundId"), "退款完成事件缺少退款标识"),
                outboxEventId
            );
            default -> "IGNORED_EVENT";
        };
        jdbcTemplate.update(
            """
            UPDATE commission_event_projection
            SET status = 'COMPLETED', result_code = ?, last_error_code = NULL,
                processed_at = CURRENT_TIMESTAMP(3)
            WHERE tenant_id = ? AND outbox_event_id = ?
            """,
            resultCode,
            principal.tenantId(),
            outboxEventId
        );
        return resultCode;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(long outboxEventId, Throwable failure) {
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
            """
            SELECT tenant_id AS tenantId, event_id AS eventId, event_type AS eventType
            FROM outbox_event WHERE id = ?
            """,
            outboxEventId
        );
        if (events.isEmpty()) return;
        Map<String, Object> event = events.getFirst();
        jdbcTemplate.update(
            """
            INSERT INTO commission_event_projection (
                tenant_id, outbox_event_id, event_id, event_type,
                status, attempt_count, last_error_code
            ) VALUES (?, ?, ?, ?, 'FAILED', 1, ?)
            ON DUPLICATE KEY UPDATE
                status = 'FAILED',
                attempt_count = attempt_count + 1,
                last_error_code = VALUES(last_error_code)
            """,
            number(event.get("tenantId")),
            outboxEventId,
            event.get("eventId"),
            event.get("eventType"),
            safeErrorCode(failure)
        );
    }

    private Map<String, Object> lockEvent(long outboxEventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT oe.id, oe.event_id AS eventId, oe.tenant_id AS tenantId,
                   oe.shop_id AS shopId, oe.event_type AS eventType,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(oe.payload, '$.paymentId'))
                     AS UNSIGNED) AS paymentId,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(oe.payload, '$.orderId'))
                     AS UNSIGNED) AS orderId,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(oe.payload, '$.serviceRecordId'))
                     AS UNSIGNED) AS serviceRecordId,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(oe.payload, '$.refundId'))
                     AS UNSIGNED) AS refundId
            FROM outbox_event oe
            WHERE oe.id = ?
            FOR UPDATE
            """,
            outboxEventId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "提成投影事件不存在");
        }
        return rows.getFirst();
    }

    private TenantPrincipal systemPrincipal(Map<String, Object> event) {
        long tenantId = number(event.get("tenantId"));
        long shopId = number(event.get("shopId"));
        Long actorId = switch (text(event.get("eventType"))) {
            case "PaymentSucceeded" -> accountId(
                "SELECT created_by FROM payment_transaction WHERE id = ? AND tenant_id = ?",
                event.get("paymentId"),
                tenantId
            );
            case "ServiceRecordCompleted" -> accountId(
                """
                SELECT COALESCE(updated_by, created_by)
                FROM service_record WHERE id = ? AND tenant_id = ?
                """,
                event.get("serviceRecordId"),
                tenantId
            );
            case "RefundCompleted" -> accountId(
                "SELECT executed_by FROM refund_transaction WHERE id = ? AND tenant_id = ?",
                event.get("refundId"),
                tenantId
            );
            default -> null;
        };
        if (actorId == null) {
            actorId = jdbcTemplate.query(
                """
                SELECT a.id
                FROM account a
                JOIN account_shop_role ar
                  ON ar.account_id = a.id AND ar.tenant_id = a.tenant_id
                JOIN role_definition r ON r.id = ar.role_id
                WHERE a.tenant_id = ? AND ar.shop_id = ?
                  AND r.role_code = 'OWNER' AND a.status = 'ACTIVE'
                ORDER BY a.id LIMIT 1
                """,
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                tenantId,
                shopId
            );
        }
        if (actorId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "提成投影缺少有效系统执行账号");
        }
        return new TenantPrincipal(
            actorId,
            tenantId,
            shopId,
            "commission-projector",
            List.of("SYSTEM"),
            Set.of(),
            Set.of(shopId),
            false
        );
    }

    private Long accountId(String sql, Object entityId, long tenantId) {
        if (entityId == null) return null;
        return jdbcTemplate.query(
            sql,
            resultSet -> {
                if (!resultSet.next()) return null;
                long value = resultSet.getLong(1);
                return resultSet.wasNull() ? null : value;
            },
            entityId,
            tenantId
        );
    }

    private long requiredNumber(Object value, String message) {
        if (value == null) throw new ApiException(HttpStatus.CONFLICT, message);
        return number(value);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String safeErrorCode(Throwable failure) {
        String code = failure == null ? "UNKNOWN" : failure.getClass().getSimpleName();
        return code.length() <= 80 ? code : code.substring(0, 80);
    }
}

