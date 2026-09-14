package com.face.platform.identity;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class AdminAccountApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TenantAccessService tenantAccessService;
    private final ShopContextService shopContextService;

    public AdminAccountApplicationService(
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        TenantAccessService tenantAccessService,
        ShopContextService shopContextService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tenantAccessService = tenantAccessService;
        this.shopContextService = shopContextService;
    }

    public List<Map<String, Object>> list(TenantPrincipal principal) {
        tenantAccessService.requireManagementPermission(principal, "admin_account:view");
        return jdbcTemplate.queryForList(
            """
            SELECT a.id, a.username, a.display_name AS displayName,
                   r.role_code AS role, a.status, a.version,
                   a.last_login_at AS lastLoginAt, a.created_at AS createdAt
            FROM account a
            JOIN account_shop_role ar
              ON ar.account_id = a.id AND ar.tenant_id = a.tenant_id
            JOIN role_definition r
              ON r.id = ar.role_id AND r.tenant_id = ar.tenant_id
            WHERE a.tenant_id = ?
              AND r.role_code IN ('ADMIN', 'SUPER_ADMIN')
              AND ar.status = 'ACTIVE' AND r.status = 'ACTIVE'
            GROUP BY a.id, a.username, a.display_name, r.role_code, a.status,
                     a.version, a.last_login_at, a.created_at
            ORDER BY FIELD(r.role_code, 'SUPER_ADMIN', 'ADMIN'), a.id
            """,
            principal.tenantId()
        );
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        String rawUsername,
        String rawDisplayName,
        String rawPassword,
        String rawRole
    ) {
        requireManage(principal);
        String username = required(rawUsername, "请输入用户名", 80);
        String displayName = required(rawDisplayName, "请输入显示名称", 80);
        validatePassword(rawPassword);
        String role = role(rawRole);
        var shop = shopContextService.requireTenantShop(principal.tenantId(), null);
        Long grantShopId = "ADMIN".equals(role) ? shop.shopId() : null;

        long accountId;
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO account (
                        tenant_id, home_shop_id, shop_id, username, display_name,
                        password_hash, role_code, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, shop.shopId());
                statement.setLong(3, shop.shopId());
                statement.setString(4, username);
                statement.setString(5, displayName);
                statement.setString(6, passwordEncoder.encode(rawPassword));
                statement.setString(7, role);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) throw new IllegalStateException("missing generated account id");
            accountId = key.longValue();
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }

        int granted = jdbcTemplate.update(
            """
            INSERT INTO account_shop_role
                (tenant_id, account_id, region_id, shop_id, role_id, status)
            SELECT ?, ?, NULL, ?, id, 'ACTIVE'
            FROM role_definition
            WHERE tenant_id = ? AND role_code = ? AND status = 'ACTIVE'
            LIMIT 1
            """,
            principal.tenantId(), accountId, grantShopId, principal.tenantId(), role
        );
        if (granted != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "管理员角色配置缺失");
        }
        audit(principal, shop.shopId(), "ADMIN_ACCOUNT_CREATE", accountId, role);
        return Map.of("id", accountId, "role", role, "version", 1);
    }

    @Transactional
    public Map<String, Object> deactivate(
        TenantPrincipal principal,
        long accountId,
        int expectedVersion
    ) {
        requireManage(principal);
        requireOtherAccount(principal, accountId);
        String role = requireVisibleTarget(principal.tenantId(), accountId);
        int changed = jdbcTemplate.update(
            """
            UPDATE account SET status = 'INACTIVE', version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            accountId, principal.tenantId(), expectedVersion
        );
        if (changed != 1) throw versionConflict();
        revokeSessions(principal.tenantId(), accountId, "ADMIN_DEACTIVATE");
        audit(principal, principal.homeShopId(), "ADMIN_ACCOUNT_DEACTIVATE", accountId, role);
        return Map.of("id", accountId, "status", "INACTIVE", "version", expectedVersion + 1);
    }

    @Transactional
    public Map<String, Object> resetPassword(
        TenantPrincipal principal,
        long accountId,
        int expectedVersion,
        String password
    ) {
        requireManage(principal);
        requireOtherAccount(principal, accountId);
        validatePassword(password);
        String role = requireVisibleTarget(principal.tenantId(), accountId);
        int changed = jdbcTemplate.update(
            """
            UPDATE account
            SET password_hash = ?, failed_login_count = 0, locked_until = NULL,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            passwordEncoder.encode(password), accountId, principal.tenantId(), expectedVersion
        );
        if (changed != 1) throw versionConflict();
        revokeSessions(principal.tenantId(), accountId, "ADMIN_PASSWORD_RESET");
        audit(principal, principal.homeShopId(), "ADMIN_ACCOUNT_PASSWORD_RESET", accountId, role);
        return Map.of("id", accountId, "reset", true, "version", expectedVersion + 1);
    }

    private void requireManage(TenantPrincipal principal) {
        tenantAccessService.requireManagementPermission(principal, "admin_account:manage");
        if (!principal.roles().contains("SUPER_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("仅超级管理员可管理管理员账号");
        }
    }

    private String requireVisibleTarget(long tenantId, long accountId) {
        List<String> roles = jdbcTemplate.queryForList(
            """
            SELECT DISTINCT r.role_code
            FROM account a
            JOIN account_shop_role ar ON ar.account_id = a.id AND ar.tenant_id = a.tenant_id
            JOIN role_definition r ON r.id = ar.role_id AND r.tenant_id = ar.tenant_id
            WHERE a.id = ? AND a.tenant_id = ?
              AND r.role_code IN ('ADMIN', 'SUPER_ADMIN') AND ar.status = 'ACTIVE'
            """,
            String.class,
            accountId,
            tenantId
        );
        if (roles.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "管理员账号不存在");
        return roles.contains("SUPER_ADMIN") ? "SUPER_ADMIN" : "ADMIN";
    }

    private void requireOtherAccount(TenantPrincipal principal, long accountId) {
        if (principal.accountId() == accountId) {
            throw new ApiException(HttpStatus.CONFLICT, "不能在当前会话中停用或重置自己的账号");
        }
    }

    private void revokeSessions(long tenantId, long accountId, String reason) {
        jdbcTemplate.update(
            """
            UPDATE auth_session
            SET revoked_at = CURRENT_TIMESTAMP(3), revoke_reason = ?,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE tenant_id = ? AND account_id = ? AND revoked_at IS NULL
            """,
            reason, tenantId, accountId
        );
        jdbcTemplate.update("DELETE FROM token WHERE userid = ?", accountId);
    }

    private void audit(
        TenantPrincipal principal,
        Long shopId,
        String action,
        long targetId,
        String role
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log
                (tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data)
            VALUES (?, ?, ?, ?, 'ACCOUNT', ?, JSON_OBJECT('role', ?))
            """,
            principal.tenantId(), shopId, principal.accountId(), action, targetId, role
        );
    }

    private String role(String value) {
        try {
            return AdminRolePolicy.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private String required(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度应为 8 至 200 个字符");
        }
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "账号状态已变化，请刷新后重试");
    }
}
