package com.face.platform.approval;

import com.face.platform.notification.NotificationEvent;
import com.face.platform.notification.NotificationEventDescriptorResolver;
import com.face.platform.notification.NotificationMessageDraft;
import com.face.platform.security.TenantAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ApprovalNotificationResolver implements NotificationEventDescriptorResolver {

    private static final Set<String> EVENT_TYPES = Set.of(
        "ApprovalRequested", "ApprovalDecided"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public ApprovalNotificationResolver(
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
        long approvalId = Long.parseLong(event.aggregateId());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT ai.shop_id AS shopId, ai.approval_no AS approvalNo,
                   ai.requester_account_id AS requesterAccountId,
                   ai.status, aps.candidate_permission AS candidatePermission
            FROM approval_instance ai
            LEFT JOIN approval_step aps
              ON aps.approval_instance_id = ai.id
             AND aps.tenant_id = ai.tenant_id
             AND aps.step_no = 1
            WHERE ai.id = ? AND ai.tenant_id = ?
            """,
            approvalId,
            event.tenantId()
        );
        if (rows.isEmpty()) return List.of();
        Map<String, Object> row = rows.getFirst();
        long shopId = number(row.get("shopId"));
        long requester = number(row.get("requesterAccountId"));
        String approvalNo = row.get("approvalNo").toString();
        if ("ApprovalDecided".equals(event.eventType())) {
            return List.of(draft(
                requester,
                shopId,
                "审批结果已更新",
                "审批单 " + approvalNo + " 的状态已更新为" + statusLabel(row.get("status").toString())
            ));
        }

        List<NotificationMessageDraft> drafts = new ArrayList<>();
        drafts.add(draft(
            requester,
            shopId,
            "审批申请已提交",
            "审批单 " + approvalNo + " 已进入待处理队列"
        ));
        Object permission = row.get("candidatePermission");
        if (permission != null) {
            for (Long accountId : accessService.accountIdsWithShopPermission(
                event.tenantId(), shopId, permission.toString()
            )) {
                if (accountId != requester) {
                    drafts.add(draft(
                        accountId,
                        shopId,
                        "有新的审批待处理",
                        "审批单 " + approvalNo + " 等待具备相应权限的人员处理"
                    ));
                }
            }
        }
        return drafts;
    }

    private NotificationMessageDraft draft(
        long recipient,
        long shopId,
        String title,
        String summary
    ) {
        return new NotificationMessageDraft(
            recipient, shopId, "APPROVAL", title, summary, null
        );
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已拒绝";
            case "CANCELLED" -> "已取消";
            case "EXPIRED" -> "已过期";
            default -> "待处理";
        };
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
