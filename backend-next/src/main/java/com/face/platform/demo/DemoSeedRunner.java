package com.face.platform.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(10)
public class DemoSeedRunner implements ApplicationRunner {

    static final String ADMIN_USERNAME = "demo-admin";
    static final String MEMBER_PHONE = "13900000001";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String commonPassword;

    public DemoSeedRunner(
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        @Value("${face.demo.account-password}") String commonPassword
    ) {
        if (commonPassword == null || commonPassword.length() < 12) {
            throw new IllegalStateException("FACE_DEMO_ADMIN_PASSWORD must contain at least 12 characters");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.commonPassword = commonPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long tenantId = requiredLong("SELECT tenant_id FROM shop WHERE id = 1 AND status = 'ACTIVE'");
        String passwordHash = passwordEncoder.encode(commonPassword);

        jdbcTemplate.update(
            """
            UPDATE account a
            JOIN account_shop_role ar ON ar.account_id = a.id AND ar.tenant_id = a.tenant_id
            JOIN role_definition r ON r.id = ar.role_id AND r.tenant_id = ar.tenant_id
            SET a.password_hash = ?, a.failed_login_count = 0, a.locked_until = NULL,
                a.status = 'ACTIVE', a.version = a.version + 1
            WHERE a.tenant_id = ? AND r.role_code = 'SUPER_ADMIN'
            """,
            passwordHash, tenantId
        );

        jdbcTemplate.update(
            """
            INSERT INTO account (
                tenant_id, home_shop_id, shop_id, username, display_name,
                password_hash, role_code, status
            ) VALUES (?, 1, 1, ?, '演示运营管理员', ?, 'ADMIN', 'ACTIVE')
            ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),
                password_hash = VALUES(password_hash), status = 'ACTIVE',
                failed_login_count = 0, locked_until = NULL, version = version + 1
            """,
            tenantId, ADMIN_USERNAME, passwordHash
        );
        long adminId = requiredLong(
            "SELECT id FROM account WHERE tenant_id = " + tenantId
                + " AND username = '" + ADMIN_USERNAME + "' LIMIT 1"
        );
        jdbcTemplate.update(
            """
            INSERT IGNORE INTO account_shop_role
                (tenant_id, account_id, region_id, shop_id, role_id, status)
            SELECT ?, ?, NULL, 1, id, 'ACTIVE'
            FROM role_definition
            WHERE tenant_id = ? AND role_code = 'ADMIN' AND status = 'ACTIVE'
            LIMIT 1
            """,
            tenantId, adminId, tenantId
        );

        long memberId = requiredLong(
            "SELECT id FROM member WHERE tenant_id = " + tenantId
                + " AND phone = '" + MEMBER_PHONE + "' LIMIT 1"
        );
        jdbcTemplate.update(
            """
            INSERT INTO account (
                tenant_id, home_shop_id, shop_id, username, display_name, phone,
                password_hash, role_code, member_id, status
            ) VALUES (?, 1, 1, ?, '体验会员', ?, ?, 'MEMBER', ?, 'ACTIVE')
            ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),
                password_hash = VALUES(password_hash), status = 'ACTIVE',
                failed_login_count = 0, locked_until = NULL, version = version + 1
            """,
            tenantId, "m:" + tenantId + ":" + MEMBER_PHONE,
            MEMBER_PHONE, passwordHash, memberId
        );
        long memberAccountId = requiredLong(
            "SELECT id FROM account WHERE tenant_id = " + tenantId
                + " AND phone = '" + MEMBER_PHONE + "' LIMIT 1"
        );
        jdbcTemplate.update(
            """
            INSERT IGNORE INTO account_shop_role
                (tenant_id, account_id, region_id, shop_id, role_id, status)
            SELECT ?, ?, NULL, 1, id, 'ACTIVE'
            FROM role_definition
            WHERE tenant_id = ? AND role_code = 'MEMBER' AND status = 'ACTIVE'
            LIMIT 1
            """,
            tenantId, memberAccountId, tenantId
        );
    }

    private long requiredLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        if (value == null) throw new IllegalStateException("Required demo seed fact is unavailable");
        return value;
    }
}
