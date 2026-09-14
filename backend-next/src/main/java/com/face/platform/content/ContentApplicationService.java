package com.face.platform.content;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ContentApplicationService {

    private static final Set<String> TYPES = Set.of(
        "BANNER", "FEATURED_SERVICE", "FEATURED_PACKAGE", "ACTIVITY",
        "POINTS_MALL", "ANNOUNCEMENT", "SHOP_INTRO", "CONTACT"
    );
    private static final Set<String> TARGET_TYPES = Set.of("NONE", "SERVICE", "URL");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final ShopContextService shopContextService;
    private final Clock clock;

    public ContentApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        ShopContextService shopContextService,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.shopContextService = shopContextService;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> publicHome() {
        var shop = shopContextService.requirePublicShop(null);
        publishDue(shop.tenantId(), shop.shopId());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, content_type AS contentType, title, summary,
                   JSON_UNQUOTE(JSON_EXTRACT(body_json, '$.text')) AS body,
                   image_url AS imageUrl, target_type AS targetType,
                   target_value AS targetValue, sort_order AS sortOrder,
                   published_at AS publishedAt
            FROM banner
            WHERE tenant_id = ? AND shop_id = ? AND status = 'PUBLISHED'
              AND (start_at IS NULL OR start_at <= CURRENT_TIMESTAMP(3))
              AND (end_at IS NULL OR end_at >= CURRENT_TIMESTAMP(3))
            ORDER BY content_type, sort_order, id
            """,
            shop.tenantId(), shop.shopId()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shop", Map.of(
            "id", shop.shopId(),
            "name", shop.name(),
            "phone", shop.phone() == null ? "" : shop.phone(),
            "address", shop.address() == null ? "" : shop.address(),
            "business_hours", shop.businessHours() == null ? "" : shop.businessHours()
        ));
        result.put("content", rows);
        return result;
    }

    public List<Map<String, Object>> list(TenantPrincipal principal) {
        tenantAccessService.requireManagementPermission(principal, "content:view");
        var shop = shopContextService.requireTenantShop(principal.tenantId(), null);
        return jdbcTemplate.queryForList(
            """
            SELECT id, content_type AS contentType, title, summary,
                   JSON_UNQUOTE(JSON_EXTRACT(body_json, '$.text')) AS body,
                   image_url AS imageUrl, target_type AS targetType,
                   target_value AS targetValue, sort_order AS sortOrder,
                   status, version, scheduled_at AS scheduledAt,
                   published_at AS publishedAt, offline_at AS offlineAt,
                   updated_at AS updatedAt
            FROM banner
            WHERE tenant_id = ? AND shop_id = ?
            ORDER BY content_type, sort_order, id
            """,
            principal.tenantId(), shop.shopId()
        );
    }

    @Transactional
    public Map<String, Object> create(TenantPrincipal principal, ContentDraft draft) {
        tenantAccessService.requireManagementPermission(principal, "content:manage");
        var shop = shopContextService.requireTenantShop(principal.tenantId(), null);
        ValidatedDraft value = validate(draft);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO banner (
                    tenant_id, shop_id, content_type, title, summary, body_json,
                    image_url, target_type, target_value, sort_order, status, updated_by
                ) VALUES (?, ?, ?, ?, ?, JSON_OBJECT('text', ?), ?, ?, ?, ?, 'DRAFT', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shop.shopId());
            statement.setString(3, value.type());
            statement.setString(4, value.title());
            statement.setString(5, value.summary());
            statement.setString(6, value.body());
            statement.setString(7, value.imageUrl());
            statement.setString(8, value.targetType());
            statement.setString(9, value.targetValue());
            statement.setInt(10, value.sortOrder());
            statement.setLong(11, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "内容编号生成失败");
        long id = key.longValue();
        audit(principal, shop.shopId(), "CONTENT_CREATE", id, "DRAFT");
        return Map.of("id", id, "status", "DRAFT", "version", 1);
    }

    @Transactional
    public Map<String, Object> update(
        TenantPrincipal principal,
        long contentId,
        int expectedVersion,
        ContentDraft draft
    ) {
        tenantAccessService.requireManagementPermission(principal, "content:manage");
        var shop = shopContextService.requireTenantShop(principal.tenantId(), null);
        ValidatedDraft value = validate(draft);
        int changed = jdbcTemplate.update(
            """
            UPDATE banner
            SET content_type = ?, title = ?, summary = ?,
                body_json = JSON_OBJECT('text', ?), image_url = ?,
                target_type = ?, target_value = ?, sort_order = ?,
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
              AND status IN ('DRAFT', 'OFFLINE')
            """,
            value.type(), value.title(), value.summary(), value.body(), value.imageUrl(),
            value.targetType(), value.targetValue(), value.sortOrder(), principal.accountId(),
            contentId, principal.tenantId(), shop.shopId(), expectedVersion
        );
        if (changed != 1) throw versionConflict();
        audit(principal, shop.shopId(), "CONTENT_UPDATE", contentId, null);
        return Map.of("id", contentId, "version", expectedVersion + 1);
    }

    @Transactional
    public Map<String, Object> changeStatus(
        TenantPrincipal principal,
        long contentId,
        int expectedVersion,
        String targetStatus,
        Instant scheduledAt
    ) {
        tenantAccessService.requireManagementPermission(principal, "content:publish");
        var shop = shopContextService.requireTenantShop(principal.tenantId(), null);
        List<String> statuses = jdbcTemplate.queryForList(
            """
            SELECT status FROM banner
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            LIMIT 1 FOR UPDATE
            """,
            String.class,
            contentId, principal.tenantId(), shop.shopId(), expectedVersion
        );
        if (statuses.isEmpty()) throw versionConflict();
        String target;
        try {
            target = ContentStatusPolicy.transition(
                statuses.getFirst(), targetStatus, scheduledAt, Instant.now(clock)
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        jdbcTemplate.update(
            """
            UPDATE banner
            SET status = ?, scheduled_at = ?,
                published_at = CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP(3) ELSE published_at END,
                offline_at = CASE WHEN ? = 'OFFLINE' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            target,
            "SCHEDULED".equals(target) ? Timestamp.from(scheduledAt) : null,
            target,
            target,
            principal.accountId(),
            contentId, principal.tenantId(), shop.shopId(), expectedVersion
        );
        audit(principal, shop.shopId(), "CONTENT_STATUS_CHANGE", contentId, target);
        return Map.of("id", contentId, "status", target, "version", expectedVersion + 1);
    }

    private void publishDue(long tenantId, long shopId) {
        jdbcTemplate.update(
            """
            UPDATE banner
            SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3),
                scheduled_at = NULL, version = version + 1
            WHERE tenant_id = ? AND shop_id = ? AND status = 'SCHEDULED'
              AND scheduled_at <= CURRENT_TIMESTAMP(3)
            """,
            tenantId, shopId
        );
    }

    private ValidatedDraft validate(ContentDraft draft) {
        if (draft == null) throw new ApiException(HttpStatus.BAD_REQUEST, "内容不能为空");
        String type = normalize(draft.contentType());
        if (!TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的内容类型");
        String title = required(draft.title(), "标题不能为空", 120);
        String targetType = normalize(draft.targetType());
        if (targetType.isEmpty()) targetType = "NONE";
        if (!TARGET_TYPES.contains(targetType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的跳转类型");
        }
        if (draft.sortOrder() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "排序值不能小于 0");
        return new ValidatedDraft(
            type, title, optional(draft.summary(), 500), optional(draft.body(), 5000),
            optional(draft.imageUrl(), 500), targetType, optional(draft.targetValue(), 255),
            draft.sortOrder()
        );
    }

    private String required(String value, String message, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "内容字段过长");
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(TenantPrincipal principal, long shopId, String action, long id, String status) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log
                (tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data)
            VALUES (?, ?, ?, ?, 'CONTENT', ?, IF(? IS NULL, NULL, JSON_OBJECT('status', ?)))
            """,
            principal.tenantId(), shopId, principal.accountId(), action, id, status, status
        );
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "内容版本或状态已变化，请刷新后重试");
    }

    public record ContentDraft(
        String contentType,
        String title,
        String summary,
        String body,
        String imageUrl,
        String targetType,
        String targetValue,
        int sortOrder
    ) {
    }

    private record ValidatedDraft(
        String type,
        String title,
        String summary,
        String body,
        String imageUrl,
        String targetType,
        String targetValue,
        int sortOrder
    ) {
    }
}
