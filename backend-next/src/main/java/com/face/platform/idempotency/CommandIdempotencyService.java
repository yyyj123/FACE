package com.face.platform.idempotency;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CommandIdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    public CommandIdempotencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void run(
        TenantPrincipal principal,
        String idempotencyKey,
        String operationCode,
        String requestHash,
        Runnable command
    ) {
        validate(idempotencyKey, operationCode, requestHash);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT operation_code, request_hash, status
            FROM idempotency_record
            WHERE tenant_id = ?
              AND idempotency_key = ?
            FOR UPDATE
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (!existing.isEmpty()) {
            Map<String, Object> record = existing.getFirst();
            if (!operationCode.equals(record.get("operation_code"))
                || !requestHash.equals(record.get("request_hash"))) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "幂等键已用于其他请求，请更换幂等键"
                );
            }
            if ("COMPLETED".equals(record.get("status"))) {
                return;
            }
            throw new ApiException(HttpStatus.CONFLICT, "相同请求正在处理中，请稍后重试");
        }

        try {
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_record (
                    tenant_id, idempotency_key, operation_code,
                    request_hash, status, expires_at
                )
                VALUES (?, ?, ?, ?, 'PROCESSING', DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR))
                """,
                principal.tenantId(),
                idempotencyKey,
                operationCode,
                requestHash
            );
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "相同请求正在处理中，请稍后重试");
        }

        command.run();
        int completed = jdbcTemplate.update(
            """
            UPDATE idempotency_record
            SET status = 'COMPLETED',
                response_code = 200,
                response_body = JSON_OBJECT('success', TRUE),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE tenant_id = ?
              AND idempotency_key = ?
              AND status = 'PROCESSING'
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (completed != 1) {
            throw new IllegalStateException("幂等记录完成状态更新失败");
        }
    }

    private void validate(String idempotencyKey, String operationCode, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少有效的 Idempotency-Key");
        }
        if (operationCode == null || operationCode.isBlank() || operationCode.length() > 80) {
            throw new IllegalArgumentException("幂等操作编码无效");
        }
        if (requestHash == null || !requestHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("请求摘要无效");
        }
    }
}
