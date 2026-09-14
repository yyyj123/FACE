package com.face.platform.integration;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IntegrationClientApplicationService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final IntegrationCatalogQueryPort catalogQueryPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public IntegrationClientApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        IntegrationCatalogQueryPort catalogQueryPort,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.catalogQueryPort = catalogQueryPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<Map<String, Object>> list(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "integration:view");
        return jdbcTemplate.queryForList(
            """
            SELECT id, client_code AS clientCode, client_name AS clientName,
                   safe_description AS safeDescription, status, scopes_json AS scopes,
                   secret_version AS secretVersion, secret_prefix AS secretPrefix,
                   rate_limit_per_minute AS rateLimitPerMinute, version,
                   rotated_at AS rotatedAt, revoked_at AS revokedAt,
                   last_used_at AS lastUsedAt, created_at AS createdAt
            FROM integration_client
            WHERE tenant_id = ? AND shop_id = ?
            ORDER BY created_at DESC, id DESC
            """,
            principal.tenantId(), shopId
        );
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal, long shopId, String clientCode, String clientName,
        String safeDescription, List<String> scopes, int rateLimit,
        String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "integration:manage");
        validate(scopes, rateLimit);
        Secret secret = issueSecret();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO integration_client (
                    tenant_id, shop_id, client_code, client_name, safe_description,
                    scopes_json, secret_prefix, secret_hash, rate_limit_per_minute,
                    created_by, create_idempotency_key
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, code(clientCode));
            statement.setString(4, text(clientName, 120, "客户端名称"));
            statement.setString(5, optional(safeDescription, 500));
            statement.setString(6, json(scopes));
            statement.setString(7, secret.prefix());
            statement.setString(8, secret.hash());
            statement.setInt(9, rateLimit);
            statement.setLong(10, principal.accountId());
            statement.setString(11, idempotencyKey);
            return statement;
        }, keyHolder);
        long clientId = keyHolder.getKey().longValue();
        event(principal, shopId, clientId, "CREATED", 1, secret.prefix(), null, idempotencyKey, requestHash);
        Map<String, Object> result = new LinkedHashMap<>(detail(principal, shopId, clientId, "integration:view"));
        result.put("clientSecret", secret.raw());
        result.put("secretShownOnce", true);
        return result;
    }

    @Transactional
    public Map<String, Object> rotate(
        TenantPrincipal principal, long shopId, long clientId, int version,
        String reason, String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "integration:rotate");
        Map<String, Object> current = detail(principal, shopId, clientId, "integration:rotate");
        if (!"ACTIVE".equals(current.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "已撤销客户端不能轮换密钥");
        Secret secret = issueSecret();
        int nextSecretVersion = ((Number) current.get("secretVersion")).intValue() + 1;
        int changed = jdbcTemplate.update(
            """
            UPDATE integration_client
            SET secret_hash = ?, secret_prefix = ?, secret_version = ?,
                rotated_by = ?, rotated_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            secret.hash(), secret.prefix(), nextSecretVersion, principal.accountId(),
            clientId, principal.tenantId(), shopId, version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "客户端版本已变化，请刷新后重试");
        event(principal, shopId, clientId, "ROTATED", nextSecretVersion, secret.prefix(), reason, idempotencyKey, requestHash);
        Map<String, Object> result = new LinkedHashMap<>(detail(principal, shopId, clientId, "integration:view"));
        result.put("clientSecret", secret.raw());
        result.put("secretShownOnce", true);
        return result;
    }

    @Transactional
    public Map<String, Object> revoke(
        TenantPrincipal principal, long shopId, long clientId, int version,
        String reason, String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "integration:rotate");
        Map<String, Object> current = detail(principal, shopId, clientId, "integration:rotate");
        if (!"ACTIVE".equals(current.get("status"))) return current;
        int changed = jdbcTemplate.update(
            """
            UPDATE integration_client
            SET status = 'REVOKED', revoked_by = ?, revoked_at = CURRENT_TIMESTAMP(3),
                secret_hash = REPEAT('0', 64), version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            principal.accountId(), clientId, principal.tenantId(), shopId, version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "客户端版本已变化，请刷新后重试");
        event(principal, shopId, clientId, "REVOKED", ((Number) current.get("secretVersion")).intValue(),
            current.get("secretPrefix").toString(), text(reason, 500, "撤销原因"), idempotencyKey, requestHash);
        return detail(principal, shopId, clientId, "integration:view");
    }

    @Transactional
    public IntegrationPrincipal authenticate(
        String clientCode, String rawSecret, Instant suppliedAt, String nonce,
        String requestId, String method, String path, String requiredScope
    ) {
        if (!IntegrationClientPolicy.timestampAllowed(clock.instant(), suppliedAt))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "开放请求时间戳已失效");
        if (!IntegrationClientPolicy.validNonce(nonce))
            throw new ApiException(HttpStatus.BAD_REQUEST, "开放请求 Nonce 格式不正确");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, tenant_id AS tenantId, shop_id AS shopId, client_code AS clientCode,
                   status, scopes_json AS scopes, secret_hash AS secretHash,
                   rate_limit_per_minute AS rateLimit
            FROM integration_client
            WHERE client_code = ? AND status = 'ACTIVE'
            FOR UPDATE
            """,
            code(clientCode)
        );
        String actualHash = SessionTokenCodec.sha256(rawSecret);
        List<Map<String, Object>> matches = rows.stream()
            .filter(candidate -> constantEquals(candidate.get("secretHash").toString(), actualHash))
            .toList();
        if (matches.size() != 1) throw new ApiException(HttpStatus.UNAUTHORIZED, "集成客户端身份无效");
        Map<String, Object> row = matches.getFirst();
        Set<String> scopes = Set.copyOf(parseScopes(row.get("scopes")));
        if (!scopes.contains(requiredScope)) throw new ApiException(HttpStatus.FORBIDDEN, "集成客户端没有所需作用域");
        long clientId = number(row.get("id"));
        Integer replayed = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_request_log WHERE integration_client_id = ? AND nonce = ?",
            Integer.class, clientId, nonce
        );
        if (replayed != null && replayed > 0)
            throw new ApiException(HttpStatus.CONFLICT, "开放请求 Nonce 已使用");
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_request_log WHERE integration_client_id = ? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE)",
            Integer.class, clientId
        );
        if (count >= ((Number) row.get("rateLimit")).intValue())
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "开放接口请求频率超过客户端上限");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO integration_request_log (
                    tenant_id, shop_id, integration_client_id, request_id, nonce,
                    scope_code, http_method, request_path, result, response_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ALLOWED', 200)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, number(row.get("tenantId")));
            statement.setLong(2, number(row.get("shopId")));
            statement.setLong(3, clientId);
            statement.setString(4, requestId);
            statement.setString(5, nonce);
            statement.setString(6, requiredScope);
            statement.setString(7, method);
            statement.setString(8, path);
            return statement;
        }, keyHolder);
        jdbcTemplate.update("UPDATE integration_client SET last_used_at = CURRENT_TIMESTAMP(3) WHERE id = ?", clientId);
        return new IntegrationPrincipal(clientId, number(row.get("tenantId")), number(row.get("shopId")),
            row.get("clientCode").toString(), scopes, keyHolder.getKey().longValue());
    }

    public void finishRequest(IntegrationPrincipal principal, int status) {
        jdbcTemplate.update(
            "UPDATE integration_request_log SET result = ?, response_code = ? WHERE id = ? AND integration_client_id = ?",
            status < 400 ? "ALLOWED" : "DENIED", status, principal.requestLogId(), principal.clientId()
        );
    }

    public List<Map<String, Object>> catalog(IntegrationPrincipal principal) {
        if (!principal.scopes().contains("catalog:read")) throw new ApiException(HttpStatus.FORBIDDEN, "缺少项目目录读取作用域");
        return catalogQueryPort.activeServices(principal.tenantId(), principal.shopId());
    }

    private Map<String, Object> detail(TenantPrincipal principal, long shopId, long clientId, String permission) {
        accessService.requireShopPermission(principal, shopId, permission);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, client_code AS clientCode, client_name AS clientName,
                   safe_description AS safeDescription, status, scopes_json AS scopes,
                   secret_version AS secretVersion, secret_prefix AS secretPrefix,
                   rate_limit_per_minute AS rateLimitPerMinute, version,
                   rotated_at AS rotatedAt, revoked_at AS revokedAt,
                   last_used_at AS lastUsedAt, created_at AS createdAt
            FROM integration_client
            WHERE id = ? AND tenant_id = ? AND shop_id = ? LIMIT 1
            """,
            clientId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "集成客户端不存在或不在当前门店");
        return rows.getFirst();
    }

    private void event(
        TenantPrincipal principal, long shopId, long clientId, String action,
        int secretVersion, String prefix, String reason, String key, String hash
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO integration_client_event (
                tenant_id, shop_id, integration_client_id, action, secret_version,
                secret_prefix, actor_account_id, safe_reason, idempotency_key, request_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, clientId, action, secretVersion, prefix,
            principal.accountId(), reason == null ? null : optional(reason, 500), key, hash
        );
    }

    private Secret issueSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = "fc_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Secret(raw, raw.substring(0, 15), SessionTokenCodec.sha256(raw));
    }

    private void validate(List<String> scopes, int rateLimit) {
        if (!IntegrationClientPolicy.validScopes(scopes)) throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 catalog:read 低敏感作用域");
        if (rateLimit < 1 || rateLimit > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "每分钟限流必须在 1 至 60 之间");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "作用域格式不正确"); }
    }

    private List<String> parseScopes(Object value) {
        try { return objectMapper.readValue(value.toString(), STRING_LIST); }
        catch (JacksonException exception) { throw new IllegalStateException("客户端作用域数据损坏", exception); }
    }

    private String code(String value) {
        String safe = value == null ? "" : value.trim().toUpperCase();
        if (!safe.matches("[A-Z0-9_-]{3,40}")) throw new ApiException(HttpStatus.BAD_REQUEST, "客户端编码格式不正确");
        return safe;
    }

    private String text(String value, int max, String label) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空或过长");
        return safe;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "说明内容过长");
        return value.trim();
    }

    private boolean constantEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalStateException("集成客户端标识不完整");
        return number.longValue();
    }

    private record Secret(String raw, String prefix, String hash) { }
}
