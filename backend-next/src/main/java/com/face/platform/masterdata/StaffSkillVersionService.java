package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class StaffSkillVersionService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public StaffSkillVersionService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<Map<String, Object>> currentSkills(
        TenantPrincipal principal,
        long shopId,
        long staffId
    ) {
        accessService.requireShopPermission(principal, shopId, "staff:view");
        return jdbcTemplate.queryForList(
            """
            SELECT ssv.id,
                   ssv.service_id AS serviceId,
                   si.service_code AS serviceCode,
                   si.name AS serviceName,
                   ssv.enabled,
                   ssv.custom_duration_minutes AS customDurationMinutes,
                   ssv.effective_from AS effectiveFrom,
                   ssv.version
            FROM staff_skill_version ssv
            JOIN service_item si
              ON si.id = ssv.service_id
             AND si.tenant_id = ssv.tenant_id
            WHERE ssv.tenant_id = ?
              AND ssv.shop_id = ?
              AND ssv.staff_id = ?
              AND ssv.effective_to IS NULL
            ORDER BY si.name, si.id
            """,
            principal.tenantId(),
            shopId,
            staffId
        );
    }

    @Transactional
    public void changeSkill(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        long serviceId,
        boolean enabled,
        Integer customDurationMinutes,
        LocalDateTime effectiveFrom,
        int expectedVersion
    ) {
        validateCommand(customDurationMinutes, effectiveFrom, expectedVersion);
        accessService.requireShopPermission(principal, shopId, "staff:manage");

        List<Map<String, Object>> staff = jdbcTemplate.queryForList(
            """
            SELECT id
            FROM staff
            WHERE tenant_id = ?
              AND shop_id = ?
              AND id = ?
            FOR UPDATE
            """,
            principal.tenantId(),
            shopId,
            staffId
        );
        if (staff.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "技师不存在或不属于当前门店");
        }

        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
            """
            SELECT id, version, effective_from
            FROM staff_skill_version
            WHERE tenant_id = ?
              AND shop_id = ?
              AND staff_id = ?
              AND service_id = ?
              AND effective_to IS NULL
            ORDER BY version DESC
            LIMIT 1
            FOR UPDATE
            """,
            principal.tenantId(),
            shopId,
            staffId,
            serviceId
        );

        int nextVersion = 1;
        if (!currentRows.isEmpty()) {
            Map<String, Object> current = currentRows.getFirst();
            long currentId = number(current.get("id")).longValue();
            int currentVersion = number(current.get("version")).intValue();
            LocalDateTime currentEffectiveFrom = localDateTime(current.get("effective_from"));
            if (currentVersion != expectedVersion) {
                throw new ApiException(HttpStatus.CONFLICT, "技师技能版本已变化，请刷新后重试");
            }
            if (!effectiveFrom.isAfter(currentEffectiveFrom)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "新技能版本生效时间必须晚于当前版本");
            }
            int closed = jdbcTemplate.update(
                """
                UPDATE staff_skill_version
                SET effective_to = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                  AND tenant_id = ?
                  AND version = ?
                  AND effective_to IS NULL
                """,
                effectiveFrom,
                principal.accountId(),
                currentId,
                principal.tenantId(),
                currentVersion
            );
            if (closed != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "技师技能版本已变化，请刷新后重试");
            }
            nextVersion = currentVersion + 1;
        } else if (expectedVersion != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "技师技能版本不存在，请刷新后重试");
        }

        jdbcTemplate.update(
            """
            INSERT INTO staff_skill_version (
                tenant_id, shop_id, staff_id, service_id, enabled,
                custom_duration_minutes, effective_from, version, created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            staffId,
            serviceId,
            enabled,
            customDurationMinutes,
            effectiveFrom,
            nextVersion,
            principal.accountId()
        );

        jdbcTemplate.update(
            """
            INSERT INTO staff_service (staff_id, service_id, custom_duration_minutes, enabled)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                custom_duration_minutes = VALUES(custom_duration_minutes),
                enabled = VALUES(enabled)
            """,
            staffId,
            serviceId,
            customDurationMinutes,
            enabled
        );
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            )
            VALUES (
                ?, ?, ?, 'STAFF_SKILL_VERSION_CHANGE', 'STAFF', ?,
                JSON_OBJECT('serviceId', ?, 'enabled', ?, 'version', ?)
            )
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            staffId,
            serviceId,
            enabled,
            nextVersion
        );
    }

    private void validateCommand(
        Integer customDurationMinutes,
        LocalDateTime effectiveFrom,
        int expectedVersion
    ) {
        if (effectiveFrom == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能版本生效时间不能为空");
        }
        if (customDurationMinutes != null && customDurationMinutes <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "自定义服务时长必须大于 0");
        }
        if (expectedVersion < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技能版本号无效");
        }
    }

    private Number number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("技师技能版本数据不完整");
        }
        return number;
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("技师技能版本生效时间无效");
    }
}
