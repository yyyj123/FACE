package com.face.platform.booking;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeeklyScheduleService {

    private static final List<String> OCCUPYING_STATUSES =
        List.of("PENDING", "CONFIRMED", "CHECKED_IN", "IN_SERVICE");
    private static final LocalTime DISPLAY_START = LocalTime.of(8, 0);
    private static final LocalTime DISPLAY_END = LocalTime.of(22, 0);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final ShopContextService shopContextService;
    private final TenantAccessService tenantAccessService;
    private final Clock clock;

    public WeeklyScheduleService(
        JdbcTemplate jdbcTemplate,
        ShopContextService shopContextService,
        TenantAccessService tenantAccessService,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.shopContextService = shopContextService;
        this.tenantAccessService = tenantAccessService;
        this.clock = clock;
    }

    public Map<String, Object> publicWeek(Long requestedShopId, LocalDate requestedFrom) {
        var shop = shopContextService.requirePublicShop(requestedShopId);
        LocalDate today = today();
        LocalDate from = requestedFrom == null ? today : requestedFrom;
        if (from.isBefore(today) || from.plusDays(6).isAfter(today.plusDays(30))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "公开排班仅可查询今天起未来 30 天");
        }
        return week(shop.tenantId(), shop.shopId(), from, false);
    }

    public Map<String, Object> adminWeek(
        TenantPrincipal principal,
        Long requestedShopId,
        LocalDate requestedFrom
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "schedule:view");
        LocalDate from = requestedFrom == null ? today() : requestedFrom;
        return week(principal.tenantId(), shop.shopId(), from, true);
    }

    private Map<String, Object> week(long tenantId, long shopId, LocalDate from, boolean admin) {
        LocalDate to = from.plusDays(6);
        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.plusDays(1).atStartOfDay();

        List<StaffRow> staff = staff(tenantId, shopId, from, to);
        List<ScheduleRow> facts = dateFacts(tenantId, shopId, from, to);
        List<RuleRow> rules = recurringRules(tenantId, shopId, from, to);
        List<BusyRow> appointments = appointments(tenantId, shopId, rangeStart, rangeEnd);
        List<BusyRow> locks = locks(tenantId, shopId, rangeStart, rangeEnd);

        List<Map<String, Object>> staffRows = new ArrayList<>();
        for (StaffRow person : staff) {
            List<Map<String, Object>> days = new ArrayList<>();
            for (int offset = 0; offset < 7; offset++) {
                LocalDate date = from.plusDays(offset);
                days.add(day(person, date, facts, rules, appointments, locks, admin));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", person.id());
            item.put("name", person.name());
            item.put("levelName", person.levelName());
            item.put("avatarUrl", person.avatarUrl());
            item.put("days", days);
            staffRows.add(item);
        }

        List<Map<String, Object>> dates = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = from.plusDays(offset);
            dates.add(Map.of(
                "date", date.toString(),
                "weekday", weekday(date.getDayOfWeek()),
                "today", date.equals(today())
            ));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shopId", shopId);
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("maxDate", today().plusDays(30).toString());
        result.put("dates", dates);
        result.put("staff", staffRows);
        return result;
    }

    private Map<String, Object> day(
        StaffRow person,
        LocalDate date,
        List<ScheduleRow> facts,
        List<RuleRow> rules,
        List<BusyRow> appointments,
        List<BusyRow> locks,
        boolean admin
    ) {
        List<ScheduleRow> personFacts = facts.stream()
            .filter(row -> row.staffId() == person.id() && row.date().equals(date))
            .toList();
        List<RuleRow> personRules = rules.stream()
            .filter(row -> row.staffId() == person.id()
                && row.dayOfWeek() == date.getDayOfWeek().getValue()
                && !date.isBefore(row.effectiveFrom())
                && (row.effectiveTo() == null || !date.isAfter(row.effectiveTo())))
            .toList();

        List<Range> specificWork = ranges(personFacts, "WORK");
        List<Range> work = specificWork.isEmpty() ? ranges(personRules, "WORK") : specificWork;
        List<Range> blocks = new ArrayList<>();
        blocks.addAll(blockRanges(personFacts));
        blocks.addAll(blockRanges(personRules));
        work = merge(work);
        blocks = merge(blocks);

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<BusyRow> dayAppointments = appointments.stream()
            .filter(row -> row.staffId() == person.id()
                && row.start().isBefore(dayEnd) && row.end().isAfter(dayStart))
            .toList();
        List<Range> busySource = new ArrayList<>();
        dayAppointments.forEach(row -> busySource.add(toTimeRange(row, date)));
        locks.stream()
            .filter(row -> row.staffId() == person.id()
                && row.start().isBefore(dayEnd) && row.end().isAfter(dayStart))
            .map(row -> toTimeRange(row, date))
            .forEach(busySource::add);
        List<Range> busy = merge(busySource);

        int workMinutes = minutes(work);
        int blockedMinutes = overlapMinutes(work, blocks);
        int reservedMinutes = overlapMinutes(work, busy);
        int unavailableMinutes = overlapMinutes(work, merge(concat(blocks, busy)));
        int availableMinutes = Math.max(0, workMinutes - unavailableMinutes);
        String scheduleStatus = scheduleStatus(personFacts, work);
        String availabilityStatus = availabilityStatus(
            scheduleStatus, workMinutes, availableMinutes, blockedMinutes, reservedMinutes
        );

        List<Map<String, Object>> slots = new ArrayList<>();
        for (LocalTime start = DISPLAY_START; start.isBefore(DISPLAY_END); start = start.plusHours(1)) {
            LocalTime end = start.plusHours(1);
            LocalTime slotStart = start;
            LocalTime slotEnd = end;
            Range slot = new Range(start, end);
            int slotWork = overlapMinutes(List.of(slot), work);
            int slotUnavailable = overlapMinutes(List.of(slot), merge(concat(blocks, busy)));
            Map<String, Object> slotItem = new LinkedHashMap<>();
            slotItem.put("start", start.toString());
            slotItem.put("end", end.toString());
            slotItem.put("status", slotStatus(slotWork, slotUnavailable));
            if (admin) {
                slotItem.put("appointments", dayAppointments.stream()
                    .filter(row -> overlaps(
                        row.start(), row.end(), date.atTime(slotStart), date.atTime(slotEnd)
                    ))
                    .map(this::appointmentSummary)
                    .toList());
            }
            slots.add(slotItem);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.toString());
        result.put("scheduleStatus", scheduleStatus);
        result.put("availabilityStatus", availabilityStatus);
        result.put("workPeriods", periodMaps(work));
        result.put("slots", slots);
        if (admin) {
            result.put("breakPeriods", periodMaps(blocks));
            result.put("appointmentCount", dayAppointments.size());
            result.put("reservedMinutes", reservedMinutes);
            result.put("availableMinutes", availableMinutes);
            result.put("scheduleFacts", personFacts.stream().map(this::factMap).toList());
        }
        return result;
    }

    private List<StaffRow> staff(long tenantId, long shopId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT st.id, st.name, st.level_name, st.avatar_url
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
            WHERE st.tenant_id = ? AND st.status = 'ACTIVE'
              AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
              AND ssa.effective_from <= ?
              AND (ssa.effective_to IS NULL OR ssa.effective_to >= ?)
              AND EXISTS (
                  SELECT 1 FROM staff_service ss
                  WHERE ss.staff_id = st.id AND ss.enabled = 1
              )
            ORDER BY st.name, st.id
            """,
            (rs, rowNum) -> new StaffRow(
                rs.getLong("id"), rs.getString("name"),
                rs.getString("level_name"), rs.getString("avatar_url")
            ),
            tenantId, shopId, Date.valueOf(to), Date.valueOf(from)
        );
    }

    private List<ScheduleRow> dateFacts(long tenantId, long shopId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
            """
            SELECT id, staff_id, schedule_date, start_time, end_time,
                   schedule_type, remark, version
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
              AND schedule_date BETWEEN ? AND ?
            ORDER BY staff_id, schedule_date, start_time, id
            """,
            (rs, rowNum) -> new ScheduleRow(
                rs.getLong("id"), rs.getLong("staff_id"), rs.getDate("schedule_date").toLocalDate(),
                localTime(rs.getTime("start_time")), localTime(rs.getTime("end_time")),
                rs.getString("schedule_type"), rs.getString("remark"), rs.getInt("version")
            ),
            tenantId, shopId, Date.valueOf(from), Date.valueOf(to)
        );
    }

    private List<RuleRow> recurringRules(long tenantId, long shopId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
            """
            SELECT id, staff_id, day_of_week, start_time, end_time, rule_type,
                   effective_from, effective_to, version
            FROM staff_schedule_rule
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
              AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
            ORDER BY staff_id, day_of_week, start_time, id
            """,
            (rs, rowNum) -> new RuleRow(
                rs.getLong("id"), rs.getLong("staff_id"), rs.getInt("day_of_week"),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                rs.getString("rule_type"), rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate(),
                rs.getInt("version")
            ),
            tenantId, shopId, Date.valueOf(to), Date.valueOf(from)
        );
    }

    private List<BusyRow> appointments(
        long tenantId, long shopId, LocalDateTime from, LocalDateTime to
    ) {
        return jdbcTemplate.query(
            """
            SELECT a.id, a.staff_id, a.occupied_start_at, a.occupied_end_at,
                   COALESCE(NULLIF(m.name, ''), '到店顾客') AS customer_name,
                   GROUP_CONCAT(ai.service_name_snapshot ORDER BY ai.sort_order SEPARATOR '、') AS service_names
            FROM appointment a
            LEFT JOIN member m ON m.id = a.member_id AND m.tenant_id = a.tenant_id
            LEFT JOIN appointment_item ai ON ai.appointment_id = a.id
            WHERE a.tenant_id = ? AND a.shop_id = ?
              AND a.status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE')
              AND a.occupied_start_at < ? AND a.occupied_end_at > ?
            GROUP BY a.id, a.staff_id, a.occupied_start_at, a.occupied_end_at, m.name
            ORDER BY a.staff_id, a.occupied_start_at, a.id
            """,
            (rs, rowNum) -> new BusyRow(
                rs.getLong("id"), rs.getLong("staff_id"),
                rs.getTimestamp("occupied_start_at").toLocalDateTime(),
                rs.getTimestamp("occupied_end_at").toLocalDateTime(),
                rs.getString("customer_name"), rs.getString("service_names")
            ),
            tenantId, shopId, Timestamp.valueOf(to), Timestamp.valueOf(from)
        );
    }

    private List<BusyRow> locks(long tenantId, long shopId, LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.query(
            """
            SELECT id, staff_id, occupied_start_at, occupied_end_at
            FROM booking_time_lock
            WHERE tenant_id = ? AND shop_id = ? AND status = 'HELD'
              AND expires_at > CURRENT_TIMESTAMP(3)
              AND occupied_start_at < ? AND occupied_end_at > ?
            ORDER BY staff_id, occupied_start_at, id
            """,
            (rs, rowNum) -> new BusyRow(
                rs.getLong("id"), rs.getLong("staff_id"),
                rs.getTimestamp("occupied_start_at").toLocalDateTime(),
                rs.getTimestamp("occupied_end_at").toLocalDateTime(), null, null
            ),
            tenantId, shopId, Timestamp.valueOf(to), Timestamp.valueOf(from)
        );
    }

    private String scheduleStatus(List<ScheduleRow> facts, List<Range> work) {
        boolean fullLeave = facts.stream().anyMatch(row -> "LEAVE".equals(row.type())
            && row.start() == null && row.end() == null);
        if (fullLeave) return "LEAVE";
        if (work.isEmpty()) {
            boolean rest = facts.stream().anyMatch(row -> List.of("BREAK", "BLOCKED", "STOP_BOOKING")
                .contains(row.type()));
            return rest ? "REST" : "UNSCHEDULED";
        }
        return "WORKING";
    }

    static String availabilityStatus(
        String scheduleStatus, int workMinutes, int availableMinutes,
        int blockedMinutes, int reservedMinutes
    ) {
        if (!"WORKING".equals(scheduleStatus) || workMinutes == 0) return "UNAVAILABLE";
        if (availableMinutes == 0) return blockedMinutes >= workMinutes && reservedMinutes == 0
            ? "UNAVAILABLE" : "FULL";
        if (availableMinutes == workMinutes) return "AVAILABLE";
        return "PARTIALLY_AVAILABLE";
    }

    static String slotStatus(int workMinutes, int unavailableMinutes) {
        if (workMinutes == 0) return "UNAVAILABLE";
        if (unavailableMinutes >= workMinutes) return "FULL";
        if (unavailableMinutes > 0 || workMinutes < 60) return "PARTIALLY_AVAILABLE";
        return "AVAILABLE";
    }

    private List<Range> ranges(List<? extends TimedType> source, String type) {
        return source.stream()
            .filter(row -> type.equals(row.type()) && row.start() != null && row.end() != null)
            .map(row -> new Range(row.start(), row.end()))
            .toList();
    }

    private List<Range> blockRanges(List<? extends TimedType> source) {
        return source.stream()
            .filter(row -> List.of("BREAK", "LEAVE", "BLOCKED", "STOP_BOOKING").contains(row.type()))
            .map(row -> new Range(
                row.start() == null ? LocalTime.MIN : row.start(),
                row.end() == null ? LocalTime.MAX : row.end()
            ))
            .toList();
    }

    private List<Range> merge(List<Range> source) {
        List<Range> sorted = source.stream()
            .filter(range -> range.end().isAfter(range.start()))
            .sorted(Comparator.comparing(Range::start).thenComparing(Range::end))
            .toList();
        List<Range> result = new ArrayList<>();
        for (Range current : sorted) {
            if (result.isEmpty() || current.start().isAfter(result.getLast().end())) {
                result.add(current);
            } else {
                Range previous = result.removeLast();
                result.add(new Range(previous.start(), previous.end().isAfter(current.end())
                    ? previous.end() : current.end()));
            }
        }
        return result;
    }

    private int minutes(List<Range> source) {
        return source.stream().mapToInt(range -> (int) Duration.between(range.start(), range.end()).toMinutes()).sum();
    }

    private int overlapMinutes(List<Range> left, List<Range> right) {
        int result = 0;
        for (Range a : merge(left)) {
            for (Range b : merge(right)) {
                LocalTime start = a.start().isAfter(b.start()) ? a.start() : b.start();
                LocalTime end = a.end().isBefore(b.end()) ? a.end() : b.end();
                if (end.isAfter(start)) result += (int) Duration.between(start, end).toMinutes();
            }
        }
        return result;
    }

    private List<Range> concat(List<Range> first, List<Range> second) {
        List<Range> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private Range toTimeRange(BusyRow row, LocalDate date) {
        LocalTime start = row.start().toLocalDate().isBefore(date) ? LocalTime.MIN : row.start().toLocalTime();
        LocalTime end = row.end().toLocalDate().isAfter(date) ? LocalTime.MAX : row.end().toLocalTime();
        return new Range(start, end);
    }

    private List<Map<String, Object>> periodMaps(List<Range> ranges) {
        return merge(ranges).stream().map(range -> Map.<String, Object>of(
            "start", range.start().toString(), "end", range.end().toString()
        )).toList();
    }

    private Map<String, Object> appointmentSummary(BusyRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.id());
        result.put("start", row.start().toLocalTime().toString());
        result.put("end", row.end().toLocalTime().toString());
        result.put("customerName", row.customerName());
        result.put("serviceNames", row.serviceNames());
        return result;
    }

    private Map<String, Object> factMap(ScheduleRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.id());
        result.put("startTime", row.start() == null ? null : row.start().toString());
        result.put("endTime", row.end() == null ? null : row.end().toString());
        result.put("scheduleType", row.type());
        result.put("remark", row.remark());
        result.put("version", row.version());
        return result;
    }

    private boolean overlaps(
        LocalDateTime leftStart, LocalDateTime leftEnd,
        LocalDateTime rightStart, LocalDateTime rightEnd
    ) {
        return leftStart.isBefore(rightEnd) && leftEnd.isAfter(rightStart);
    }

    private LocalTime localTime(Time value) {
        return value == null ? null : value.toLocalTime();
    }

    private String weekday(DayOfWeek value) {
        return switch (value) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), DEFAULT_ZONE);
    }

    private interface TimedType {
        LocalTime start();
        LocalTime end();
        String type();
    }

    private record StaffRow(long id, String name, String levelName, String avatarUrl) {
    }

    private record ScheduleRow(
        long id, long staffId, LocalDate date, LocalTime start, LocalTime end,
        String type, String remark, int version
    ) implements TimedType {
    }

    private record RuleRow(
        long id, long staffId, int dayOfWeek, LocalTime start, LocalTime end,
        String type, LocalDate effectiveFrom, LocalDate effectiveTo, int version
    ) implements TimedType {
    }

    private record BusyRow(
        long id, long staffId, LocalDateTime start, LocalDateTime end,
        String customerName, String serviceNames
    ) {
    }

    private record Range(LocalTime start, LocalTime end) {
    }
}
