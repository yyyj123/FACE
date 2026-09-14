package com.face.platform.outbox;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(
        TenantPrincipal principal,
        Long shopId,
        String aggregateType,
        String aggregateId,
        String eventType,
        Map<String, Object> payload
    ) {
        appendAndReturnId(principal, shopId, aggregateType, aggregateId, eventType, payload);
    }

    public String appendAndReturnId(
        TenantPrincipal principal,
        Long shopId,
        String aggregateType,
        String aggregateId,
        String eventType,
        Map<String, Object> payload
    ) {
        requireIdentifierOnlyPayload(payload);
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            INSERT INTO outbox_event (
                event_id, tenant_id, shop_id, aggregate_type,
                aggregate_id, event_type, payload
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON))
            """,
            eventId,
            principal.tenantId(),
            shopId,
            requiredText(aggregateType, "事件聚合类型"),
            requiredText(aggregateId, "事件聚合编号"),
            requiredText(eventType, "事件类型"),
            writeJson(payload)
        );
        return eventId;
    }

    private void requireIdentifierOnlyPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "事件载荷必须包含业务标识");
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (key == null || !(key.endsWith("Id") || key.endsWith("Ids"))) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "事件载荷禁止包含敏感业务正文，仅允许业务标识"
                );
            }
            Object value = entry.getValue();
            if (!(value instanceof Number)
                && !(value instanceof String)
                && !(value instanceof Iterable<?>)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "事件业务标识格式不正确");
            }
        }
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "事件载荷格式不正确");
        }
    }
}
