package com.face.platform.shop;

import com.face.platform.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShopContextService {

    private final JdbcTemplate jdbcTemplate;
    private final String businessMode;
    private final long defaultShopId;

    public ShopContextService(
        JdbcTemplate jdbcTemplate,
        @Value("${face.business.mode:SINGLE_SHOP}") String businessMode,
        @Value("${face.business.default-shop-id:0}") long defaultShopId
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.businessMode = normalizeMode(businessMode);
        this.defaultShopId = defaultShopId;
    }

    public ShopContext requirePublicShop(Long requestedShopId) {
        long resolvedId;
        if ("SINGLE_SHOP".equals(businessMode)) {
            if (defaultShopId <= 0) {
                throw unavailable("默认门店未配置");
            }
            if (requestedShopId != null && requestedShopId.longValue() != defaultShopId) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "单店模式不接受其他门店参数");
            }
            resolvedId = defaultShopId;
        } else {
            if (requestedShopId == null || requestedShopId <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "请选择门店");
            }
            resolvedId = requestedShopId;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, tenant_id AS tenantId, name, phone, address,
                   business_hours AS businessHours, timezone, status
            FROM shop
            WHERE id = ? AND status = 'ACTIVE'
            LIMIT 1
            """,
            resolvedId
        );
        if (rows.isEmpty()) {
            throw unavailable("默认门店不存在或已停用");
        }
        Map<String, Object> row = rows.getFirst();
        return new ShopContext(
            number(row.get("id")),
            number(row.get("tenantId")),
            String.valueOf(row.get("name")),
            nullableText(row.get("phone")),
            nullableText(row.get("address")),
            nullableText(row.get("businessHours")),
            nullableText(row.get("timezone")),
            businessMode
        );
    }

    public ShopContext requireTenantShop(long tenantId, Long requestedShopId) {
        ShopContext context = requirePublicShop(requestedShopId);
        if (context.tenantId() != tenantId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "默认门店不属于当前租户");
        }
        return context;
    }

    public boolean singleShop() {
        return "SINGLE_SHOP".equals(businessMode);
    }

    private String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SINGLE_SHOP", "MULTI_SHOP").contains(normalized)) {
            throw new IllegalArgumentException("BUSINESS_MODE must be SINGLE_SHOP or MULTI_SHOP");
        }
        return normalized;
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw unavailable("默认门店数据不完整");
    }

    private String nullableText(Object value) {
        return value == null ? null : value.toString();
    }

    public record ShopContext(
        long shopId,
        long tenantId,
        String name,
        String phone,
        String address,
        String businessHours,
        String timezone,
        String businessMode
    ) {
    }
}
