package com.face.platform.marketing;

import com.face.platform.api.ApiException;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketingConsentService {

    public static final String CONSENT_TEXT_VERSION = "MARKETING-CONSENT-V1";
    public static final String CONSENT_TEXT =
        "我同意接收本店的营销活动信息。我可以随时撤回，撤回后不会再进入新的营销活动受众。";
    public static final String CONSENT_TEXT_SHA256 = RequestHash.of(CONSENT_TEXT);
    private static final List<String> CHANNELS = List.of("IN_APP", "SMS", "EMAIL", "WECHAT");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public MarketingConsentService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMine(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        Map<String, Map<String, Object>> current = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            """
            SELECT id, channel, status, consent_text_version AS consentTextVersion,
                   consent_text_sha256 AS consentTextSha256, version,
                   granted_at AS grantedAt, revoked_at AS revokedAt,
                   updated_at AS updatedAt
            FROM member_marketing_consent
            WHERE tenant_id = ? AND member_id = ?
            """,
            principal.tenantId(), memberId
        )) {
            current.put(row.get("channel").toString(), row);
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (String channel : CHANNELS) {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> row = current.get(channel);
            result.put("channel", channel);
            result.put("status", row == null ? "REVOKED" : row.get("status"));
            result.put("version", row == null ? 0 : row.get("version"));
            result.put("available", MarketingCampaignPolicy.channelAvailable(channel));
            result.put("consentTextVersion", CONSENT_TEXT_VERSION);
            result.put("consentTextSha256", CONSENT_TEXT_SHA256);
            if (row != null) {
                result.put("grantedAt", row.get("grantedAt"));
                result.put("revokedAt", row.get("revokedAt"));
                result.put("updatedAt", row.get("updatedAt"));
            }
            records.add(result);
        }
        return Map.of("consentText", CONSENT_TEXT, "records", records);
    }

    @Transactional
    public Map<String, Object> updateMine(
        TenantPrincipal principal,
        String channel,
        String status,
        int expectedVersion,
        String idempotencyKey,
        String requestHash
    ) {
        long memberId = accessService.requireMemberId(principal);
        String normalizedChannel = normalizeChannel(channel);
        String normalizedStatus = normalizeStatus(status);
        String safeKey = key(idempotencyKey);
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT request_hash FROM member_marketing_consent_history
            WHERE tenant_id = ? AND member_id = ? AND channel = ? AND idempotency_key = ?
            """,
            principal.tenantId(), memberId, normalizedChannel, safeKey
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().get("request_hash").toString())) {
                throw new ApiException(HttpStatus.CONFLICT, "该幂等键已用于不同的同意请求");
            }
            return current(principal, memberId, normalizedChannel);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, status, version FROM member_marketing_consent
            WHERE tenant_id = ? AND member_id = ? AND channel = ? FOR UPDATE
            """,
            principal.tenantId(), memberId, normalizedChannel
        );
        int currentVersion = rows.isEmpty() ? 0 : ((Number) rows.getFirst().get("version")).intValue();
        if (!MarketingConsentPolicy.versionMatches(currentVersion, expectedVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "营销同意版本冲突，请刷新后重试");
        }
        if (!rows.isEmpty() && normalizedStatus.equals(rows.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "营销同意状态没有变化");
        }
        int nextVersion = currentVersion + 1;
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                """
                INSERT INTO member_marketing_consent (
                    tenant_id, member_id, channel, status,
                    consent_text_version, consent_text_sha256, source,
                    version, granted_at, revoked_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, 'MEMBER_SELF', ?,
                    CASE WHEN ? = 'GRANTED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                    CASE WHEN ? = 'REVOKED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END, ?)
                """,
                principal.tenantId(), memberId, normalizedChannel, normalizedStatus,
                CONSENT_TEXT_VERSION, CONSENT_TEXT_SHA256, nextVersion,
                normalizedStatus, normalizedStatus, principal.accountId()
            );
        } else {
            int changed = jdbcTemplate.update(
                """
                UPDATE member_marketing_consent
                SET status = ?, consent_text_version = ?, consent_text_sha256 = ?,
                    source = 'MEMBER_SELF', version = version + 1,
                    granted_at = CASE WHEN ? = 'GRANTED' THEN CURRENT_TIMESTAMP(3) ELSE granted_at END,
                    revoked_at = CASE WHEN ? = 'REVOKED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                    updated_by = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                normalizedStatus, CONSENT_TEXT_VERSION, CONSENT_TEXT_SHA256,
                normalizedStatus, normalizedStatus, principal.accountId(),
                rows.getFirst().get("id"), principal.tenantId(), expectedVersion
            );
            if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "营销同意版本已变化");
        }
        jdbcTemplate.update(
            """
            INSERT INTO member_marketing_consent_history (
                tenant_id, member_id, channel, status, consent_version,
                consent_text_version, consent_text_sha256, source,
                actor_account_id, idempotency_key, request_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'MEMBER_SELF', ?, ?, ?)
            """,
            principal.tenantId(), memberId, normalizedChannel, normalizedStatus, nextVersion,
            CONSENT_TEXT_VERSION, CONSENT_TEXT_SHA256, principal.accountId(), safeKey, requestHash
        );
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, ?, 'MEMBER_MARKETING_CONSENT', ?,
                      JSON_OBJECT('channel', ?, 'status', ?, 'version', ?))
            """,
            principal.tenantId(), principal.homeShopId(), principal.accountId(),
            "MARKETING_CONSENT_" + normalizedStatus, memberId,
            normalizedChannel, normalizedStatus, nextVersion
        );
        return current(principal, memberId, normalizedChannel);
    }

    private Map<String, Object> current(TenantPrincipal principal, long memberId, String channel) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, channel, status, version,
                   consent_text_version AS consentTextVersion,
                   consent_text_sha256 AS consentTextSha256,
                   granted_at AS grantedAt, revoked_at AS revokedAt,
                   updated_at AS updatedAt
            FROM member_marketing_consent
            WHERE tenant_id = ? AND member_id = ? AND channel = ?
            """,
            principal.tenantId(), memberId, channel
        );
        if (rows.isEmpty()) {
            return Map.of(
                "channel", channel, "status", "REVOKED", "version", 0,
                "available", MarketingCampaignPolicy.channelAvailable(channel),
                "consentTextVersion", CONSENT_TEXT_VERSION,
                "consentTextSha256", CONSENT_TEXT_SHA256
            );
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("available", MarketingCampaignPolicy.channelAvailable(channel));
        return result;
    }

    private String normalizeChannel(String value) {
        String channel = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!MarketingConsentPolicy.validChannel(channel)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "营销通道不正确");
        }
        return channel;
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("GRANTED", "REVOKED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "同意状态不正确");
        }
        return status;
    }

    private String key(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > 100 || !safe.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key 格式不正确");
        }
        return safe;
    }
}
