package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ServiceCatalogService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public ServiceCatalogService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<Map<String, Object>> requirePackageServices(
        TenantPrincipal principal,
        long shopId,
        List<Long> serviceIds
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐至少包含一个服务项目");
        }
        List<Long> distinctIds = serviceIds.stream().distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(distinctIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        args.addAll(distinctIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, name
            FROM service_item
            WHERE tenant_id = ?
              AND shop_id = ?
              AND status = 'ACTIVE'
              AND id IN (%s)
            ORDER BY id
            """.formatted(placeholders),
            args.toArray()
        );
        if (rows.size() != distinctIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐包含不存在或已停用的服务项目");
        }
        return rows;
    }

    @Transactional
    public void update(
        TenantPrincipal principal,
        long shopId,
        long serviceId,
        String name,
        String coverUrl,
        int durationMinutes,
        int cleanupMinutes,
        BigDecimal listPrice,
        BigDecimal memberPrice,
        String status,
        int expectedVersion
    ) {
        String safeName = name == null ? "" : name.trim();
        String safeStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (safeName.isBlank() || safeName.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "服务项目名称不能为空且不能超过120个字符");
        }
        if (durationMinutes <= 0 || cleanupMinutes < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "服务时长或清场时长无效");
        }
        if (listPrice == null || listPrice.signum() < 0
            || memberPrice == null || memberPrice.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "服务价格不能为负数");
        }
        if (!STATUSES.contains(safeStatus) || expectedVersion < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "服务状态或版本无效");
        }
        accessService.requireShopPermission(principal, shopId, "service:manage");
        int changed = jdbcTemplate.update(
            """
            UPDATE service_item
            SET name = ?,
                cover_url = ?,
                duration_minutes = ?,
                cleanup_minutes = ?,
                list_price = ?,
                member_price = ?,
                status = ?,
                version = version + 1,
                updated_by = ?,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
              AND tenant_id = ?
              AND shop_id = ?
              AND version = ?
            """,
            safeName,
            trimToNull(coverUrl),
            durationMinutes,
            cleanupMinutes,
            listPrice,
            memberPrice,
            safeStatus,
            principal.accountId(),
            serviceId,
            principal.tenantId(),
            shopId,
            expectedVersion
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "服务项目版本已变化，请刷新后重试");
        }
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            )
            VALUES (
                ?, ?, ?, 'SERVICE_CATALOG_UPDATE', 'SERVICE_ITEM', ?,
                JSON_OBJECT('status', ?, 'version', ?)
            )
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            serviceId,
            safeStatus,
            expectedVersion + 1
        );
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
