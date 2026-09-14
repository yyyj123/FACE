package com.face.platform.v3.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class V3AuthSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public V3AuthSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LoginAccount> findAccountForLogin(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id,
                   tenant_id,
                   home_shop_id,
                   username,
                   password_hash,
                   status,
                   CASE
                     WHEN locked_until IS NOT NULL
                      AND locked_until > CURRENT_TIMESTAMP(3)
                     THEN 1 ELSE 0
                   END AS login_locked
            FROM account
            WHERE username = ?
            LIMIT 1
            """,
            username
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.getFirst();
        return Optional.of(new LoginAccount(
            number(row.get("id")),
            number(row.get("tenant_id")),
            nullableNumber(row.get("home_shop_id")),
            String.valueOf(row.get("username")),
            String.valueOf(row.get("password_hash")),
            String.valueOf(row.get("status")),
            truthy(row.get("login_locked"))
        ));
    }

    public Optional<LoginAccount> findMemberAccountForLogin(long tenantId, String identity) {
        return findLoginAccount(
            """
            SELECT id, tenant_id, home_shop_id, username, password_hash, status,
                   CASE WHEN locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(3)
                        THEN 1 ELSE 0 END AS login_locked
            FROM account
            WHERE tenant_id = ? AND role_code = 'MEMBER'
              AND (phone = ? OR username = ?)
            LIMIT 1
            """,
            tenantId,
            identity,
            identity
        );
    }

    public Optional<LoginAccount> findMemberAccountByPhone(long tenantId, String phone) {
        return findLoginAccount(
            """
            SELECT id, tenant_id, home_shop_id, username, password_hash, status,
                   CASE WHEN locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(3)
                        THEN 1 ELSE 0 END AS login_locked
            FROM account
            WHERE tenant_id = ? AND role_code = 'MEMBER' AND phone = ?
            LIMIT 1
            """,
            tenantId,
            phone
        );
    }

    public boolean hasVisibleAdminRole(long accountId, long tenantId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM account_shop_role ar
            JOIN role_definition r ON r.id = ar.role_id AND r.tenant_id = ar.tenant_id
            WHERE ar.account_id = ? AND ar.tenant_id = ?
              AND ar.status = 'ACTIVE' AND r.status = 'ACTIVE'
              AND r.role_code IN ('ADMIN', 'SUPER_ADMIN')
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
            """,
            Integer.class,
            accountId,
            tenantId
        );
        return count != null && count > 0;
    }

    public void updatePasswordAndRevoke(long accountId, long tenantId, String passwordHash, Instant now) {
        jdbcTemplate.update(
            """
            UPDATE account SET password_hash = ?, failed_login_count = 0,
                               locked_until = NULL, version = version + 1
            WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
            """,
            passwordHash,
            accountId,
            tenantId
        );
        jdbcTemplate.update(
            """
            UPDATE auth_session SET revoked_at = ?, revoke_reason = 'PASSWORD_RESET',
                                    version = version + 1, updated_at = ?
            WHERE account_id = ? AND tenant_id = ? AND revoked_at IS NULL
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            accountId,
            tenantId
        );
    }

    private Optional<LoginAccount> findLoginAccount(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.getFirst();
        return Optional.of(new LoginAccount(
            number(row.get("id")),
            number(row.get("tenant_id")),
            nullableNumber(row.get("home_shop_id")),
            String.valueOf(row.get("username")),
            String.valueOf(row.get("password_hash")),
            String.valueOf(row.get("status")),
            truthy(row.get("login_locked"))
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(
        long accountId,
        Instant now,
        int threshold,
        Duration lockDuration
    ) {
        jdbcTemplate.update(
            """
            UPDATE account
            SET locked_until = CASE
                    WHEN failed_login_count + 1 >= ?
                    THEN DATE_ADD(?, INTERVAL ? SECOND)
                    ELSE locked_until
                END,
                failed_login_count = LEAST(failed_login_count + 1, 65535)
            WHERE id = ?
            """,
            threshold,
            Timestamp.from(now),
            lockDuration.toSeconds(),
            accountId
        );
    }

    public void recordLoginSuccess(long accountId, Instant now) {
        jdbcTemplate.update(
            """
            UPDATE account
            SET failed_login_count = 0,
                locked_until = NULL,
                last_login_at = ?
            WHERE id = ?
            """,
            Timestamp.from(now),
            accountId
        );
    }

    public void create(NewSession session) {
        jdbcTemplate.update(
            """
            INSERT INTO auth_session (
                session_id,
                tenant_id,
                account_id,
                home_shop_id,
                access_token_hash,
                refresh_token_hash,
                access_expires_at,
                refresh_expires_at,
                last_seen_at,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            session.sessionId(),
            session.tenantId(),
            session.accountId(),
            session.homeShopId(),
            session.accessTokenHash(),
            session.refreshTokenHash(),
            Timestamp.from(session.accessExpiresAt()),
            Timestamp.from(session.refreshExpiresAt()),
            Timestamp.from(session.createdAt()),
            Timestamp.from(session.createdAt()),
            Timestamp.from(session.createdAt())
        );
    }

    public Optional<RefreshableSession> findRefreshableForUpdate(
        String refreshTokenHash,
        Instant now
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT s.id,
                   s.session_id,
                   s.tenant_id,
                   s.account_id,
                   s.home_shop_id,
                   a.username,
                   s.version
            FROM auth_session s
            JOIN account a
              ON a.id = s.account_id
             AND a.tenant_id = s.tenant_id
            WHERE s.refresh_token_hash = ?
              AND s.revoked_at IS NULL
              AND s.refresh_expires_at > ?
              AND a.status = 'ACTIVE'
            LIMIT 1
            FOR UPDATE
            """,
            refreshTokenHash,
            Timestamp.from(now)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.getFirst();
        return Optional.of(new RefreshableSession(
            number(row.get("id")),
            String.valueOf(row.get("session_id")),
            number(row.get("tenant_id")),
            number(row.get("account_id")),
            nullableNumber(row.get("home_shop_id")),
            String.valueOf(row.get("username")),
            number(row.get("version"))
        ));
    }

    public boolean rotate(long id, long expectedVersion, Rotation rotation) {
        return jdbcTemplate.update(
            """
            UPDATE auth_session
            SET access_token_hash = ?,
                refresh_token_hash = ?,
                access_expires_at = ?,
                refresh_expires_at = ?,
                last_seen_at = ?,
                version = version + 1,
                updated_at = ?
            WHERE id = ?
              AND version = ?
              AND revoked_at IS NULL
            """,
            rotation.accessTokenHash(),
            rotation.refreshTokenHash(),
            Timestamp.from(rotation.accessExpiresAt()),
            Timestamp.from(rotation.refreshExpiresAt()),
            Timestamp.from(rotation.rotatedAt()),
            Timestamp.from(rotation.rotatedAt()),
            id,
            expectedVersion
        ) == 1;
    }

    public boolean revokeByAccessHash(String accessTokenHash, Instant now, String reason) {
        return jdbcTemplate.update(
            """
            UPDATE auth_session
            SET revoked_at = ?,
                revoke_reason = ?,
                version = version + 1,
                updated_at = ?
            WHERE access_token_hash = ?
              AND revoked_at IS NULL
            """,
            Timestamp.from(now),
            reason,
            Timestamp.from(now),
            accessTokenHash
        ) == 1;
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("会话数据缺少数字标识");
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return "1".equals(String.valueOf(value))
            || "true".equalsIgnoreCase(String.valueOf(value));
    }

    public record LoginAccount(
        long accountId,
        long tenantId,
        Long homeShopId,
        String username,
        String passwordHash,
        String status,
        boolean loginLocked
    ) {
    }

    public record NewSession(
        String sessionId,
        long tenantId,
        long accountId,
        Long homeShopId,
        String accessTokenHash,
        String refreshTokenHash,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        Instant createdAt
    ) {
    }

    public record RefreshableSession(
        long id,
        String sessionId,
        long tenantId,
        long accountId,
        Long homeShopId,
        String username,
        long version
    ) {
    }

    public record Rotation(
        String accessTokenHash,
        String refreshTokenHash,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        Instant rotatedAt
    ) {
    }
}
