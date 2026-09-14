package com.face.platform.commission;

import com.face.platform.notification.NotificationEvent;
import com.face.platform.notification.NotificationEventDescriptorResolver;
import com.face.platform.notification.NotificationMessageDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CommissionNotificationResolver implements NotificationEventDescriptorResolver {

    private static final Set<String> EVENT_TYPES = Set.of(
        "CommissionAccrued", "CommissionReversed", "CommissionAdjustmentApplied"
    );

    private final JdbcTemplate jdbcTemplate;

    public CommissionNotificationResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public List<NotificationMessageDraft> resolve(NotificationEvent event) {
        long entryId = Long.parseLong(event.aggregateId());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT ce.shop_id AS shopId, ce.entry_no AS entryNo, a.id AS accountId
            FROM commission_entry ce
            JOIN account a
              ON a.tenant_id = ce.tenant_id
             AND a.staff_id = ce.staff_id
             AND a.status = 'ACTIVE'
            WHERE ce.id = ? AND ce.tenant_id = ?
            """,
            entryId,
            event.tenantId()
        );
        if (rows.isEmpty()) return List.of();
        Map<String, Object> row = rows.getFirst();
        String title = switch (event.eventType()) {
            case "CommissionReversed" -> "提成记录发生冲正";
            case "CommissionAdjustmentApplied" -> "提成调整已生效";
            default -> "提成已入账";
        };
        return List.of(new NotificationMessageDraft(
            number(row.get("accountId")),
            number(row.get("shopId")),
            "COMMISSION",
            title,
            "提成记录 " + row.get("entryNo") + " 已更新，可在本人工作台查看",
            "/workbench?tab=commission"
        ));
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
