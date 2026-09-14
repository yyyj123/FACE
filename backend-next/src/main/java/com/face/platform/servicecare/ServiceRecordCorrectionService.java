package com.face.platform.servicecare;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class ServiceRecordCorrectionService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
        "serviceSummary",
        "nextVisitRecommendation",
        "skinType",
        "concerns",
        "observations",
        "homeCareAdvice",
        "nextRecommendedAt"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final ObjectMapper objectMapper;

    public ServiceRecordCorrectionService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> append(
        TenantPrincipal principal,
        long serviceRecordId,
        CareCorrectionRequest request
    ) {
        validate(request);
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "service_record:correct"
        );
        String idempotencyKey = request.idempotencyKey().trim();
        String requestHash = hash(serviceRecordId, request);
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, service_record_id AS serviceRecordId,
                   request_hash AS requestHash, created_at AS createdAt
            FROM service_record_correction
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (!replay.isEmpty()) {
            Map<String, Object> existing = replay.getFirst();
            if (number(existing.get("serviceRecordId")) != serviceRecordId
                || !requestHash.equals(existing.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "更正幂等键已用于不同请求");
            }
            return accepted(serviceRecordId, existing.get("id"), true);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT status, version
            FROM service_record
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            serviceRecordId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "护理记录不存在");
        }
        Map<String, Object> record = rows.getFirst();
        if (!"COMPLETED".equals(record.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已完成护理可以追加更正");
        }
        if (number(record.get("version")) != request.serviceRecordVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "护理记录版本已变化，请刷新后重试");
        }

        jdbcTemplate.update(
            """
            INSERT INTO service_record_correction (
                tenant_id, shop_id, service_record_id, base_version,
                correction_type, reason, corrected_fields,
                idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, ?, 'CARE_FACT', ?, CAST(? AS JSON), ?, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            serviceRecordId,
            request.serviceRecordVersion(),
            request.reason().trim(),
            writeJson(new TreeMap<>(request.correctedFields())),
            idempotencyKey,
            requestHash,
            principal.accountId()
        );
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action,
                entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, 'SERVICE_RECORD_CORRECTION_APPEND',
                      'SERVICE_RECORD', ?, CAST(? AS JSON))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            serviceRecordId,
            writeJson(Map.of("correctionAppended", true))
        );
        return accepted(serviceRecordId, null, false);
    }

    public List<Map<String, Object>> list(
        TenantPrincipal principal,
        long shopId,
        long serviceRecordId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "care_record:view");
        return jdbcTemplate.queryForList(
            """
            SELECT id, service_record_id AS serviceRecordId,
                   base_version AS baseVersion, correction_type AS correctionType,
                   reason, corrected_fields AS correctedFields,
                   created_by AS createdBy, created_at AS createdAt
            FROM service_record_correction
            WHERE tenant_id = ? AND shop_id = ? AND service_record_id = ?
            ORDER BY created_at, id
            """,
            principal.tenantId(),
            shopId,
            serviceRecordId
        );
    }

    private void validate(CareCorrectionRequest request) {
        if (request == null
            || request.shopId() == null
            || request.serviceRecordVersion() == null
            || request.reason() == null
            || request.reason().isBlank()
            || request.idempotencyKey() == null
            || request.idempotencyKey().isBlank()
            || request.correctedFields() == null
            || request.correctedFields().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "护理更正内容不完整");
        }
        if (request.correctedFields().keySet().stream().anyMatch(key -> !ALLOWED_FIELDS.contains(key))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "护理更正包含不支持的字段");
        }
    }

    private String hash(long serviceRecordId, CareCorrectionRequest request) {
        return SessionTokenCodec.sha256(
            serviceRecordId + "|"
                + request.serviceRecordVersion() + "|"
                + request.reason().trim() + "|"
                + writeJson(new TreeMap<>(request.correctedFields()))
        );
    }

    private Map<String, Object> accepted(
        long serviceRecordId,
        Object correctionId,
        boolean replayed
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("serviceRecordId", serviceRecordId);
        result.put("correctionId", correctionId);
        result.put("replayed", replayed);
        return result;
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("护理更正数据不完整");
        }
        return number.longValue();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "护理更正内容格式不正确");
        }
    }
}
