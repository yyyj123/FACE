package com.face.api;

import com.annotation.IgnoreAuth;
import com.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@RestController
@RequestMapping("/api/v1/auth")
public class FaceAuthController {
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public FaceAuthController(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @IgnoreAuth
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = stringValue(body.get("username"));
        String password = stringValue(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) {
            return FaceResponses.error(400, "请输入账号和密码");
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
            "SELECT id, shop_id, username, password_hash, role_code, staff_id, member_id " +
                "FROM account WHERE username = ? AND status = 'ACTIVE' LIMIT 1",
            username
        );
        if (accounts.isEmpty()) {
            return FaceResponses.error(401, "账号或密码不正确");
        }

        Map<String, Object> account = accounts.get(0);
        String hash = stringValue(account.get("password_hash"));
        if (!hash.startsWith("$2") || !passwordEncoder.matches(password, hash)) {
            return FaceResponses.error(401, "账号或密码不正确");
        }

        Long id = ((Number) account.get("id")).longValue();
        String role = stringValue(account.get("role_code"));
        String token = tokenService.generateToken(id, username, "account", role);
        jdbcTemplate.update("UPDATE account SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = ?", id);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("token", token);
        data.put("accountId", id);
        data.put("shopId", account.get("shop_id"));
        data.put("username", username);
        data.put("role", role);
        data.put("staffId", account.get("staff_id"));
        data.put("memberId", account.get("member_id"));
        return FaceResponses.ok(data);
    }

    @IgnoreAuth
    @PostMapping("/register")
    @Transactional
    public Map<String, Object> register(@RequestBody final Map<String, Object> body) {
        final String username = firstValue(body, "username", "zhanghao");
        final String password = firstValue(body, "password", "mima");
        final String name = firstValue(body, "name", "xingming");
        final String phone = firstValue(body, "phone", "shouji");
        if (username.isEmpty() || password.length() < 6 || name.isEmpty() || phone.isEmpty()) {
            return FaceResponses.error(400, "请完整填写账号、至少 6 位密码、姓名和手机号");
        }
        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account a LEFT JOIN member m ON m.id = a.member_id WHERE a.username = ? OR m.phone = ?",
            Integer.class, username, phone
        );
        if (duplicate != null && duplicate > 0) return FaceResponses.error(409, "账号或手机号已注册");
        final Long shopId = body.get("shopId") == null ? 1L : Long.valueOf(body.get("shopId").toString());
        final Long tenantId = jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM shop WHERE id = ? AND status = 'ACTIVE'", Long.class, shopId
        );
        if (tenantId == null) return FaceResponses.error(400, "门店不存在或已停用");
        final String memberNo = "M" + System.currentTimeMillis();
        final String globalMemberNo = "GM" + System.currentTimeMillis();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws java.sql.SQLException {
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO member (tenant_id, home_shop_id, shop_id, member_no, global_member_no, " +
                        "name, phone, gender, avatar_url, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ONLINE')",
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, tenantId);
                statement.setLong(2, shopId);
                statement.setLong(3, shopId);
                statement.setString(4, memberNo);
                statement.setString(5, globalMemberNo);
                statement.setString(6, name);
                statement.setString(7, phone);
                statement.setString(8, firstValue(body, "gender", "xingbie"));
                statement.setString(9, firstValue(body, "avatarUrl", "touxiang"));
                return statement;
            }
        }, keyHolder);
        Long memberId = keyHolder.getKey().longValue();
        jdbcTemplate.update(
            "INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, member_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'MEMBER', ?)",
            tenantId, shopId, shopId, username, name, passwordEncoder.encode(password), memberId
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("memberId", memberId);
        data.put("memberNo", memberNo);
        return FaceResponses.ok(data);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstValue(Map<String, Object> body, String primary, String legacy) {
        String value = stringValue(body.get(primary));
        return value.isEmpty() ? stringValue(body.get(legacy)) : value;
    }
}
