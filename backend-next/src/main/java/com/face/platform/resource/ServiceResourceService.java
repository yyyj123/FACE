package com.face.platform.resource;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ServiceResourceService {

    private static final Set<String> TYPES = Set.of("ROOM", "EQUIPMENT");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public ServiceResourceService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<Map<String, Object>> list(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "resource:view");
        return jdbcTemplate.queryForList(
            """
            SELECT id,
                   resource_code AS resourceCode,
                   resource_name AS resourceName,
                   resource_type AS resourceType,
                   capacity,
                   status,
                   version,
                   updated_at AS updatedAt
            FROM service_resource
            WHERE tenant_id = ?
              AND shop_id = ?
            ORDER BY resource_type, resource_name, id
            """,
            principal.tenantId(),
            shopId
        );
    }

    @Transactional
    public void create(
        TenantPrincipal principal,
        long shopId,
        String resourceCode,
        String resourceName,
        String resourceType,
        int capacity
    ) {
        ResourceValues values = validate(resourceCode, resourceName, resourceType, capacity);
        accessService.requireShopPermission(principal, shopId, "resource:manage");
        jdbcTemplate.update(
            """
            INSERT INTO service_resource (
                tenant_id, shop_id, resource_code, resource_name,
                resource_type, capacity, status, version, created_by, updated_by
            )
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            values.code(),
            values.name(),
            values.type(),
            values.capacity(),
            principal.accountId(),
            principal.accountId()
        );
        audit(principal, shopId, "RESOURCE_CREATE", null, values.type());
    }

    @Transactional
    public void update(
        TenantPrincipal principal,
        long shopId,
        long resourceId,
        String resourceCode,
        String resourceName,
        String resourceType,
        int capacity,
        int expectedVersion
    ) {
        ResourceValues values = validate(resourceCode, resourceName, resourceType, capacity);
        if (expectedVersion < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源版本号无效");
        }
        accessService.requireShopPermission(principal, shopId, "resource:manage");
        int changed = jdbcTemplate.update(
            """
            UPDATE service_resource
            SET resource_code = ?,
                resource_name = ?,
                resource_type = ?,
                capacity = ?,
                version = version + 1,
                updated_by = ?,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
              AND tenant_id = ?
              AND shop_id = ?
              AND version = ?
            """,
            values.code(),
            values.name(),
            values.type(),
            values.capacity(),
            principal.accountId(),
            resourceId,
            principal.tenantId(),
            shopId,
            expectedVersion
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "资源版本已变化，请刷新后重试");
        }
        audit(principal, shopId, "RESOURCE_UPDATE", resourceId, values.type());
    }

    @Transactional
    public void deactivate(
        TenantPrincipal principal,
        long shopId,
        long resourceId,
        int expectedVersion
    ) {
        if (expectedVersion < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源版本号无效");
        }
        accessService.requireShopPermission(principal, shopId, "resource:manage");
        int changed = jdbcTemplate.update(
            """
            UPDATE service_resource
            SET status = 'INACTIVE',
                version = version + 1,
                updated_by = ?,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
              AND tenant_id = ?
              AND shop_id = ?
              AND version = ?
              AND status = 'ACTIVE'
            """,
            principal.accountId(),
            resourceId,
            principal.tenantId(),
            shopId,
            expectedVersion
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "资源版本已变化或资源已停用，请刷新后重试");
        }
        audit(principal, shopId, "RESOURCE_DEACTIVATE", resourceId, "INACTIVE");
    }

    private ResourceValues validate(
        String resourceCode,
        String resourceName,
        String resourceType,
        int capacity
    ) {
        String code = resourceCode == null ? "" : resourceCode.trim().toUpperCase(Locale.ROOT);
        String name = resourceName == null ? "" : resourceName.trim();
        String type = resourceType == null ? "" : resourceType.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank() || code.length() > 50 || !code.matches("[A-Z0-9_-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源编码仅支持字母、数字、下划线和短横线");
        }
        if (name.isBlank() || name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源名称不能为空且不能超过100个字符");
        }
        if (!TYPES.contains(type) || capacity < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源类型或容量无效");
        }
        return new ResourceValues(code, name, type, capacity);
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        Long entityId,
        String state
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            )
            VALUES (?, ?, ?, ?, 'SERVICE_RESOURCE', ?, JSON_OBJECT('state', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityId,
            state
        );
    }

    private record ResourceValues(String code, String name, String type, int capacity) {
    }
}
