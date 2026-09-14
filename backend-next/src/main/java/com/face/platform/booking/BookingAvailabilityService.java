package com.face.platform.booking;

import com.face.platform.api.ApiException;
import com.face.platform.shop.ShopContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
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
public class BookingAvailabilityService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> OCCUPYING_APPOINTMENT_STATUSES =
        List.of("PENDING", "CONFIRMED", "CHECKED_IN", "IN_SERVICE");

    private final JdbcTemplate jdbcTemplate;
    private final ShopContextService shopContextService;
    private final BookingPolicy bookingPolicy;
    private final Clock clock;

    public BookingAvailabilityService(
        JdbcTemplate jdbcTemplate,
        ShopContextService shopContextService,
        BookingPolicy bookingPolicy,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.shopContextService = shopContextService;
        this.bookingPolicy = bookingPolicy;
        this.clock = clock;
    }

    public Map<String, Object> availability(
        Long requestedShopId,
        long serviceId,
        Long requestedStaffId,
        LocalDate fromDate
    ) {
        var shop = shopContextService.requirePublicShop(requestedShopId);
        ZoneId zone = zone(shop.timezone());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);
        LocalDate firstDate = fromDate == null ? now.toLocalDate() : fromDate;
        final List<LocalDate> dates;
        try {
            dates = bookingPolicy.queryDates(now.toLocalDate(), firstDate);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        ServiceDefinition service = requireService(shop.tenantId(), shop.shopId(), serviceId);
        List<StaffCandidate> staff = qualifiedStaff(
            shop.tenantId(), shop.shopId(), serviceId, requestedStaffId, service.rule().durationMinutes()
        );
        if (requestedStaffId != null && staff.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "所选技师不可提供当前项目");
        }

        Map<LocalDate, Map<LocalDateTime, List<StaffCandidate>>> byDate = new LinkedHashMap<>();
        for (LocalDate date : dates) {
            Map<LocalDateTime, List<StaffCandidate>> starts = new java.util.TreeMap<>();
            for (StaffCandidate candidate : staff) {
                BookingPolicy.ServiceRule staffRule = withDuration(service.rule(), candidate.durationMinutes());
                for (BookingPolicy.SlotWindow slot : availableWindows(
                    shop.tenantId(), shop.shopId(), candidate.id(), staffRule, date, now, false, null
                )) {
                    starts.computeIfAbsent(slot.serviceStart(), ignored -> new ArrayList<>()).add(candidate);
                }
            }
            byDate.put(date, starts);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        byDate.forEach((date, starts) -> {
            List<Map<String, Object>> slots = new ArrayList<>();
            starts.forEach((start, candidates) -> {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("start_at", start.toString());
                slot.put("staff", candidates.stream()
                    .sorted(Comparator.comparing(StaffCandidate::name).thenComparingLong(StaffCandidate::id))
                    .map(candidate -> Map.of(
                        "id", candidate.id(),
                        "name", candidate.name(),
                        "duration_minutes", candidate.durationMinutes()
                    ))
                    .toList());
                slots.add(slot);
            });
            days.add(Map.of("date", date.toString(), "slots", slots));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shop_id", shop.shopId());
        result.put("service", serviceResponse(service));
        result.put("assignment_modes", List.of("SPECIFIED", "UNASSIGNED"));
        result.put("from_date", firstDate.toString());
        result.put("to_date", firstDate.plusDays(BookingPolicy.QUERY_DAYS - 1L).toString());
        result.put("max_booking_date", now.toLocalDate().plusDays(BookingPolicy.MAX_ADVANCE_DAYS).toString());
        result.put("generated_at", now.toString());
        result.put("days", days);
        return result;
    }

    CandidateWindow requireAvailable(
        long tenantId,
        long shopId,
        long serviceId,
        long staffId,
        LocalDateTime serviceStart,
        boolean minimumAdvanceBypassed
    ) {
        return requireAvailable(
            tenantId, shopId, serviceId, staffId, serviceStart, minimumAdvanceBypassed, null
        );
    }

    CandidateWindow requireAvailable(
        long tenantId,
        long shopId,
        long serviceId,
        long staffId,
        LocalDateTime serviceStart,
        boolean minimumAdvanceBypassed,
        Long ignoredLockId
    ) {
        ServiceDefinition service = requireService(tenantId, shopId, serviceId);
        StaffCandidate candidate = qualifiedStaff(tenantId, shopId, serviceId, staffId, service.rule().durationMinutes())
            .stream().findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "技师不可提供当前项目"));
        BookingPolicy.ServiceRule staffRule = withDuration(service.rule(), candidate.durationMinutes());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zoneForShop(shopId));
        if (!bookingPolicy.isSelectable(staffRule, now, serviceStart, minimumAdvanceBypassed)) {
            throw new ApiException(HttpStatus.CONFLICT, "该时间不符合预约提前量或当日预约规则");
        }
        if (serviceStart.toLocalDate().isAfter(now.toLocalDate().plusDays(BookingPolicy.MAX_ADVANCE_DAYS))) {
            throw new ApiException(HttpStatus.CONFLICT, "预约日期超出未来 30 天范围");
        }
        return availableWindows(
            tenantId, shopId, staffId, staffRule, serviceStart.toLocalDate(), now,
            minimumAdvanceBypassed, ignoredLockId
        ).stream()
            .filter(slot -> slot.serviceStart().equals(serviceStart))
            .findFirst()
            .map(slot -> new CandidateWindow(service, candidate, staffRule, slot))
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "该时段刚刚已被占用或已停止预约"));
    }

    List<StaffCandidate> qualifiedStaff(
        long tenantId,
        long shopId,
        long serviceId,
        Long requestedStaffId,
        int defaultDuration
    ) {
        String filter = requestedStaffId == null ? "" : " AND st.id = ?";
        List<Object> args = new ArrayList<>(List.of(serviceId, tenantId, shopId));
        if (requestedStaffId != null) args.add(requestedStaffId);
        return jdbcTemplate.query(
            """
            SELECT st.id, st.name, COALESCE(ss.custom_duration_minutes, ?) AS duration_minutes
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
             AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
             AND ssa.effective_from <= CURRENT_DATE
             AND (ssa.effective_to IS NULL OR ssa.effective_to >= CURRENT_DATE)
            JOIN staff_service ss ON ss.staff_id = st.id AND ss.service_id = ? AND ss.enabled = 1
            WHERE st.tenant_id = ? AND st.status = 'ACTIVE'
            %s
            ORDER BY st.id
            """.formatted(filter),
            (rs, rowNum) -> new StaffCandidate(
                rs.getLong("id"), rs.getString("name"), rs.getInt("duration_minutes")
            ),
            reorderQualifiedArgs(defaultDuration, shopId, serviceId, tenantId, requestedStaffId)
        );
    }

    private Object[] reorderQualifiedArgs(
        int defaultDuration, long shopId, long serviceId, long tenantId, Long requestedStaffId
    ) {
        List<Object> args = new ArrayList<>(List.of(defaultDuration, shopId, serviceId, tenantId));
        if (requestedStaffId != null) args.add(requestedStaffId);
        return args.toArray();
    }

    private List<BookingPolicy.SlotWindow> availableWindows(
        long tenantId,
        long shopId,
        long staffId,
        BookingPolicy.ServiceRule rule,
        LocalDate date,
        LocalDateTime now,
        boolean minimumAdvanceBypassed,
        Long ignoredLockId
    ) {
        List<TimeRange> work = workRanges(tenantId, shopId, staffId, date);
        if (work.isEmpty()) return List.of();
        List<TimeRange> blocks = blockRanges(tenantId, shopId, staffId, date);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<DateTimeRange> occupied = occupiedRanges(
            tenantId, shopId, staffId, dayStart, dayEnd, now, ignoredLockId
        );
        List<BookingPolicy.SlotWindow> result = new ArrayList<>();
        for (TimeRange range : work) {
            LocalDateTime workStart = LocalDateTime.of(date, range.start());
            LocalDateTime workEnd = LocalDateTime.of(date, range.end());
            for (BookingPolicy.SlotWindow slot : bookingPolicy.generateSlots(rule, workStart, workEnd)) {
                if (!bookingPolicy.isSelectable(rule, now, slot.serviceStart(), minimumAdvanceBypassed)) continue;
                boolean blocked = blocks.stream().anyMatch(rangeBlock -> bookingPolicy.overlaps(
                    slot,
                    LocalDateTime.of(date, rangeBlock.start()),
                    LocalDateTime.of(date, rangeBlock.end())
                ));
                boolean busy = occupied.stream().anyMatch(existing -> bookingPolicy.overlaps(
                    slot, existing.start(), existing.end()
                ));
                if (!blocked && !busy) result.add(slot);
            }
        }
        return result.stream().distinct().sorted(Comparator.comparing(BookingPolicy.SlotWindow::serviceStart)).toList();
    }

    private List<TimeRange> workRanges(long tenantId, long shopId, long staffId, LocalDate date) {
        List<TimeRange> specific = dateScheduleRanges(tenantId, shopId, staffId, date, "WORK");
        if (!specific.isEmpty()) return specific;
        return recurringRanges(tenantId, shopId, staffId, date, "WORK");
    }

    private List<TimeRange> blockRanges(long tenantId, long shopId, long staffId, LocalDate date) {
        List<TimeRange> result = new ArrayList<>();
        result.addAll(jdbcTemplate.query(
            """
            SELECT start_time, end_time
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND schedule_date = ?
              AND status = 'ACTIVE'
              AND schedule_type IN ('BREAK', 'LEAVE', 'BLOCKED', 'STOP_BOOKING')
            """,
            (rs, rowNum) -> {
                Time start = rs.getTime("start_time");
                Time end = rs.getTime("end_time");
                return new TimeRange(
                    start == null ? LocalTime.MIN : start.toLocalTime(),
                    end == null ? LocalTime.MAX : end.toLocalTime()
                );
            },
            tenantId, shopId, staffId, Date.valueOf(date)
        ));
        result.addAll(recurringRanges(tenantId, shopId, staffId, date, "BREAK", "STOP_BOOKING"));
        return result;
    }

    private List<TimeRange> dateScheduleRanges(
        long tenantId, long shopId, long staffId, LocalDate date, String type
    ) {
        return jdbcTemplate.query(
            """
            SELECT start_time, end_time FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND schedule_date = ?
              AND status = 'ACTIVE'
              AND schedule_type = ? AND start_time IS NOT NULL AND end_time IS NOT NULL
            ORDER BY start_time
            """,
            (rs, rowNum) -> new TimeRange(
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime()
            ),
            tenantId, shopId, staffId, Date.valueOf(date), type
        );
    }

    private List<TimeRange> recurringRanges(
        long tenantId, long shopId, long staffId, LocalDate date, String... types
    ) {
        String placeholders = String.join(",", java.util.Collections.nCopies(types.length, "?"));
        List<Object> args = new ArrayList<>(List.of(
            tenantId, shopId, staffId, date.getDayOfWeek().getValue(), Date.valueOf(date), Date.valueOf(date)
        ));
        args.addAll(List.of(types));
        return jdbcTemplate.query(
            """
            SELECT start_time, end_time FROM staff_schedule_rule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND day_of_week = ?
              AND status = 'ACTIVE' AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
              AND rule_type IN (%s)
            ORDER BY start_time
            """.formatted(placeholders),
            (rs, rowNum) -> new TimeRange(
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime()
            ),
            args.toArray()
        );
    }

    private List<DateTimeRange> occupiedRanges(
        long tenantId, long shopId, long staffId,
        LocalDateTime dayStart, LocalDateTime dayEnd, LocalDateTime now, Long ignoredLockId
    ) {
        List<DateTimeRange> result = new ArrayList<>();
        result.addAll(jdbcTemplate.query(
            """
            SELECT occupied_start_at, occupied_end_at FROM appointment
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE')
              AND occupied_start_at < ? AND occupied_end_at > ?
            """,
            (rs, rowNum) -> new DateTimeRange(
                rs.getTimestamp("occupied_start_at").toLocalDateTime(),
                rs.getTimestamp("occupied_end_at").toLocalDateTime()
            ),
            tenantId, shopId, staffId, Timestamp.valueOf(dayEnd), Timestamp.valueOf(dayStart)
        ));
        String ignored = ignoredLockId == null ? "" : " AND id <> ?";
        List<Object> lockArgs = new ArrayList<>(List.of(
            tenantId, shopId, staffId, Timestamp.valueOf(now),
            Timestamp.valueOf(dayEnd), Timestamp.valueOf(dayStart)
        ));
        if (ignoredLockId != null) lockArgs.add(ignoredLockId);
        result.addAll(jdbcTemplate.query(
            """
            SELECT occupied_start_at, occupied_end_at FROM booking_time_lock
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND status = 'HELD' AND expires_at > ?
              AND occupied_start_at < ? AND occupied_end_at > ?%s
            """.formatted(ignored),
            (rs, rowNum) -> new DateTimeRange(
                rs.getTimestamp("occupied_start_at").toLocalDateTime(),
                rs.getTimestamp("occupied_end_at").toLocalDateTime()
            ),
            lockArgs.toArray()
        ));
        return result;
    }

    private ServiceDefinition requireService(long tenantId, long shopId, long serviceId) {
        List<ServiceDefinition> rows = jdbcTemplate.query(
            """
            SELECT id, name, booking_notice, duration_minutes, slot_interval_minutes,
                   buffer_before_minutes, buffer_after_minutes, minimum_advance_minutes,
                   same_day_booking_allowed, free_cancel_minutes, reschedule_cutoff_minutes,
                   max_reschedules, late_cancel_policy, late_cancel_value, booking_terms_version
            FROM service_item
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            LIMIT 1
            """,
            (rs, rowNum) -> new ServiceDefinition(
                rs.getLong("id"), rs.getString("name"), rs.getString("booking_notice"),
                new BookingPolicy.ServiceRule(
                    rs.getLong("id"), rs.getInt("duration_minutes"), rs.getInt("slot_interval_minutes"),
                    rs.getInt("buffer_before_minutes"), rs.getInt("buffer_after_minutes"),
                    rs.getInt("minimum_advance_minutes"), rs.getBoolean("same_day_booking_allowed"),
                    rs.getInt("free_cancel_minutes"), rs.getInt("reschedule_cutoff_minutes"),
                    rs.getInt("max_reschedules"), rs.getString("late_cancel_policy"),
                    rs.getBigDecimal("late_cancel_value"), String.valueOf(rs.getInt("booking_terms_version"))
                )
            ),
            serviceId, tenantId, shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "护理项目不存在或未开放预约");
        return rows.getFirst();
    }

    private Map<String, Object> serviceResponse(ServiceDefinition service) {
        BookingPolicy.ServiceRule rule = service.rule();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", service.id());
        result.put("name", service.name());
        result.put("duration_minutes", rule.durationMinutes());
        result.put("slot_interval_minutes", rule.slotIntervalMinutes());
        result.put("buffer_before_minutes", rule.bufferBeforeMinutes());
        result.put("buffer_after_minutes", rule.bufferAfterMinutes());
        result.put("minimum_advance_minutes", rule.minimumAdvanceMinutes());
        result.put("same_day_booking_allowed", rule.sameDayBookingAllowed());
        result.put("free_cancel_minutes", rule.freeCancelMinutes());
        result.put("reschedule_cutoff_minutes", rule.rescheduleCutoffMinutes());
        result.put("max_reschedules", rule.maxReschedules());
        result.put("late_cancel_policy", rule.lateCancelPolicy());
        result.put("late_cancel_value", rule.lateCancelValue());
        result.put("terms_version", rule.termsVersion());
        result.put("booking_notice", service.bookingNotice());
        return result;
    }

    private BookingPolicy.ServiceRule withDuration(BookingPolicy.ServiceRule rule, int duration) {
        return new BookingPolicy.ServiceRule(
            rule.serviceId(), duration, rule.slotIntervalMinutes(),
            rule.bufferBeforeMinutes(), rule.bufferAfterMinutes(), rule.minimumAdvanceMinutes(),
            rule.sameDayBookingAllowed(), rule.freeCancelMinutes(), rule.rescheduleCutoffMinutes(),
            rule.maxReschedules(), rule.lateCancelPolicy(), rule.lateCancelValue(), rule.termsVersion()
        );
    }

    private ZoneId zoneForShop(long shopId) {
        String timezone = jdbcTemplate.queryForObject("SELECT timezone FROM shop WHERE id = ?", String.class, shopId);
        return zone(timezone);
    }

    private ZoneId zone(String value) {
        try {
            return value == null || value.isBlank() ? DEFAULT_ZONE : ZoneId.of(value);
        } catch (RuntimeException ignored) {
            return DEFAULT_ZONE;
        }
    }

    record TimeRange(LocalTime start, LocalTime end) {
    }

    record DateTimeRange(LocalDateTime start, LocalDateTime end) {
    }

    public record StaffCandidate(long id, String name, int durationMinutes) {
    }

    public record ServiceDefinition(
        long id,
        String name,
        String bookingNotice,
        BookingPolicy.ServiceRule rule
    ) {
    }

    public record CandidateWindow(
        ServiceDefinition service,
        StaffCandidate staff,
        BookingPolicy.ServiceRule rule,
        BookingPolicy.SlotWindow slot
    ) {
    }
}
