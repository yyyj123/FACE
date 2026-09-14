package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StaffScheduleService {

    private static final Set<String> TYPES = Set.of("WORK", "LEAVE", "BLOCKED");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public StaffScheduleService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<Map<String, Object>> list(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        accessService.requireShopPermission(principal, shopId, "staff:view");
        LocalDate safeFrom = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate safeTo = toDate == null ? safeFrom.plusDays(31) : toDate;
        if (safeTo.isBefore(safeFrom) || safeTo.isAfter(safeFrom.plusDays(93))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排班查询区间无效或超过94天");
        }
        return jdbcTemplate.queryForList(
            """
            SELECT id,
                   staff_id AS staffId,
                   schedule_date AS scheduleDate,
                   start_time AS startTime,
                   end_time AS endTime,
                   schedule_type AS scheduleType,
                   remark,
                   status,
                   version,
                   updated_at AS updatedAt
            FROM staff_schedule
            WHERE tenant_id = ?
              AND shop_id = ?
              AND staff_id = ?
              AND schedule_date BETWEEN ? AND ?
            ORDER BY schedule_date, start_time, id
            """,
            principal.tenantId(),
            shopId,
            staffId,
            safeFrom,
            safeTo
        );
    }

    @Transactional
    public void create(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        LocalDate scheduleDate,
        LocalTime startTime,
        LocalTime endTime,
        String scheduleType,
        String remark
    ) {
        String normalizedType = validateCommand(
            scheduleDate,
            startTime,
            endTime,
            scheduleType
        );
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

        Integer overlaps;
        if (startTime == null) {
            overlaps = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM staff_schedule
                WHERE tenant_id = ?
                  AND shop_id = ?
                  AND staff_id = ?
                  AND schedule_date = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                principal.tenantId(),
                shopId,
                staffId,
                scheduleDate
            );
        } else {
            overlaps = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM staff_schedule
                WHERE tenant_id = ?
                  AND shop_id = ?
                  AND staff_id = ?
                  AND schedule_date = ?
                  AND status = 'ACTIVE'
                  AND (
                       start_time IS NULL
                    OR end_time IS NULL
                    OR (start_time < ? AND end_time > ?)
                  )
                """,
                Integer.class,
                principal.tenantId(),
                shopId,
                staffId,
                scheduleDate,
                endTime,
                startTime
            );
        }
        if (overlaps != null && overlaps > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "技师排班时段发生冲突");
        }

        jdbcTemplate.update(
            """
            INSERT INTO staff_schedule (
                tenant_id, shop_id, staff_id, schedule_date,
                start_time, end_time, schedule_type, remark,
                status, version, created_by, updated_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            staffId,
            scheduleDate,
            startTime,
            endTime,
            normalizedType,
            remark,
            principal.accountId(),
            principal.accountId()
        );
        audit(principal, shopId, staffId, "STAFF_SCHEDULE_CREATE", normalizedType);
    }

    @Transactional
    public void deactivate(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        long scheduleId,
        int expectedVersion
    ) {
        accessService.requireShopPermission(principal, shopId, "staff:manage");
        int changed = jdbcTemplate.update(
            """
            UPDATE staff_schedule
            SET status = 'INACTIVE',
                version = version + 1,
                updated_by = ?,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
              AND tenant_id = ?
              AND shop_id = ?
              AND staff_id = ?
              AND version = ?
              AND status = 'ACTIVE'
            """,
            principal.accountId(),
            scheduleId,
            principal.tenantId(),
            shopId,
            staffId,
            expectedVersion
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "排班版本已变化或已停用，请刷新后重试");
        }
        audit(principal, shopId, staffId, "STAFF_SCHEDULE_DEACTIVATE", "INACTIVE");
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        String action,
        String state
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            )
            VALUES (?, ?, ?, ?, 'STAFF', ?, JSON_OBJECT('scheduleState', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            staffId,
            state
        );
    }

    private String validateCommand(
        LocalDate scheduleDate,
        LocalTime startTime,
        LocalTime endTime,
        String scheduleType
    ) {
        if (scheduleDate == null || scheduleType == null || scheduleType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排班日期和类型不能为空");
        }
        String normalizedType = scheduleType.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排班类型无效");
        }
        boolean bothNull = startTime == null && endTime == null;
        boolean bothPresent = startTime != null && endTime != null;
        if (!bothNull && !bothPresent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排班开始与结束时间必须同时填写");
        }
        if ("WORK".equals(normalizedType) && !bothPresent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "工作排班必须填写开始与结束时间");
        }
        if (bothPresent && !endTime.isAfter(startTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排班结束时间必须晚于开始时间");
        }
        return normalizedType;
    }
}
