package com.face.platform.notification;

import com.face.platform.marketing.MarketingDeliveryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarketingNotificationDeliveryAdapter implements MarketingDeliveryPort {

    private final JdbcTemplate jdbcTemplate;

    public MarketingNotificationDeliveryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void deliverInApp(Message message) {
        requireSafe(message);
        int inserted = jdbcTemplate.update(
            """
            INSERT IGNORE INTO notification_message (
                tenant_id, shop_id, recipient_account_id, event_id,
                event_type, business_type, business_id, category,
                channel, delivery_status, external_status,
                title, safe_summary, action_path
            ) VALUES (?, ?, ?, ?, 'MarketingCampaignDelivered',
                      'MARKETING_CAMPAIGN', ?, 'MARKETING',
                      'IN_APP', 'DELIVERED', 'NOT_REQUESTED', ?, ?, ?)
            """,
            message.tenantId(), message.shopId(), message.recipientAccountId(),
            message.eventId(), Long.toString(message.campaignId()),
            message.title(), message.safeSummary(), message.actionPath()
        );
        if (inserted != 1) {
            throw new IllegalStateException("营销站内通知重复或写入失败");
        }
    }

    private void requireSafe(Message message) {
        if (message.tenantId() <= 0 || message.shopId() <= 0 || message.campaignId() <= 0
            || message.recipientAccountId() <= 0 || message.eventId() == null
            || message.eventId().isBlank() || message.title() == null || message.title().isBlank()
            || message.title().length() > 120 || message.safeSummary() == null
            || message.safeSummary().isBlank() || message.safeSummary().length() > 500
            || (message.actionPath() != null && message.actionPath().length() > 255)) {
            throw new IllegalArgumentException("营销通知安全摘要不符合契约");
        }
    }
}
