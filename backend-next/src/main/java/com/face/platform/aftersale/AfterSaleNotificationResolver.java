package com.face.platform.aftersale;

import com.face.platform.notification.NotificationEvent;
import com.face.platform.notification.NotificationEventDescriptorResolver;
import com.face.platform.notification.NotificationMessageDraft;
import com.face.platform.security.TenantAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AfterSaleNotificationResolver implements NotificationEventDescriptorResolver {

    private static final Set<String> EVENT_TYPES = Set.of(
        "AfterSaleCaseCreated",
        "AfterSaleStatusChanged",
        "AfterSaleCaseReopened",
        "AfterSaleRefundRequested"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public AfterSaleNotificationResolver(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    @Override
    public Set<String> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public List<NotificationMessageDraft> resolve(NotificationEvent event) {
        long caseId = Long.parseLong(event.aggregateId());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT c.shop_id AS shopId, c.case_no AS caseNo, c.status,
                   c.created_by AS createdBy,
                   c.assignee_account_id AS assigneeAccountId,
                   ma.id AS memberAccountId
            FROM after_sale_case c
            LEFT JOIN account ma
              ON ma.tenant_id = c.tenant_id
             AND ma.member_id = c.member_id
             AND ma.status = 'ACTIVE'
            WHERE c.id = ? AND c.tenant_id = ?
            """,
            caseId,
            event.tenantId()
        );
        if (rows.isEmpty()) return List.of();
        Map<String, Object> row = rows.getFirst();
        long shopId = number(row.get("shopId"));
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        recipients.add(number(row.get("createdBy")));
        nullableNumber(row.get("assigneeAccountId"), recipients);
        nullableNumber(row.get("memberAccountId"), recipients);
        recipients.addAll(accessService.accountIdsWithShopPermission(
            event.tenantId(), shopId, "aftersale:manage"
        ));

        String title = switch (event.eventType()) {
            case "AfterSaleCaseCreated" -> "新售后工单已创建";
            case "AfterSaleCaseReopened" -> "售后工单已重开";
            case "AfterSaleRefundRequested" -> "售后退款申请已提交";
            default -> "售后工单进度已更新";
        };
        String summary = "工单 " + row.get("caseNo")
            + " 当前状态为" + statusLabel(row.get("status").toString());
        List<NotificationMessageDraft> drafts = new ArrayList<>();
        for (Long recipient : recipients) {
            drafts.add(new NotificationMessageDraft(
                recipient, shopId, "AFTERSALE", title, summary, null
            ));
        }
        return drafts;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "待受理";
            case "TRIAGED" -> "已分派";
            case "PROCESSING" -> "处理中";
            case "WAITING_CUSTOMER" -> "等待顾客";
            case "RESOLVED" -> "已解决";
            case "CLOSED" -> "已关闭";
            case "REJECTED" -> "已驳回";
            case "REOPENED" -> "已重开";
            default -> "已更新";
        };
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private void nullableNumber(Object value, Set<Long> target) {
        if (value instanceof Number number) target.add(number.longValue());
    }
}
