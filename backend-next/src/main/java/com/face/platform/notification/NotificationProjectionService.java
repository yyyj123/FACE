package com.face.platform.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationProjectionService {

    private static final Set<String> CATEGORIES = Set.of(
        "APPROVAL", "AFTERSALE", "COMMISSION", "SETTLEMENT", "SYSTEM", "MARKETING"
    );

    private final JdbcTemplate jdbcTemplate;
    private final List<NotificationEventDescriptorResolver> resolvers;

    public NotificationProjectionService(
        JdbcTemplate jdbcTemplate,
        List<NotificationEventDescriptorResolver> resolvers
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.resolvers = List.copyOf(resolvers);
    }

    @Transactional
    public int projectPending() {
        Set<String> eventTypes = new LinkedHashSet<>();
        for (NotificationEventDescriptorResolver resolver : resolvers) {
            eventTypes.addAll(resolver.eventTypes());
        }
        if (eventTypes.isEmpty()) return 0;
        String placeholders = String.join(
            ",", Collections.nCopies(eventTypes.size(), "?")
        );
        List<Object> args = new ArrayList<>(eventTypes);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT oe.id AS outboxRowId, oe.event_id AS eventId,
                   oe.tenant_id AS tenantId, oe.shop_id AS shopId,
                   oe.aggregate_type AS aggregateType,
                   oe.aggregate_id AS aggregateId, oe.event_type AS eventType
            FROM outbox_event oe
            LEFT JOIN notification_projection_checkpoint cp
              ON cp.tenant_id = oe.tenant_id
             AND cp.event_id = oe.event_id
            WHERE oe.event_type IN (%s)
              AND (
                    cp.id IS NULL
                 OR (cp.status = 'FAILED' AND cp.next_retry_at <= CURRENT_TIMESTAMP(3))
              )
            ORDER BY oe.id
            LIMIT 50
            """.formatted(placeholders),
            args.toArray()
        );
        int projected = 0;
        for (Map<String, Object> row : rows) {
            NotificationEvent event = event(row);
            jdbcTemplate.update(
                """
                INSERT IGNORE INTO notification_projection_checkpoint (
                    tenant_id, event_id, outbox_event_row_id, event_type,
                    status, next_retry_at
                ) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP(3))
                """,
                event.tenantId(),
                event.eventId(),
                event.outboxRowId(),
                event.eventType()
            );
            try {
                List<NotificationMessageDraft> drafts = resolver(event).resolve(event);
                int count = 0;
                for (NotificationMessageDraft draft : drafts) {
                    requireSafeDraft(draft);
                    count += jdbcTemplate.update(
                        """
                        INSERT IGNORE INTO notification_message (
                            tenant_id, shop_id, recipient_account_id, event_id,
                            event_type, business_type, business_id, category,
                            channel, delivery_status, external_status,
                            title, safe_summary, action_path
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IN_APP', 'DELIVERED',
                                  'UNAVAILABLE', ?, ?, ?)
                        """,
                        event.tenantId(),
                        draft.shopId(),
                        draft.recipientAccountId(),
                        event.eventId(),
                        event.eventType(),
                        event.aggregateType(),
                        event.aggregateId(),
                        draft.category(),
                        draft.title(),
                        draft.safeSummary(),
                        draft.actionPath()
                    );
                }
                jdbcTemplate.update(
                    """
                    UPDATE notification_projection_checkpoint
                    SET status = 'PROJECTED', message_count = ?,
                        projected_at = CURRENT_TIMESTAMP(3),
                        last_error_code = NULL
                    WHERE tenant_id = ? AND event_id = ?
                    """,
                    count,
                    event.tenantId(),
                    event.eventId()
                );
                projected++;
            } catch (RuntimeException exception) {
                String code = exception.getClass().getSimpleName();
                if (code.length() > 80) code = code.substring(0, 80);
                jdbcTemplate.update(
                    """
                    UPDATE notification_projection_checkpoint
                    SET status = 'FAILED', retry_count = retry_count + 1,
                        next_retry_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE),
                        last_error_code = ?
                    WHERE tenant_id = ? AND event_id = ?
                    """,
                    code,
                    event.tenantId(),
                    event.eventId()
                );
            }
        }
        return projected;
    }

    private NotificationEventDescriptorResolver resolver(NotificationEvent event) {
        return resolvers.stream()
            .filter(candidate -> candidate.eventTypes().contains(event.eventType()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("通知事件缺少显式解析器"));
    }

    private NotificationEvent event(Map<String, Object> row) {
        return new NotificationEvent(
            number(row.get("outboxRowId")),
            row.get("eventId").toString(),
            number(row.get("tenantId")),
            nullableNumber(row.get("shopId")),
            row.get("aggregateType").toString(),
            row.get("aggregateId").toString(),
            row.get("eventType").toString()
        );
    }

    private void requireSafeDraft(NotificationMessageDraft draft) {
        if (draft.recipientAccountId() <= 0
            || !CATEGORIES.contains(draft.category())
            || draft.title() == null
            || draft.title().isBlank()
            || draft.title().length() > 120
            || draft.safeSummary() == null
            || draft.safeSummary().isBlank()
            || draft.safeSummary().length() > 500
            || (draft.actionPath() != null && draft.actionPath().length() > 255)) {
            throw new IllegalArgumentException("通知安全摘要不符合契约");
        }
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
