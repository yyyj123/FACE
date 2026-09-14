package com.face.platform.v3.auth;

import com.face.platform.v3.api.V3ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class V3AuthSessionService {

    private static final int LOGIN_FAILURE_THRESHOLD = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    private final V3AuthSessionRepository repository;
    private final SessionTokenCodec codec;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String dummyPasswordHash;

    public V3AuthSessionService(
        V3AuthSessionRepository repository,
        SessionTokenCodec codec,
        PasswordEncoder passwordEncoder,
        Clock clock,
        @Value("${face.security.access-token-ttl:PT15M}") Duration accessTokenTtl,
        @Value("${face.security.refresh-token-ttl:P30D}") Duration refreshTokenTtl
    ) {
        this.repository = repository;
        this.codec = codec;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public SessionTokens login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        return authenticate(repository.findAccountForLogin(normalizedUsername), password);
    }

    @Transactional
    public SessionTokens loginMember(long tenantId, String identity, String password) {
        String normalizedIdentity = normalizeUsername(identity);
        return authenticate(
            repository.findMemberAccountForLogin(tenantId, normalizedIdentity),
            password
        );
    }

    @Transactional
    public SessionTokens loginAdmin(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        var account = repository.findAccountForLogin(normalizedUsername);
        if (account.isEmpty() || !repository.hasVisibleAdminRole(
            account.orElseThrow().accountId(),
            account.orElseThrow().tenantId()
        )) {
            passwordEncoder.matches(password == null ? "" : password, dummyPasswordHash);
            throw unauthenticated();
        }
        return authenticate(account, password);
    }

    @Transactional
    public SessionTokens issueMemberSession(long tenantId, String phone) {
        V3AuthSessionRepository.LoginAccount account = repository
            .findMemberAccountByPhone(tenantId, phone)
            .filter(value -> "ACTIVE".equals(value.status()))
            .orElseThrow(this::unauthenticated);
        return issue(account, Instant.now(clock));
    }

    @Transactional
    public void resetMemberPassword(long tenantId, String phone, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 200) {
            throw new V3ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "密码长度应为 8 至 200 个字符");
        }
        V3AuthSessionRepository.LoginAccount account = repository
            .findMemberAccountByPhone(tenantId, phone)
            .orElseThrow(this::unauthenticated);
        repository.updatePasswordAndRevoke(
            account.accountId(), tenantId, passwordEncoder.encode(rawPassword), Instant.now(clock)
        );
    }

    private SessionTokens authenticate(
        java.util.Optional<V3AuthSessionRepository.LoginAccount> account,
        String password
    ) {
        if (password == null || password.isBlank()) {
            throw unauthenticated();
        }
        Instant now = Instant.now(clock);
        if (account.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw unauthenticated();
        }
        V3AuthSessionRepository.LoginAccount value = account.orElseThrow();
        if (!"ACTIVE".equals(value.status())) {
            throw unauthenticated();
        }
        if (value.loginLocked()) {
            throw new V3ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "登录失败次数过多，请稍后再试"
            );
        }
        if (!passwordEncoder.matches(password, value.passwordHash())) {
            repository.recordLoginFailure(
                value.accountId(),
                now,
                LOGIN_FAILURE_THRESHOLD,
                LOGIN_LOCK_DURATION
            );
            throw unauthenticated();
        }

        return issue(value, now);
    }

    private SessionTokens issue(V3AuthSessionRepository.LoginAccount value, Instant now) {
        SessionTokenCodec.TokenPair pair = codec.issue();
        String sessionId = UUID.randomUUID().toString();
        Instant accessExpiresAt = now.plus(accessTokenTtl);
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        repository.recordLoginSuccess(value.accountId(), now);
        repository.create(new V3AuthSessionRepository.NewSession(
            sessionId,
            value.tenantId(),
            value.accountId(),
            value.homeShopId(),
            pair.accessTokenHash(),
            pair.refreshTokenHash(),
            accessExpiresAt,
            refreshExpiresAt,
            now
        ));
        return new SessionTokens(
            sessionId,
            pair.accessToken(),
            pair.refreshToken(),
            accessExpiresAt,
            refreshExpiresAt
        );
    }

    @Transactional
    public SessionTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw unauthenticated();
        }
        Instant now = Instant.now(clock);
        V3AuthSessionRepository.RefreshableSession session = repository
            .findRefreshableForUpdate(codec.hash(refreshToken.trim()), now)
            .orElseThrow(this::unauthenticated);
        SessionTokenCodec.TokenPair pair = codec.issue();
        Instant accessExpiresAt = now.plus(accessTokenTtl);
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        boolean rotated = repository.rotate(
            session.id(),
            session.version(),
            new V3AuthSessionRepository.Rotation(
                pair.accessTokenHash(),
                pair.refreshTokenHash(),
                accessExpiresAt,
                refreshExpiresAt,
                now
            )
        );
        if (!rotated) {
            throw unauthenticated();
        }
        return new SessionTokens(
            session.sessionId(),
            pair.accessToken(),
            pair.refreshToken(),
            accessExpiresAt,
            refreshExpiresAt
        );
    }

    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw unauthenticated();
        }
        boolean revoked = repository.revokeByAccessHash(
            codec.hash(accessToken.trim()),
            Instant.now(clock),
            "LOGOUT"
        );
        if (!revoked) {
            throw unauthenticated();
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw unauthenticated();
        }
        String normalized = username.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw unauthenticated();
        }
        return normalized;
    }

    private V3ApiException unauthenticated() {
        return new V3ApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "用户名或密码不正确，或登录状态已失效"
        );
    }

    public record SessionTokens(
        String sessionId,
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
    ) {
    }
}
