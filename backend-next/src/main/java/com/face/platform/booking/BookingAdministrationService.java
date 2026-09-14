package com.face.platform.booking;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingAdministrationService {

    private static final int SLOT_INTERVAL_MINUTES = 60;
    private static final Set<String> LATE_POLICIES = Set.of(
        "FULL_REFUND", "FIXED_FEE", "PERCENTAGE_FEE", "DEDUCT_CARD_TIMES", "NON_REFUNDABLE"
    );
    private static final Set<String> DATE_TYPES = Set.of("WORK", "BREAK", "LEAVE", "BLOCKED", "STOP_BOOKING");
    private static final Set<String> RULE_TYPES = Set.of("WORK", "BREAK", "STOP_BOOKING");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final ShopContextService shopContextService;

    public BookingAdministrationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        ShopContextService shopContextService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.shopContextService = shopContextService;
    }

    public List<Map<String, Object>> policies(TenantPrincipal principal, Long requestedShopId) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:view");
        return jdbcTemplate.queryForList(
            """
            SELECT id, name, duration_minutes AS durationMinutes,
                   slot_interval_minutes AS slotIntervalMinutes,
                   buffer_before_minutes AS bufferBeforeMinutes,
                   buffer_after_minutes AS bufferAfterMinutes,
                   minimum_advance_minutes AS minimumAdvanceMinutes,
                   same_day_booking_allowed AS sameDayBookingAllowed,
                   free_cancel_minutes AS freeCancelMinutes,
                   reschedule_cutoff_minutes AS rescheduleCutoffMinutes,
                   max_reschedules AS maxReschedules,
                   late_cancel_policy AS lateCancelPolicy,
                   late_cancel_value AS lateCancelValue,
                   booking_terms_version AS termsVersion,
                   booking_notice AS bookingNotice, status, updated_at AS updatedAt
            FROM service_item
            WHERE tenant_id = ? AND shop_id = ?
            ORDER BY status = 'ACTIVE' DESC, name, id
            """,
            principal.tenantId(), shop.shopId()
        );
    }

    @Transactional
    public Map<String, Object> updatePolicy(
        TenantPrincipal principal,
        Long requestedShopId,
        long serviceId,
        PolicyCommand command
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        validatePolicy(command);
        int changed = jdbcTemplate.update(
            """
            UPDATE service_item
            SET slot_interval_minutes = ?, buffer_before_minutes = ?, buffer_after_minutes = ?,
                minimum_advance_minutes = ?, same_day_booking_allowed = ?,
                free_cancel_minutes = ?, reschedule_cutoff_minutes = ?, max_reschedules = ?,
                late_cancel_policy = ?, late_cancel_value = ?, booking_notice = ?,
                booking_terms_version = booking_terms_version + 1,
                booking_policy_updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            """,
            command.slotIntervalMinutes(), command.bufferBeforeMinutes(), command.bufferAfterMinutes(),
            command.minimumAdvanceMinutes(), command.sameDayBookingAllowed(),
            command.freeCancelMinutes(), command.rescheduleCutoffMinutes(), command.maxReschedules(),
            normalize(command.lateCancelPolicy()), command.lateCancelValue(), trim(command.bookingNotice()),
            principal.accountId(), serviceId, principal.tenantId(), shop.shopId()
        );
        if (changed != 1) throw new ApiException(HttpStatus.NOT_FOUND, "护理项目不存在");
        audit(principal, shop.shopId(), "BOOKING_POLICY_UPDATE", "SERVICE_ITEM", serviceId);
        return Map.of("id", serviceId, "updated", true, "terms_version_incremented", true);
    }

    public Map<String, Object> schedules(
        TenantPrincipal principal,
        Long requestedShopId,
        LocalDate from,
        LocalDate to
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:view");
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 31) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排班查询范围最多 31 天");
        }
        List<Map<String, Object>> facts = jdbcTemplate.queryForList(
            """
            SELECT ss.id, ss.staff_id AS staffId, st.name AS staffName,
                   ss.schedule_date AS scheduleDate, ss.start_time AS startTime,
                   ss.end_time AS endTime, ss.schedule_type AS scheduleType, ss.remark,
                   ss.status, ss.version
            FROM staff_schedule ss
            JOIN staff st ON st.id = ss.staff_id AND st.tenant_id = ss.tenant_id
            WHERE ss.tenant_id = ? AND ss.shop_id = ? AND ss.schedule_date BETWEEN ? AND ?
            ORDER BY ss.schedule_date, ss.start_time, st.name, ss.id
            """,
            principal.tenantId(), shop.shopId(), Date.valueOf(from), Date.valueOf(to)
        );
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
            """
            SELECT r.id, r.staff_id AS staffId, st.name AS staffName,
                   r.day_of_week AS dayOfWeek, r.start_time AS startTime,
                   r.end_time AS endTime, r.rule_type AS ruleType,
                   r.effective_from AS effectiveFrom, r.effective_to AS effectiveTo,
                   r.status, r.version
            FROM staff_schedule_rule r
            JOIN staff st ON st.id = r.staff_id AND st.tenant_id = r.tenant_id
            WHERE r.tenant_id = ? AND r.shop_id = ?
              AND r.effective_from <= ? AND (r.effective_to IS NULL OR r.effective_to >= ?)
            ORDER BY st.name, r.day_of_week, r.start_time, r.id
            """,
            principal.tenantId(), shop.shopId(), Date.valueOf(to), Date.valueOf(from)
        );
        return Map.of("from", from.toString(), "to", to.toString(), "facts", facts, "rules", rules);
    }

    @Transactional
    public Map<String, Object> addDateFact(
        TenantPrincipal principal,
        Long requestedShopId,
        DateFactCommand command
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        String type = normalize(command.scheduleType());
        if (!DATE_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的日期排班类型");
        requireStaff(principal.tenantId(), shop.shopId(), command.staffId());
        validateTimes(type, command.startTime(), command.endTime());
        requireDateFactAvailable(
            principal.tenantId(), shop.shopId(), command.staffId(), command.scheduleDate(),
            command.startTime(), command.endTime(), type, null
        );
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO staff_schedule (
                    tenant_id, shop_id, staff_id, schedule_date,
                    start_time, end_time, schedule_type, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shop.shopId());
            statement.setLong(3, command.staffId());
            statement.setDate(4, Date.valueOf(command.scheduleDate()));
            if (command.startTime() == null) statement.setNull(5, java.sql.Types.TIME);
            else statement.setTime(5, Time.valueOf(command.startTime()));
            if (command.endTime() == null) statement.setNull(6, java.sql.Types.TIME);
            else statement.setTime(6, Time.valueOf(command.endTime()));
            statement.setString(7, type);
            statement.setString(8, trim(command.remark()));
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        audit(principal, shop.shopId(), "SCHEDULE_FACT_CREATE", "STAFF_SCHEDULE", id);
        return Map.of("id", id, "created", true);
    }

    @Transactional
    public Map<String, Object> updateDateFact(
        TenantPrincipal principal,
        Long requestedShopId,
        long factId,
        DateFactCommand command,
        int expectedVersion
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        String type = normalize(command.scheduleType());
        if (!DATE_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的日期排班类型");
        requireStaff(principal.tenantId(), shop.shopId(), command.staffId());
        validateTimes(type, command.startTime(), command.endTime());
        Map<String, Object> existing = requireDateFact(
            principal.tenantId(), shop.shopId(), factId, expectedVersion
        );
        if (((Number) existing.get("staffId")).longValue() != command.staffId()) {
            throw new ApiException(HttpStatus.CONFLICT, "排班所属技师不能直接更换，请停用后重新建立");
        }
        requireDateFactAvailable(
            principal.tenantId(), shop.shopId(), command.staffId(), command.scheduleDate(),
            command.startTime(), command.endTime(), type, factId
        );
        if (hasOccupyingAppointment(
            principal.tenantId(), shop.shopId(), command.staffId(),
            dateValue(existing.get("scheduleDate")),
            timeValue(existing.get("startTime")), timeValue(existing.get("endTime"))
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "该排班范围已有预约，不能直接调整，请先处理预约");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE staff_schedule
            SET schedule_date = ?, start_time = ?, end_time = ?, schedule_type = ?, remark = ?,
                version = version + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            command.scheduleDate(), command.startTime(), command.endTime(), type, trim(command.remark()),
            principal.accountId(), factId, principal.tenantId(), shop.shopId(), expectedVersion
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "排班已被修改，请刷新后重试");
        audit(principal, shop.shopId(), "SCHEDULE_FACT_UPDATE", "STAFF_SCHEDULE", factId);
        return Map.of("id", factId, "updated", true, "version", expectedVersion + 1);
    }

    @Transactional
    public Map<String, Object> deactivateDateFact(
        TenantPrincipal principal,
        Long requestedShopId,
        long factId,
        int expectedVersion
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        Map<String, Object> existing = requireDateFact(
            principal.tenantId(), shop.shopId(), factId, expectedVersion
        );
        if ("WORK".equals(existing.get("scheduleType")) && hasOccupyingAppointment(
            principal.tenantId(), shop.shopId(), ((Number) existing.get("staffId")).longValue(),
            dateValue(existing.get("scheduleDate")),
            timeValue(existing.get("startTime")), timeValue(existing.get("endTime"))
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "该工作排班已有预约，不能删除");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE staff_schedule
            SET status = 'INACTIVE', version = version + 1,
                updated_by = ?, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            principal.accountId(), factId, principal.tenantId(), shop.shopId(), expectedVersion
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "排班已被修改或停用，请刷新后重试");
        audit(principal, shop.shopId(), "SCHEDULE_FACT_DEACTIVATE", "STAFF_SCHEDULE", factId);
        return Map.of("id", factId, "status", "INACTIVE", "version", expectedVersion + 1);
    }

    @Transactional
    public Map<String, Object> addRecurringRule(
        TenantPrincipal principal,
        Long requestedShopId,
        RecurringRuleCommand command
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        String type = normalize(command.ruleType());
        if (!RULE_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的周期排班类型");
        if (command.dayOfWeek() < 1 || command.dayOfWeek() > 7) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "星期必须为 1 至 7");
        }
        validateTimes(type, command.startTime(), command.endTime());
        if (command.effectiveFrom() == null
            || command.effectiveTo() != null && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "生效日期范围不正确");
        }
        requireStaff(principal.tenantId(), shop.shopId(), command.staffId());
        requireRecurringRuleAvailable(
            principal.tenantId(), shop.shopId(), command.staffId(), command.dayOfWeek(),
            command.startTime(), command.endTime(), type,
            command.effectiveFrom(), command.effectiveTo()
        );
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO staff_schedule_rule (
                    tenant_id, shop_id, staff_id, day_of_week, start_time, end_time,
                    rule_type, effective_from, effective_to, status, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shop.shopId());
            statement.setLong(3, command.staffId());
            statement.setInt(4, command.dayOfWeek());
            statement.setTime(5, Time.valueOf(command.startTime()));
            statement.setTime(6, Time.valueOf(command.endTime()));
            statement.setString(7, type);
            statement.setDate(8, Date.valueOf(command.effectiveFrom()));
            if (command.effectiveTo() == null) statement.setNull(9, java.sql.Types.DATE);
            else statement.setDate(9, Date.valueOf(command.effectiveTo()));
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        audit(principal, shop.shopId(), "SCHEDULE_RULE_CREATE", "STAFF_SCHEDULE_RULE", id);
        return Map.of("id", id, "created", true);
    }

    @Transactional
    public Map<String, Object> deactivateRule(
        TenantPrincipal principal,
        Long requestedShopId,
        long ruleId,
        int version
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        int changed = jdbcTemplate.update(
            """
            UPDATE staff_schedule_rule
            SET status = 'INACTIVE', version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'
            """,
            principal.accountId(), ruleId, principal.tenantId(), shop.shopId(), version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "排班规则已被修改或停用");
        audit(principal, shop.shopId(), "SCHEDULE_RULE_DEACTIVATE", "STAFF_SCHEDULE_RULE", ruleId);
        return Map.of("id", ruleId, "status", "INACTIVE", "version", version + 1);
    }

    @Transactional
    public Map<String, Object> activateRule(
        TenantPrincipal principal,
        Long requestedShopId,
        long ruleId,
        int version
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:manage");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT staff_id AS staffId, day_of_week AS dayOfWeek,
                   start_time AS startTime, end_time AS endTime, rule_type AS ruleType,
                   effective_from AS effectiveFrom, effective_to AS effectiveTo
            FROM staff_schedule_rule
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'INACTIVE'
            FOR UPDATE
            """,
            ruleId, principal.tenantId(), shop.shopId(), version
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "排班规则已被修改或已经启用，请刷新后重试");
        }
        Map<String, Object> rule = rows.getFirst();
        long staffId = ((Number) rule.get("staffId")).longValue();
        int dayOfWeek = ((Number) rule.get("dayOfWeek")).intValue();
        LocalTime startTime = timeValue(rule.get("startTime"));
        LocalTime endTime = timeValue(rule.get("endTime"));
        String ruleType = String.valueOf(rule.get("ruleType"));
        LocalDate effectiveFrom = dateValue(rule.get("effectiveFrom"));
        LocalDate effectiveTo = rule.get("effectiveTo") == null
            ? null
            : dateValue(rule.get("effectiveTo"));

        requireStaff(principal.tenantId(), shop.shopId(), staffId);
        requireRecurringRuleAvailable(
            principal.tenantId(), shop.shopId(), staffId, dayOfWeek,
            startTime, endTime, ruleType, effectiveFrom, effectiveTo
        );
        int changed = jdbcTemplate.update(
            """
            UPDATE staff_schedule_rule
            SET status = 'ACTIVE', version = version + 1,
                updated_by = ?, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'INACTIVE'
            """,
            principal.accountId(), ruleId, principal.tenantId(), shop.shopId(), version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "排班规则已被修改，请刷新后重试");
        }
        audit(principal, shop.shopId(), "SCHEDULE_RULE_ACTIVATE", "STAFF_SCHEDULE_RULE", ruleId);
        return Map.of("id", ruleId, "status", "ACTIVE", "version", version + 1);
    }

    private void validatePolicy(PolicyCommand command) {
        if (command.slotIntervalMinutes() != SLOT_INTERVAL_MINUTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "预约开始时间统一按 60 分钟间隔开放");
        }
        if (command.bufferBeforeMinutes() < 0 || command.bufferBeforeMinutes() > 240
            || command.bufferAfterMinutes() < 0 || command.bufferAfterMinutes() > 240
            || command.minimumAdvanceMinutes() < 0 || command.minimumAdvanceMinutes() > 43200
            || command.freeCancelMinutes() < 0 || command.freeCancelMinutes() > 43200
            || command.rescheduleCutoffMinutes() < 0 || command.rescheduleCutoffMinutes() > 43200
            || command.maxReschedules() < 0 || command.maxReschedules() > 20
            || !LATE_POLICIES.contains(normalize(command.lateCancelPolicy()))
            || command.lateCancelValue() == null || command.lateCancelValue().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "预约规则参数不正确");
        }
    }

    private void validateTimes(String type, LocalTime start, LocalTime end) {
        if ((start == null || end == null) && !Set.of("LEAVE", "STOP_BOOKING").contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该排班类型必须填写开始和结束时间");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }

    private void requireStaff(long tenantId, long shopId, long staffId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM staff st
            JOIN staff_shop_assignment ssa ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
            WHERE st.id = ? AND st.tenant_id = ? AND st.status = 'ACTIVE'
              AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
            """,
            Integer.class, staffId, tenantId, shopId
        );
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "技师不存在或不在当前门店");
    }

    private void requireDateFactAvailable(
        long tenantId,
        long shopId,
        long staffId,
        LocalDate date,
        LocalTime start,
        LocalTime end,
        String type,
        Long excludedId
    ) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT id, start_time AS startTime, end_time AS endTime, schedule_type AS scheduleType
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND schedule_date = ?
              AND status = 'ACTIVE' AND (? IS NULL OR id <> ?)
            """,
            tenantId, shopId, staffId, date, excludedId, excludedId
        );
        boolean work = "WORK".equals(type);
        for (Map<String, Object> row : existing) {
            boolean otherWork = "WORK".equals(row.get("scheduleType"));
            if (work != otherWork) continue;
            LocalTime otherStart = timeValue(row.get("startTime"));
            LocalTime otherEnd = timeValue(row.get("endTime"));
            if (timeOverlaps(start, end, otherStart, otherEnd)) {
                throw new ApiException(HttpStatus.CONFLICT, work
                    ? "工作排班与已有工作时段重叠"
                    : "休息、请假或停止预约时段发生重叠");
            }
        }
        if (!work && hasOccupyingAppointment(tenantId, shopId, staffId, date, start, end)) {
            throw new ApiException(HttpStatus.CONFLICT, "该时段已有预约，不能设置休息或请假");
        }
    }

    private void requireRecurringRuleAvailable(
        long tenantId,
        long shopId,
        long staffId,
        int dayOfWeek,
        LocalTime start,
        LocalTime end,
        String type,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        boolean work = "WORK".equals(type);
        Integer overlaps = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM staff_schedule_rule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND day_of_week = ?
              AND status = 'ACTIVE'
              AND ((rule_type = 'WORK') = ?)
              AND start_time < ? AND end_time > ?
              AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
            """,
            Integer.class,
            tenantId, shopId, staffId, dayOfWeek, work,
            end, start, effectiveTo == null ? LocalDate.of(9999, 12, 31) : effectiveTo, effectiveFrom
        );
        if (overlaps != null && overlaps > 0) {
            throw new ApiException(HttpStatus.CONFLICT, work
                ? "周期工作时段与已有规则重叠"
                : "周期休息或停止预约时段与已有规则重叠");
        }
        if (work) return;
        Integer appointments = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM appointment
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE')
              AND WEEKDAY(occupied_start_at) + 1 = ?
              AND DATE(occupied_start_at) >= ?
              AND (? IS NULL OR DATE(occupied_start_at) <= ?)
              AND TIME(occupied_start_at) < ? AND TIME(occupied_end_at) > ?
            """,
            Integer.class,
            tenantId, shopId, staffId, dayOfWeek, effectiveFrom, effectiveTo, effectiveTo, end, start
        );
        if (appointments != null && appointments > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该周期时段已有预约，不能设置休息或停止预约");
        }
    }

    private Map<String, Object> requireDateFact(
        long tenantId, long shopId, long factId, int expectedVersion
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, staff_id AS staffId, schedule_date AS scheduleDate,
                   start_time AS startTime, end_time AS endTime, schedule_type AS scheduleType
            FROM staff_schedule
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'
            FOR UPDATE
            """,
            factId, tenantId, shopId, expectedVersion
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "排班已被修改或停用，请刷新后重试");
        return rows.getFirst();
    }

    private boolean hasOccupyingAppointment(
        long tenantId,
        long shopId,
        long staffId,
        LocalDate date,
        LocalTime start,
        LocalTime end
    ) {
        LocalDateTime rangeStart = date.atTime(start == null ? LocalTime.MIN : start);
        LocalDateTime rangeEnd = end == null ? date.plusDays(1).atStartOfDay() : date.atTime(end);
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM appointment
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE')
              AND occupied_start_at < ? AND occupied_end_at > ?
            """,
            Integer.class, tenantId, shopId, staffId, rangeEnd, rangeStart
        );
        return count != null && count > 0;
    }

    private boolean timeOverlaps(
        LocalTime leftStart, LocalTime leftEnd, LocalTime rightStart, LocalTime rightEnd
    ) {
        LocalTime aStart = leftStart == null ? LocalTime.MIN : leftStart;
        LocalTime aEnd = leftEnd == null ? LocalTime.MAX : leftEnd;
        LocalTime bStart = rightStart == null ? LocalTime.MIN : rightStart;
        LocalTime bEnd = rightEnd == null ? LocalTime.MAX : rightEnd;
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private LocalDate dateValue(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date sqlDate) return sqlDate.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalTime timeValue(Object value) {
        if (value == null) return null;
        if (value instanceof LocalTime localTime) return localTime;
        if (value instanceof Time sqlTime) return sqlTime.toLocalTime();
        return LocalTime.parse(String.valueOf(value));
    }

    private void audit(TenantPrincipal principal, long shopId, String action, String type, long id) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (tenant_id, shop_id, account_id, action, entity_type, entity_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, principal.accountId(), action, type, id
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PolicyCommand(
        int slotIntervalMinutes,
        int bufferBeforeMinutes,
        int bufferAfterMinutes,
        int minimumAdvanceMinutes,
        boolean sameDayBookingAllowed,
        int freeCancelMinutes,
        int rescheduleCutoffMinutes,
        int maxReschedules,
        String lateCancelPolicy,
        java.math.BigDecimal lateCancelValue,
        String bookingNotice
    ) {
    }

    public record DateFactCommand(
        long staffId,
        LocalDate scheduleDate,
        LocalTime startTime,
        LocalTime endTime,
        String scheduleType,
        String remark
    ) {
    }

    public record RecurringRuleCommand(
        long staffId,
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String ruleType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
    }
}
