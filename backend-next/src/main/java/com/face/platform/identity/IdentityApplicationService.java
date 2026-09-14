package com.face.platform.identity;

import com.face.platform.api.ApiException;
import com.face.platform.member.MemberService;
import com.face.platform.shop.ShopContextService;
import com.face.platform.v3.auth.V3AuthSessionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IdentityApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ShopContextService shopContextService;
    private final SmsVerificationService smsVerificationService;
    private final MemberService memberService;
    private final V3AuthSessionService sessionService;

    public IdentityApplicationService(
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        ShopContextService shopContextService,
        SmsVerificationService smsVerificationService,
        MemberService memberService,
        V3AuthSessionService sessionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.shopContextService = shopContextService;
        this.smsVerificationService = smsVerificationService;
        this.memberService = memberService;
        this.sessionService = sessionService;
    }

    public Map<String, Object> requestSms(String phone, String purpose) {
        var shop = shopContextService.requirePublicShop(null);
        return smsVerificationService.request(shop.tenantId(), phone, purpose);
    }

    public AuthenticatedIdentity passwordLogin(String phone, String password) {
        var shop = shopContextService.requirePublicShop(null);
        String normalizedPhone = normalizePhone(phone);
        return identity(
            shop.tenantId(),
            shop.shopId(),
            normalizedPhone,
            sessionService.loginMember(shop.tenantId(), normalizedPhone, password)
        );
    }

    @Transactional
    public AuthenticatedIdentity smsLogin(String phone, String code) {
        var shop = shopContextService.requirePublicShop(null);
        String normalizedPhone = normalizePhone(phone);
        smsVerificationService.consume(shop.tenantId(), normalizedPhone, "REGISTER_LOGIN", code);
        return identity(
            shop.tenantId(),
            shop.shopId(),
            normalizedPhone,
            sessionService.issueMemberSession(shop.tenantId(), normalizedPhone)
        );
    }

    @Transactional
    public AuthenticatedIdentity register(
        String phone,
        String code,
        String password,
        String name
    ) {
        var shop = shopContextService.requirePublicShop(null);
        String normalizedPhone = normalizePhone(phone);
        validatePassword(password);
        smsVerificationService.consume(shop.tenantId(), normalizedPhone, "REGISTER_LOGIN", code);
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account WHERE tenant_id = ? AND phone = ?",
            Integer.class,
            shop.tenantId(),
            normalizedPhone
        );
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该手机号已注册，请直接登录");
        }

        MemberService.OnlineIdentityMember member = memberService.attachOnlineIdentity(
            shop.tenantId(), shop.shopId(), normalizedPhone, name
        );
        long accountId;
        try {
            accountId = createMemberAccount(
                shop.tenantId(), shop.shopId(), normalizedPhone, password, member
            );
        } catch (DuplicateKeyException exception) {
            // MySQL constraint uk_account_tenant_phone is the final race guard.
            throw new ApiException(HttpStatus.CONFLICT, "该手机号已注册，请直接登录");
        }
        int granted = jdbcTemplate.update(
            """
            INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
            SELECT ?, ?, ?, id, 'ACTIVE'
            FROM role_definition
            WHERE tenant_id = ? AND role_code = 'MEMBER' AND status = 'ACTIVE'
            LIMIT 1
            """,
            shop.tenantId(), accountId, shop.shopId(), shop.tenantId()
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
            shop.tenantId(), shop.shopId(), accountId, member.memberId()
        );
        return identity(
            shop.tenantId(),
            shop.shopId(),
            normalizedPhone,
            sessionService.issueMemberSession(shop.tenantId(), normalizedPhone)
        );
    }

    @Transactional
    public Map<String, Object> resetPassword(String phone, String code, String password) {
        var shop = shopContextService.requirePublicShop(null);
        String normalizedPhone = normalizePhone(phone);
        validatePassword(password);
        smsVerificationService.consume(shop.tenantId(), normalizedPhone, "PASSWORD_RESET", code);
        sessionService.resetMemberPassword(shop.tenantId(), normalizedPhone, password);
        return Map.of("reset", true);
    }

    private long createMemberAccount(
        long tenantId,
        long shopId,
        String phone,
        String password,
        MemberService.OnlineIdentityMember member
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO account (
                    tenant_id, home_shop_id, shop_id, username, display_name, phone,
                    password_hash, role_code, member_id, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'MEMBER', ?, 'ACTIVE')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tenantId);
            statement.setLong(2, shopId);
            statement.setLong(3, shopId);
            statement.setString(4, "m:" + tenantId + ":" + phone);
            statement.setString(5, member.name());
            statement.setString(6, phone);
            statement.setString(7, passwordEncoder.encode(password));
            statement.setLong(8, member.memberId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "登录账号创建失败");
        }
        return key.longValue();
    }

    private AuthenticatedIdentity identity(
        long tenantId,
        long shopId,
        String phone,
        V3AuthSessionService.SessionTokens tokens
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id AS accountId, member_id AS memberId, display_name AS displayName
            FROM account
            WHERE tenant_id = ? AND phone = ? AND role_code = 'MEMBER' AND status = 'ACTIVE'
            LIMIT 1
            """,
            tenantId,
            phone
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "会员登录状态无效");
        }
        Map<String, Object> row = rows.getFirst();
        return new AuthenticatedIdentity(
            ((Number) row.get("accountId")).longValue(),
            ((Number) row.get("memberId")).longValue(),
            shopId,
            String.valueOf(row.get("displayName")),
            "MEMBER",
            tokens
        );
    }

    private String normalizePhone(String phone) {
        try {
            return SmsVerificationPolicy.normalizePhone(phone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度应为 8 至 200 个字符");
        }
    }

    public record AuthenticatedIdentity(
        long accountId,
        long memberId,
        long shopId,
        String displayName,
        String role,
        V3AuthSessionService.SessionTokens tokens
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("account_id", accountId);
            data.put("member_id", memberId);
            data.put("shop_id", shopId);
            data.put("display_name", displayName);
            data.put("role", role);
            data.put("session_id", tokens.sessionId());
            data.put("access_token", tokens.accessToken());
            data.put("token_type", "Bearer");
            data.put("access_expires_at", tokens.accessExpiresAt().toString());
            data.put("refresh_token", tokens.refreshToken());
            data.put("refresh_expires_at", tokens.refreshExpiresAt().toString());
            return data;
        }
    }
}
