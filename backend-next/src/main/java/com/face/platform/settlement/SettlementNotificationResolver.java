package com.face.platform.settlement;

import com.face.platform.notification.NotificationEvent;
import com.face.platform.notification.NotificationEventDescriptorResolver;
import com.face.platform.notification.NotificationMessageDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SettlementNotificationResolver implements NotificationEventDescriptorResolver {

    private static final Set<String> EVENT_TYPES = Set.of(
        "CommissionSettlementCreated",
        "CommissionSettlementConfirmed",
        "CommissionSettlementPaid"
    );

    private final JdbcTemplate jdbcTemplate;

    public SettlementNotificationResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public List<NotificationMessageDraft> resolve(NotificationEvent event) {
        long batchId = Long.parseLong(event.aggregateId());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT DISTINCT b.shop_id AS shopId, b.settlement_no AS settlementNo,
                   a.id AS accountId
            FROM commission_settlement_batch b
            JOIN commission_settlement_item i
              ON i.batch_id = b.id
             AND i.tenant_id = b.tenant_id
            JOIN account a
              ON a.tenant_id = b.tenant_id
             AND a.staff_id = i.staff_id
             AND a.status = 'ACTIVE'
            WHERE b.id = ? AND b.tenant_id = ?
            ORDER BY a.id
            """,
            batchId,
            event.tenantId()
        );
        String title = switch (event.eventType()) {
            case "CommissionSettlementPaid" -> "提成结算已支付";
            case "CommissionSettlementConfirmed" -> "提成结算已确认";
            default -> "提成结算批次已建立";
        };
        List<NotificationMessageDraft> drafts = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            drafts.add(new NotificationMessageDraft(
                number(row.get("accountId")),
                number(row.get("shopId")),
                "SETTLEMENT",
                title,
                "结算单 " + row.get("settlementNo") + " 状态已更新",
                "/workbench?tab=commission"
            ));
        }
        return drafts;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
