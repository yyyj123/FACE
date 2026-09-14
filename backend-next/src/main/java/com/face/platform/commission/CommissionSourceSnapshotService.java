package com.face.platform.commission;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class CommissionSourceSnapshotService {

    private static final Set<String> SOURCE_TYPES = Set.of("SERVICE", "SALE");
    private static final Set<String> MANAGEMENT_ROLES = Set.of(
        "OWNER", "MANAGER", "FINANCE", "REGIONAL_MANAGER"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public CommissionSourceSnapshotService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long snapshotId
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:entry:view");
        Map<String, Object> snapshot = requireSnapshot(principal, shopId, snapshotId);
        if (selfOnly(principal)
            && number(snapshot.get("staffId")) != accessService.requireStaffId(principal)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "提成来源快照不存在");
        }
        return snapshot;
    }

    @Transactional
    public Map<String, Object> capture(
        TenantPrincipal principal,
        SnapshotCommand command
    ) {
        accessService.requireShopPermission(
            principal,
            command.shopId(),
            "commission:rule:manage"
        );
        return captureInternal(principal, command);
    }

    Map<String, Object> captureInternal(
        TenantPrincipal principal,
        SnapshotCommand command
    ) {
        String sourceType = sourceType(command.sourceType());
        if (command.sourceId() <= 0 || command.staffId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "提成来源或员工标识不正确");
        }
        String businessNo = required(command.businessNo(), "业务编号", 80);
        BigDecimal baseAmount = money(command.baseAmount());
        if (command.sourceOccurredAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "业务发生时间不能为空");
        }
        Map<String, Object> safeFacts = command.facts() == null
            ? Map.of()
            : new TreeMap<>(command.facts());
        String hash;
        try {
            hash = CommissionSourceSnapshotPolicy.hash(
                sourceType,
                command.sourceId(),
                command.staffId(),
                baseAmount,
                command.sourceOccurredAt(),
                safeFacts
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT id, snapshot_hash AS snapshotHash
            FROM commission_source_snapshot
            WHERE tenant_id = ? AND source_type = ? AND source_id = ? AND staff_id = ?
            FOR UPDATE
            """,
            principal.tenantId(),
            sourceType,
            command.sourceId(),
            command.staffId()
        );
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            if (!hash.equals(row.get("snapshotHash"))) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "同一提成来源已存在不同快照，禁止覆盖历史事实"
                );
            }
            return requireSnapshot(
                principal,
                command.shopId(),
                number(row.get("id"))
            );
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO commission_source_snapshot (
                        tenant_id, shop_id, source_type, source_id, staff_id,
                        member_id, business_no, base_amount, source_occurred_at,
                        snapshot_data, snapshot_hash, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, 'CAPTURED')
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, command.shopId());
                statement.setString(3, sourceType);
                statement.setLong(4, command.sourceId());
                statement.setLong(5, command.staffId());
                if (command.memberId() == null) {
                    statement.setObject(6, null);
                } else {
                    statement.setLong(6, command.memberId());
                }
                statement.setString(7, businessNo);
                statement.setBigDecimal(8, baseAmount);
                statement.setObject(9, command.sourceOccurredAt());
                statement.setString(10, writeJson(safeFacts));
                statement.setString(11, hash);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "提成来源快照正在被其他请求创建");
        }
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "提成来源快照创建失败");
        }
        long snapshotId = generated.longValue();
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id,
                after_data
            ) VALUES (?, ?, ?, 'COMMISSION_SOURCE_CAPTURE',
                      'COMMISSION_SOURCE_SNAPSHOT', ?,
                      JSON_OBJECT('sourceType', ?, 'sourceId', ?, 'staffId', ?))
            """,
            principal.tenantId(),
            command.shopId(),
            principal.accountId(),
            snapshotId,
            sourceType,
            command.sourceId(),
            command.staffId()
        );
        outboxEventService.append(
            principal,
            command.shopId(),
            "COMMISSION_SOURCE_SNAPSHOT",
            Long.toString(snapshotId),
            "CommissionSourceCaptured",
            Map.of("commissionSourceSnapshotId", snapshotId)
        );
        return requireSnapshot(principal, command.shopId(), snapshotId);
    }

    private Map<String, Object> requireSnapshot(
        TenantPrincipal principal,
        long shopId,
        long snapshotId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, source_type AS sourceType,
                   source_id AS sourceId, staff_id AS staffId,
                   member_id AS memberId, business_no AS businessNo,
                   base_amount AS baseAmount, source_occurred_at AS sourceOccurredAt,
                   snapshot_data AS snapshotData, snapshot_hash AS snapshotHash,
                   status, created_at AS createdAt
            FROM commission_source_snapshot
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            """,
            snapshotId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成来源快照不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("baseAmount", moneyText(result.get("baseAmount")));
        return result;
    }

    private boolean selfOnly(TenantPrincipal principal) {
        return principal.roles().contains("BEAUTICIAN")
            && principal.roles().stream().noneMatch(MANAGEMENT_ROLES::contains);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "提成来源快照格式不正确");
        }
    }

    private String sourceType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "提成来源类型不正确");
        }
        return normalized;
    }

    private String required(String value, String label, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空或过长");
        }
        return safe;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "提成基数格式不正确");
        }
        return value;
    }

    private String moneyText(Object value) {
        return (value instanceof BigDecimal decimal
            ? decimal
            : new BigDecimal(value.toString())).toPlainString();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    public record SnapshotCommand(
        long shopId,
        String sourceType,
        long sourceId,
        long staffId,
        Long memberId,
        String businessNo,
        BigDecimal baseAmount,
        Instant sourceOccurredAt,
        Map<String, Object> facts
    ) {
    }
}
