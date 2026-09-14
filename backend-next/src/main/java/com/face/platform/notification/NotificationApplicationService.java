package com.face.platform.notification;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public NotificationApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        String status,
        int page,
        int pageSize
    ) {
        requireSelf(principal);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String normalized = status(status);
        StringBuilder where = new StringBuilder(
            " WHERE tenant_id = ? AND recipient_account_id = ?"
        );
        List<Object> args = new ArrayList<>(
            List.of(principal.tenantId(), principal.accountId())
        );
        if (!"ALL".equals(normalized)) {
            where.append(" AND status = ?");
            args.add(normalized);
        }
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, event_type AS eventType,
                   business_type AS businessType, business_id AS businessId,
                   category, channel, delivery_status AS deliveryStatus,
                   external_status AS externalStatus, title,
                   safe_summary AS safeSummary, action_path AS actionPath,
                   status, read_at AS readAt, version, created_at AS createdAt
            FROM notification_message
            %s
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """.formatted(where),
            pageArgs.toArray()
        );
        Number total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification_message" + where,
            Number.class,
            args.toArray()
        );
        return Map.of(
            "records", records,
            "total", total == null ? 0 : total.longValue(),
            "unreadCount", unreadCount(principal),
            "page", safePage,
            "pageSize", safeSize,
            "asOf", Instant.now().toString()
        );
    }

    @Transactional
    public Map<String, Object> markRead(
        TenantPrincipal principal,
        long notificationId,
        int version
    ) {
        requireSelf(principal);
        Map<String, Object> notification = requireMessage(principal, notificationId, true);
        String current = notification.get("status").toString();
        if ("READ".equals(current)) return notification;
        int actualVersion = ((Number) notification.get("version")).intValue();
        if (!NotificationPolicy.versionMatches(actualVersion, version)
            || !NotificationPolicy.canTransition(current, "READ")) {
            throw new ApiException(HttpStatus.CONFLICT, "通知状态或版本已变化");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE notification_message
            SET status = 'READ', read_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND recipient_account_id = ?
              AND status = 'UNREAD' AND version = ?
            """,
            notificationId,
            principal.tenantId(),
            principal.accountId(),
            version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "通知已被其他操作更新");
        }
        audit(principal, "NOTIFICATION_READ", notificationId);
        return requireMessage(principal, notificationId, false);
    }

    @Transactional
    public Map<String, Object> markAllRead(TenantPrincipal principal) {
        requireSelf(principal);
        Instant watermark = Instant.now();
        int changed = jdbcTemplate.update(
            """
            UPDATE notification_message
            SET status = 'READ', read_at = ?, version = version + 1
            WHERE tenant_id = ? AND recipient_account_id = ?
              AND status = 'UNREAD' AND created_at <= ?
            """,
            Timestamp.from(watermark),
            principal.tenantId(),
            principal.accountId(),
            Timestamp.from(watermark)
        );
        audit(principal, "NOTIFICATION_READ_ALL", principal.accountId());
        return Map.of(
            "changed", changed,
            "unreadCount", unreadCount(principal),
            "watermark", watermark.toString()
        );
    }

    public Map<String, Object> summary(TenantPrincipal principal) {
        requireSelf(principal);
        return Map.of("unreadCount", unreadCount(principal));
    }

    private Map<String, Object> requireMessage(
        TenantPrincipal principal,
        long notificationId,
        boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, event_type AS eventType,
                   business_type AS businessType, business_id AS businessId,
                   category, channel, delivery_status AS deliveryStatus,
                   external_status AS externalStatus, title,
                   safe_summary AS safeSummary, action_path AS actionPath,
                   status, read_at AS readAt, version, created_at AS createdAt
            FROM notification_message
            WHERE id = ? AND tenant_id = ? AND recipient_account_id = ?%s
            """.formatted(lock ? " FOR UPDATE" : ""),
            notificationId,
            principal.tenantId(),
            principal.accountId()
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        return new LinkedHashMap<>(rows.getFirst());
    }

    private long unreadCount(TenantPrincipal principal) {
        Number count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM notification_message
            WHERE tenant_id = ? AND recipient_account_id = ? AND status = 'UNREAD'
            """,
            Number.class,
            principal.tenantId(),
            principal.accountId()
        );
        return count == null ? 0 : count.longValue();
    }

    private void requireSelf(TenantPrincipal principal) {
        accessService.requirePermission(principal, "notification:view:self");
    }

    private String status(String value) {
        String normalized = value == null || value.isBlank()
            ? "ALL"
            : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "UNREAD", "READ").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "通知状态不正确");
        }
        return normalized;
    }

    private void audit(TenantPrincipal principal, String action, long entityId) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'NOTIFICATION', ?)
            """,
            principal.tenantId(),
            principal.homeShopId(),
            principal.accountId(),
            action,
            entityId
        );
    }
}
