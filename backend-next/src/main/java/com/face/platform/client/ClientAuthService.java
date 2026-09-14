package com.face.platform.client;

import com.face.platform.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientAuthService {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ClientAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> login(Map<String, Object> body) {
        String username = required(body, "username", "请输入账号");
        String password = required(body, "password", "请输入密码");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT a.id AS accountId, a.tenant_id AS tenantId, a.home_shop_id AS shopId,
                   a.username, a.password_hash AS passwordHash, a.role_code AS role,
                   a.staff_id AS staffId, a.member_id AS memberId
            FROM account a
            WHERE a.username = ? AND a.status = 'ACTIVE'
            LIMIT 1
            """,
            username
        );
        if (rows.isEmpty() || !passwordEncoder.matches(password, rows.getFirst().get("passwordHash").toString())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        Map<String, Object> account = rows.getFirst();
        String role = account.get("role").toString();
        Integer grantCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM account_shop_role ar
            JOIN role_definition r ON r.id = ar.role_id
            WHERE ar.account_id = ? AND ar.tenant_id = ?
              AND ar.status = 'ACTIVE' AND r.status = 'ACTIVE'
              AND ar.effective_from <= CURRENT_TIMESTAMP(3)
              AND (ar.effective_to IS NULL OR ar.effective_to > CURRENT_TIMESTAMP(3))
            """,
            Integer.class,
            account.get("accountId"),
            account.get("tenantId")
        );
        if (grantCount == null || grantCount == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "账号尚未分配有效门店角色");
        }

        String token = UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
            """
            INSERT INTO token (userid, username, tablename, role, token, expiratedtime)
            VALUES (?, ?, 'account', ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                token = VALUES(token),
                expiratedtime = VALUES(expiratedtime),
                addtime = CURRENT_TIMESTAMP
            """,
            account.get("accountId"),
            username,
            role,
            token,
            LocalDateTime.now().plusHours(12)
        );
        jdbcTemplate.update(
            "UPDATE account SET last_login_at = CURRENT_TIMESTAMP(3), failed_login_count = 0 WHERE id = ?",
            account.get("accountId")
        );

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("token", token);
        session.put("accountId", account.get("accountId"));
        session.put("shopId", account.get("shopId"));
        session.put("username", username);
        session.put("role", role);
        session.put("staffId", account.get("staffId"));
        session.put("memberId", account.get("memberId"));
        return session;
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> body) {
        String username = required(body, "username", "请输入账号");
        String password = required(body, "password", "请输入密码");
        String name = required(body, "name", "请输入姓名");
        String phone = required(body, "phone", "请输入手机号");
        long shopId = positiveLong(body.get("shopId"), 1L);
        if (username.length() < 3 || username.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "账号长度应为3到80个字符");
        }
        if (password.length() < 6 || password.length() > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度应为6到72个字符");
        }
        if (!phone.matches("1[3-9]\\d{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入正确的11位手机号");
        }

        List<Map<String, Object>> shops = jdbcTemplate.queryForList(
            "SELECT id, tenant_id AS tenantId FROM shop WHERE id = ? AND status = 'ACTIVE' LIMIT 1",
            shopId
        );
        if (shops.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "预约门店不存在或已停用");
        }
        long tenantId = ((Number) shops.getFirst().get("tenantId")).longValue();
        Integer usernameCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account WHERE username = ?",
            Integer.class,
            username
        );
        if (usernameCount != null && usernameCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该登录账号已存在");
        }
        Integer phoneCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM member WHERE tenant_id = ? AND phone = ?",
            Integer.class,
            tenantId,
            phone
        );
        if (phoneCount != null && phoneCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该手机号已注册，请直接登录或联系门店");
        }

        String temporary = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        KeyHolder memberKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO member (
                    tenant_id, home_shop_id, shop_id, member_no, global_member_no,
                    name, phone, source, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ONLINE', 'ACTIVE')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tenantId);
            statement.setLong(2, shopId);
            statement.setLong(3, shopId);
            statement.setString(4, "TMP" + temporary);
            statement.setString(5, "GTMP" + temporary);
            statement.setString(6, name);
            statement.setString(7, phone);
            return statement;
        }, memberKey);
        long memberId = generatedId(memberKey, "会员编号生成失败");
        String memberNo = "M%06d".formatted(memberId);
        String globalMemberNo = "GM%03d%012d".formatted(tenantId, memberId);
        jdbcTemplate.update(
            "UPDATE member SET member_no = ?, global_member_no = ? WHERE id = ? AND tenant_id = ?",
            memberNo,
            globalMemberNo,
            memberId,
            tenantId
        );
        jdbcTemplate.update(
            """
            INSERT INTO member_shop_profile
                (tenant_id, member_id, shop_id, first_visit_at, source, status)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), 'ONLINE', 'ACTIVE')
            """,
            tenantId,
            memberId,
            shopId
        );
        for (String accountType : List.of("BALANCE", "GIFT_BALANCE", "POINTS")) {
            jdbcTemplate.update(
                """
                INSERT INTO member_account
                    (tenant_id, member_id, account_type, currency_code, balance, status)
                VALUES (?, ?, ?, 'CNY', 0, 'ACTIVE')
                """,
                tenantId,
                memberId,
                accountType
            );
        }

        KeyHolder accountKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO account (
                    tenant_id, home_shop_id, shop_id, username, display_name,
                    password_hash, role_code, member_id, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'MEMBER', ?, 'ACTIVE')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tenantId);
            statement.setLong(2, shopId);
            statement.setLong(3, shopId);
            statement.setString(4, username);
            statement.setString(5, name);
            statement.setString(6, passwordEncoder.encode(password));
            statement.setLong(7, memberId);
            return statement;
        }, accountKey);
        long accountId = generatedId(accountKey, "登录账号创建失败");
        int granted = jdbcTemplate.update(
            """
            INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
            SELECT ?, ?, ?, id, 'ACTIVE'
            FROM role_definition
            WHERE tenant_id = ? AND role_code = 'MEMBER' AND status = 'ACTIVE'
            LIMIT 1
            """,
            tenantId,
            accountId,
            shopId,
            tenantId
        );
        if (granted != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "会员角色配置缺失");
        }
        jdbcTemplate.update(
            """
            INSERT INTO audit_log
                (tenant_id, shop_id, account_id, action, entity_type, entity_id)
            VALUES (?, ?, ?, 'CLIENT_REGISTER', 'MEMBER', ?)
            """,
            tenantId,
            shopId,
            accountId,
            memberId
        );
        return Map.of("memberId", memberId, "memberNo", memberNo);
    }

    private long generatedId(KeyHolder keyHolder, String message) {
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        return key.longValue();
    }

    private String required(Map<String, Object> body, String field, String message) {
        Object raw = body == null ? null : body.get(field);
        String value = raw == null ? "" : raw.toString().trim();
        if (value.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, message);
        return value;
    }

    private long positiveLong(Object value, long fallback) {
        if (value == null || value.toString().isBlank()) return fallback;
        try {
            long result = Long.parseLong(value.toString());
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "门店参数不正确");
        }
    }
}
