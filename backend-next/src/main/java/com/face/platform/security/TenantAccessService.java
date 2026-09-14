package com.face.platform.security;

import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class TenantAccessService {

    private final JdbcTemplate jdbcTemplate;

    public TenantAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TenantPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
            """
            SELECT a.id AS account_id, a.tenant_id, a.home_shop_id, a.username
            FROM token t
            JOIN account a ON a.id = t.userid
            WHERE t.token = ?
              AND t.expiratedtime > CURRENT_TIMESTAMP(3)
              AND a.status = 'ACTIVE'
            LIMIT 1
            """,
            token.trim()
        );
        Optional<TenantPrincipal> legacy = resolveAccount(accounts);
        return legacy.isPresent() ? legacy : resolveV3(token);
    }

    public Optional<TenantPrincipal> resolveV3(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
            """
            SELECT a.id AS account_id, a.tenant_id, a.home_shop_id, a.username
            FROM auth_session s
            JOIN account a
              ON a.id = s.account_id
             AND a.tenant_id = s.tenant_id
            WHERE s.access_token_hash = ?
              AND s.revoked_at IS NULL
              AND s.access_expires_at > CURRENT_TIMESTAMP(3)
              AND s.refresh_expires_at > CURRENT_TIMESTAMP(3)
              AND a.status = 'ACTIVE'
            LIMIT 1
            """,
            SessionTokenCodec.sha256(token.trim())
        );
        return resolveAccount(accounts);
    }

    private Optional<TenantPrincipal> resolveAccount(List<Map<String, Object>> accounts) {
        if (accounts.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> account = accounts.getFirst();
        long accountId = number(account.get("account_id"));
        long tenantId = number(account.get("tenant_id"));
        Long homeShopId = nullableNumber(account.get("home_shop_id"));

        List<Map<String, Object>> grants = jdbcTemplate.queryForList(
            """
            SELECT r.role_code, r.scope_type, ar.region_id, ar.shop_id
            FROM account_shop_role ar
            JOIN role_definition r ON r.id = ar.role_id
            WHERE ar.account_id = ?
              AND ar.tenant_id = ?
              AND ar.status = 'ACTIVE'
              AND r.status = 'ACTIVE'
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
            ORDER BY r.role_code
            """,
            accountId,
            tenantId
        );

        List<String> roles = new ArrayList<>();
        Set<Long> regionIds = new LinkedHashSet<>();
        Set<Long> shopIds = new LinkedHashSet<>();
        boolean tenantWide = false;
        for (Map<String, Object> grant : grants) {
            roles.add(grant.get("role_code").toString());
            tenantWide = tenantWide || "TENANT".equals(grant.get("scope_type"));
            Long regionId = nullableNumber(grant.get("region_id"));
            Long shopId = nullableNumber(grant.get("shop_id"));
            if (regionId != null) regionIds.add(regionId);
            if (shopId != null) shopIds.add(shopId);
        }
        if (roles.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new TenantPrincipal(
            accountId,
            tenantId,
            homeShopId,
            account.get("username").toString(),
            List.copyOf(roles),
            Set.copyOf(regionIds),
            Set.copyOf(shopIds),
            tenantWide
        ));
    }

    public List<Map<String, Object>> listAccessibleShops(TenantPrincipal principal) {
        if (principal.tenantWide()) {
            return jdbcTemplate.queryForList(
                """
                SELECT id, shop_code AS shopCode, name, phone, address, timezone,
                       currency_code AS currencyCode, status
                FROM shop
                WHERE tenant_id = ?
                ORDER BY name
                """,
                principal.tenantId()
            );
        }
        if (!principal.regionIds().isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(principal.regionIds().size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(principal.tenantId());
            args.addAll(principal.regionIds());
            return jdbcTemplate.queryForList(
                """
                SELECT id, shop_code AS shopCode, name, phone, address, timezone,
                       currency_code AS currencyCode, status
                FROM shop
                WHERE tenant_id = ?
                  AND region_id IN (%s)
                ORDER BY name
                """.formatted(placeholders),
                args.toArray()
            );
        }
        if (principal.shopIds().isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(principal.shopIds().size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.addAll(principal.shopIds());
        return jdbcTemplate.queryForList(
            """
            SELECT id, shop_code AS shopCode, name, phone, address, timezone,
                   currency_code AS currencyCode, status
            FROM shop
            WHERE tenant_id = ?
              AND id IN (%s)
            ORDER BY name
            """.formatted(placeholders),
            args.toArray()
        );
    }

    public List<Long> accessibleShopIds(TenantPrincipal principal) {
        return listAccessibleShops(principal).stream()
            .map(shop -> number(shop.get("id")))
            .toList();
    }

    public List<Long> accessibleShopIds(TenantPrincipal principal, String permissionCode) {
        return jdbcTemplate.queryForList(
            """
            SELECT DISTINCT s.id
            FROM account_shop_role ar
            JOIN role_definition r
              ON r.id = ar.role_id
             AND r.tenant_id = ar.tenant_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission_definition p ON p.id = rp.permission_id
            JOIN shop s ON s.tenant_id = ar.tenant_id
            WHERE ar.account_id = ?
              AND ar.tenant_id = ?
              AND p.permission_code = ?
              AND ar.status = 'ACTIVE'
              AND r.status = 'ACTIVE'
              AND r.scope_type IN ('TENANT', 'REGION', 'SHOP', 'SELF')
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
              AND (
                    r.scope_type = 'TENANT'
                 OR (r.scope_type = 'REGION' AND ar.region_id = s.region_id)
                 OR (r.scope_type IN ('SHOP', 'SELF') AND ar.shop_id = s.id)
              )
            ORDER BY s.id
            """,
            principal.accountId(),
            principal.tenantId(),
            permissionCode
        ).stream()
            .map(shop -> number(shop.get("id")))
            .toList();
    }

    public List<String> permissionCodes(TenantPrincipal principal) {
        return jdbcTemplate.queryForList(
            """
            SELECT DISTINCT p.permission_code
            FROM account_shop_role ar
            JOIN role_definition r
              ON r.id = ar.role_id
             AND r.tenant_id = ar.tenant_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission_definition p ON p.id = rp.permission_id
            WHERE ar.account_id = ?
              AND ar.tenant_id = ?
              AND ar.status = 'ACTIVE'
              AND r.status = 'ACTIVE'
              AND r.scope_type IN ('TENANT', 'REGION', 'SHOP', 'SELF')
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
            ORDER BY p.permission_code
            """,
            String.class,
            principal.accountId(),
            principal.tenantId()
        );
    }

    public void requirePermission(TenantPrincipal principal, String permissionCode) {
        if (!permissionCodes(principal).contains(permissionCode)) {
            throw new AccessDeniedException("当前账号没有该功能权限");
        }
    }

    public List<Long> accountIdsWithShopPermission(
        long tenantId,
        long shopId,
        String permissionCode
    ) {
        return jdbcTemplate.queryForList(
            """
            SELECT DISTINCT a.id
            FROM account a
            JOIN account_shop_role ar
              ON ar.account_id = a.id
             AND ar.tenant_id = a.tenant_id
            JOIN role_definition r
              ON r.id = ar.role_id
             AND r.tenant_id = ar.tenant_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission_definition p ON p.id = rp.permission_id
            JOIN shop s ON s.id = ? AND s.tenant_id = ?
            WHERE a.tenant_id = ?
              AND a.status = 'ACTIVE'
              AND p.permission_code = ?
              AND ar.status = 'ACTIVE'
              AND r.status = 'ACTIVE'
              AND r.scope_type IN ('TENANT', 'REGION', 'SHOP', 'SELF')
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
              AND (
                    r.scope_type = 'TENANT'
                 OR (r.scope_type = 'REGION' AND ar.region_id = s.region_id)
                 OR (r.scope_type IN ('SHOP', 'SELF') AND ar.shop_id = s.id)
              )
            ORDER BY a.id
            """,
            Long.class,
            shopId,
            tenantId,
            tenantId,
            permissionCode
        );
    }

    public long requireShopAccess(TenantPrincipal principal, Long shopId) {
        if (shopId == null) {
            throw new AccessDeniedException("请选择可管理的门店");
        }
        boolean accessible = accessibleShopIds(principal).stream().anyMatch(id -> id == shopId.longValue());
        if (!accessible) {
            throw new AccessDeniedException("当前账号无权管理该门店");
        }
        return shopId;
    }

    public long requireShopPermission(
        TenantPrincipal principal,
        long shopId,
        String permissionCode
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM account_shop_role ar
            JOIN role_definition r
              ON r.id = ar.role_id
             AND r.tenant_id = ar.tenant_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission_definition p ON p.id = rp.permission_id
            JOIN shop s ON s.tenant_id = ar.tenant_id
            WHERE ar.account_id = ?
              AND ar.tenant_id = ?
              AND s.id = ?
              AND p.permission_code = ?
              AND ar.status = 'ACTIVE'
              AND r.status = 'ACTIVE'
              AND r.scope_type IN ('TENANT', 'REGION', 'SHOP', 'SELF')
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
              AND (
                    r.scope_type = 'TENANT'
                 OR (r.scope_type = 'REGION' AND ar.region_id = s.region_id)
                 OR (r.scope_type IN ('SHOP', 'SELF') AND ar.shop_id = s.id)
              )
            """,
            Integer.class,
            principal.accountId(),
            principal.tenantId(),
            shopId,
            permissionCode
        );
        if (count == null || count == 0) {
            throw new AccessDeniedException("当前账号没有该门店的功能权限");
        }
        return shopId;
    }

    public void requireManagementPermission(TenantPrincipal principal, String permissionCode) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM account_shop_role ar
            JOIN role_definition r ON r.id = ar.role_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission_definition p ON p.id = rp.permission_id
            WHERE ar.account_id = ?
              AND ar.tenant_id = ?
              AND ar.status = 'ACTIVE'
              AND r.status = 'ACTIVE'
              AND r.scope_type IN ('TENANT', 'REGION', 'SHOP')
              AND p.permission_code = ?
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
            """,
            Integer.class,
            principal.accountId(),
            principal.tenantId(),
            permissionCode
        );
        if (count == null || count == 0) {
            throw new AccessDeniedException("当前账号没有该功能的管理权限");
        }
    }

    public long requireMemberId(TenantPrincipal principal) {
        if (!principal.roles().contains("MEMBER")) {
            throw new AccessDeniedException("仅会员本人可以执行该操作");
        }
        Long memberId = jdbcTemplate.queryForObject(
            "SELECT member_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            Long.class,
            principal.accountId(),
            principal.tenantId()
        );
        if (memberId == null) {
            throw new AccessDeniedException("当前会员账号未绑定有效会员资料");
        }
        return memberId;
    }

    public long requireStaffId(TenantPrincipal principal) {
        if (!principal.roles().contains("BEAUTICIAN")) {
            throw new AccessDeniedException("仅技师本人可以执行该操作");
        }
        Long staffId = jdbcTemplate.queryForObject(
            "SELECT staff_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            Long.class,
            principal.accountId(),
            principal.tenantId()
        );
        if (staffId == null) {
            throw new AccessDeniedException("当前技师账号未绑定有效员工资料");
        }
        return staffId;
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("账号租户数据不完整");
        }
        return number.longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
