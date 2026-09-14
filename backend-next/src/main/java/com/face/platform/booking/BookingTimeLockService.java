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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingTimeLockService {

    public static final int LOCK_MINUTES = 15;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final BookingAvailabilityService availabilityService;
    private final ShopContextService shopContextService;
    private final TenantAccessService tenantAccessService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BookingTimeLockService(
        JdbcTemplate jdbcTemplate,
        BookingAvailabilityService availabilityService,
        ShopContextService shopContextService,
        TenantAccessService tenantAccessService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.availabilityService = availabilityService;
        this.shopContextService = shopContextService;
        this.tenantAccessService = tenantAccessService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> hold(TenantPrincipal principal, HoldCommand command) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), command.shopId());
        boolean memberRequest = principal.roles().contains("MEMBER");
        long memberId;
        boolean bypassMinimumAdvance = false;
        String source;
        if (memberRequest) {
            memberId = currentMemberId(principal);
            source = command.waitlistId() == null ? "MEMBER" : "WAITLIST";
        } else {
            tenantAccessService.requireShopPermission(principal, shop.shopId(), "appointment:manage");
            if (command.memberId() == null || command.memberId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "代客预约必须选择会员");
            }
            if (command.proxyReason() == null || command.proxyReason().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "代客预约必须记录原因");
            }
            memberId = command.memberId();
            requireMember(principal.tenantId(), shop.shopId(), memberId);
            bypassMinimumAdvance = command.bypassMinimumAdvance();
            source = "ADMIN";
        }

        BookingAvailabilityService.CandidateWindow selected = selectCandidate(
            principal.tenantId(), shop.shopId(), command, bypassMinimumAdvance
        );
        String mutexName = acquireStaffMutex(principal.tenantId(), shop.shopId(), selected.staff().id());
        try {
            requireNoCurrentOverlap(
                principal.tenantId(), shop.shopId(), selected.staff().id(),
                selected.slot().occupiedStart(), selected.slot().occupiedEnd()
            );
            String snapshot = snapshot(selected, command.proxyReason());
            LocalDateTime now = now(shop.timezone());
            LocalDateTime expiresAt = now.plusMinutes(LOCK_MINUTES);
            String token = UUID.randomUUID().toString();

            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            final long selectedMemberId = memberId;
            final String selectedSource = source;
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                """
                INSERT INTO booking_time_lock (
                    tenant_id, shop_id, lock_token, member_id, staff_id, service_id,
                    waitlist_id, start_at, end_at, occupied_start_at, occupied_end_at,
                    source, status, expires_at, terms_version, rule_snapshot_json, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'HELD', ?, ?, CAST(? AS JSON), ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, shop.shopId());
                statement.setString(3, token);
                statement.setLong(4, selectedMemberId);
                statement.setLong(5, selected.staff().id());
                statement.setLong(6, selected.service().id());
                if (command.waitlistId() == null) statement.setNull(7, java.sql.Types.BIGINT);
                else statement.setLong(7, command.waitlistId());
                statement.setTimestamp(8, Timestamp.valueOf(selected.slot().serviceStart()));
                statement.setTimestamp(9, Timestamp.valueOf(selected.slot().serviceEnd(selected.rule())));
                statement.setTimestamp(10, Timestamp.valueOf(selected.slot().occupiedStart()));
                statement.setTimestamp(11, Timestamp.valueOf(selected.slot().occupiedEnd()));
                statement.setString(12, selectedSource);
                statement.setTimestamp(13, Timestamp.valueOf(expiresAt));
                statement.setInt(14, Integer.parseInt(selected.rule().termsVersion()));
                statement.setString(15, snapshot);
                statement.setLong(16, principal.accountId());
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约时间锁创建失败");

            if ("UNASSIGNED".equalsIgnoreCase(command.assignmentMode())) {
                advanceAssignmentCursor(
                    principal.tenantId(), shop.shopId(), selected.service().id(), selected.staff().id(), now
                );
            }
            return lockResponse(
                key.longValue(), token, selected, expiresAt, snapshot, source,
                command.proxyReason()
            );
        } finally {
            releaseStaffMutex(mutexName);
        }
    }

    @Transactional
    public Map<String, Object> replace(
        TenantPrincipal principal,
        String oldToken,
        HoldCommand newCommand
    ) {
        Map<String, Object> replacement = hold(principal, newCommand);
        int released = jdbcTemplate.update(
            """
            UPDATE booking_time_lock
            SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP(3),
                release_reason = 'REMATCHED_AFTER_NEW_LOCK', version = version + 1
            WHERE tenant_id = ? AND lock_token = ? AND created_by = ? AND status = 'HELD'
            """,
            principal.tenantId(), oldToken, principal.accountId()
        );
        if (released != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "原时间锁已失效；新时间锁未保留");
        }
        return replacement;
    }

    @Transactional
    public Map<String, Object> confirm(
        TenantPrincipal principal,
        String token,
        int termsVersion,
        boolean termsConfirmed,
        String memberNote
    ) {
        if (!termsConfirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请重新确认当前预约条款");
        }
        Map<String, Object> lock = lockByToken(principal, token);
        LocalDateTime current = nowForShop(((Number) lock.get("shopId")).longValue());
        if (!"HELD".equals(lock.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "预约时间锁已释放或已使用");
        }
        LocalDateTime expiresAt = timestamp(lock.get("expiresAt"));
        if (!expiresAt.isAfter(current)) {
            expireLock(((Number) lock.get("id")).longValue(), "LOCK_TIMEOUT");
            throw new ApiException(HttpStatus.CONFLICT, "预约时间锁已超时，请重新选择时段");
        }
        int lockedTermsVersion = ((Number) lock.get("termsVersion")).intValue();
        if (termsVersion != lockedTermsVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "预约条款版本已变化，请重新确认");
        }

        long staffId = ((Number) lock.get("staffId")).longValue();
        lockStaff(principal.tenantId(), staffId);
        BookingAvailabilityService.CandidateWindow currentCandidate = availabilityService.requireAvailable(
            principal.tenantId(), ((Number) lock.get("shopId")).longValue(),
            ((Number) lock.get("serviceId")).longValue(), staffId,
            timestamp(lock.get("startAt")), "ADMIN".equals(lock.get("source")),
            ((Number) lock.get("id")).longValue()
        );
        if (Integer.parseInt(currentCandidate.rule().termsVersion()) != lockedTermsVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "预约规则已更新，请释放时间锁后重新选择");
        }

        LocalDateTime confirmedAt = current;
        String appointmentNo = "SC3" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
            .format(current) + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO appointment (
                    tenant_id, shop_id, appointment_no, member_id, staff_id,
                    start_at, end_at, occupied_start_at, occupied_end_at,
                    status, source, member_note, terms_version, rule_snapshot_json,
                    terms_confirmed_at, free_cancel_deadline, reschedule_deadline,
                    booking_time_lock_id, version, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, CAST(? AS JSON),
                          ?, ?, ?, ?, 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, ((Number) lock.get("shopId")).longValue());
            statement.setString(3, appointmentNo);
            statement.setLong(4, ((Number) lock.get("memberId")).longValue());
            statement.setLong(5, staffId);
            statement.setTimestamp(6, Timestamp.valueOf(timestamp(lock.get("startAt"))));
            statement.setTimestamp(7, Timestamp.valueOf(timestamp(lock.get("endAt"))));
            statement.setTimestamp(8, Timestamp.valueOf(timestamp(lock.get("occupiedStartAt"))));
            statement.setTimestamp(9, Timestamp.valueOf(timestamp(lock.get("occupiedEndAt"))));
            statement.setString(10, "ADMIN".equals(lock.get("source")) ? "FRONT_DESK" : "ONLINE");
            statement.setString(11, trim(memberNote));
            statement.setInt(12, lockedTermsVersion);
            statement.setString(13, String.valueOf(lock.get("ruleSnapshotJson")));
            statement.setTimestamp(14, Timestamp.valueOf(confirmedAt));
            statement.setTimestamp(15, Timestamp.valueOf(
                timestamp(lock.get("startAt")).minusMinutes(currentCandidate.rule().freeCancelMinutes())
            ));
            statement.setTimestamp(16, Timestamp.valueOf(
                timestamp(lock.get("startAt")).minusMinutes(currentCandidate.rule().rescheduleCutoffMinutes())
            ));
            statement.setLong(17, ((Number) lock.get("id")).longValue());
            statement.setLong(18, principal.accountId());
            return statement;
        }, keyHolder);
        Number appointmentKey = keyHolder.getKey();
        if (appointmentKey == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约创建失败");
        long appointmentId = appointmentKey.longValue();

        jdbcTemplate.update(
            """
            INSERT INTO appointment_item (
                appointment_id, service_id, service_name_snapshot,
                duration_minutes_snapshot, price_snapshot, sort_order
            )
            SELECT ?, si.id, si.name, ?, COALESCE(si.member_price, si.list_price), 0
            FROM service_item si WHERE si.id = ? AND si.tenant_id = ?
            """,
            appointmentId, currentCandidate.staff().durationMinutes(), currentCandidate.service().id(), principal.tenantId()
        );
        jdbcTemplate.update(
            """
            UPDATE booking_time_lock
            SET status = 'CONVERTED', appointment_id = ?, terms_confirmed_at = ?, version = version + 1
            WHERE id = ? AND status = 'HELD'
            """,
            appointmentId, Timestamp.valueOf(confirmedAt), ((Number) lock.get("id")).longValue()
        );
        jdbcTemplate.update(
            """
            INSERT INTO appointment_status_history (
                tenant_id, shop_id, appointment_id, from_status, to_status,
                reason, changed_by, appointment_version
            ) VALUES (?, ?, ?, NULL, 'PENDING', 'SC3_TIME_LOCK_CONFIRMED', ?, 0)
            """,
            principal.tenantId(), ((Number) lock.get("shopId")).longValue(),
            appointmentId, principal.accountId()
        );
        return Map.of(
            "appointment_id", appointmentId,
            "appointment_no", appointmentNo,
            "status", "PENDING",
            "lock_status", "CONVERTED"
        );
    }

    @Transactional
    public Map<String, Object> confirmPaid(
        TenantPrincipal principal,
        String token,
        int termsVersion,
        String memberNote,
        long orderId
    ) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT a.id AS appointmentId, a.appointment_no AS appointmentNo, a.status
            FROM booking_time_lock btl
            JOIN appointment a ON a.id = btl.appointment_id
            WHERE btl.tenant_id = ? AND btl.lock_token = ? AND btl.created_by = ?
            """,
            principal.tenantId(), token, principal.accountId()
        );
        if (!existing.isEmpty()) return existing.getFirst();

        Map<String, Object> converted = confirm(
            principal, token, termsVersion, true, memberNote
        );
        long appointmentId = ((Number) converted.get("appointment_id")).longValue();
        int changed = jdbcTemplate.update(
            """
            UPDATE appointment
            SET status = 'CONFIRMED', version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND status = 'PENDING' AND version = 0
            """,
            principal.accountId(), appointmentId, principal.tenantId()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "支付成功后预约确认状态已变化");
        }
        jdbcTemplate.update(
            """
            INSERT INTO appointment_status_history (
                tenant_id, shop_id, appointment_id, from_status, to_status,
                reason, changed_by, appointment_version
            ) SELECT tenant_id, shop_id, id, 'PENDING', 'CONFIRMED', ?, ?, 1
              FROM appointment WHERE id = ?
            """,
            "SC4_PAYMENT_CONFIRMED:ORDER:" + orderId, principal.accountId(), appointmentId
        );
        return Map.of(
            "appointmentId", appointmentId,
            "appointmentNo", converted.get("appointment_no"),
            "status", "CONFIRMED"
        );
    }

    @Transactional
    public Map<String, Object> release(TenantPrincipal principal, String token, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "MEMBER_RELEASED" : reason.trim();
        int changed = jdbcTemplate.update(
            """
            UPDATE booking_time_lock
            SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP(3),
                release_reason = ?, version = version + 1
            WHERE tenant_id = ? AND lock_token = ? AND created_by = ? AND status = 'HELD'
            """,
            safeReason, principal.tenantId(), token, principal.accountId()
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "预约时间锁已释放或不存在");
        return Map.of("released", true, "reason", safeReason);
    }

    @Transactional
    public int expireHeldLocks() {
        return jdbcTemplate.update(
            """
            UPDATE booking_time_lock
            SET status = 'EXPIRED', released_at = CURRENT_TIMESTAMP(3),
                release_reason = 'LOCK_TIMEOUT', version = version + 1
            WHERE status = 'HELD' AND expires_at <= CURRENT_TIMESTAMP(3)
            """
        );
    }

    private BookingAvailabilityService.CandidateWindow selectCandidate(
        long tenantId,
        long shopId,
        HoldCommand command,
        boolean bypassMinimumAdvance
    ) {
        if (!"UNASSIGNED".equalsIgnoreCase(command.assignmentMode())) {
            if (command.staffId() == null || command.staffId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "指定技师模式必须选择技师");
            }
            lockStaff(tenantId, command.staffId());
            return availabilityService.requireAvailable(
                tenantId, shopId, command.serviceId(), command.staffId(),
                command.startAt(), bypassMinimumAdvance
            );
        }

        List<BookingAvailabilityService.StaffCandidate> candidates = availabilityService.qualifiedStaff(
            tenantId, shopId, command.serviceId(), null, 1
        );
        if (candidates.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "当前没有可提供项目的技师");
        List<RankedCandidate> ranked = candidates.stream()
            .map(candidate -> rank(tenantId, shopId, command.serviceId(), command.startAt(), candidate))
            .sorted(Comparator.comparingLong(RankedCandidate::occupiedMinutes)
                .thenComparingLong(RankedCandidate::assignmentCount)
                .thenComparing(RankedCandidate::lastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingLong(value -> value.staff().id()))
            .toList();
        for (RankedCandidate candidate : ranked) {
            lockStaff(tenantId, candidate.staff().id());
            try {
                return availabilityService.requireAvailable(
                    tenantId, shopId, command.serviceId(), candidate.staff().id(),
                    command.startAt(), bypassMinimumAdvance
                );
            } catch (ApiException exception) {
                if (exception.status() != HttpStatus.CONFLICT) throw exception;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "该时段所有可选技师均已被占用");
    }

    private RankedCandidate rank(
        long tenantId,
        long shopId,
        long serviceId,
        LocalDateTime startAt,
        BookingAvailabilityService.StaffCandidate staff
    ) {
        LocalDateTime dayStart = startAt.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        Long occupied = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(minutes), 0) FROM (
              SELECT TIMESTAMPDIFF(MINUTE, occupied_start_at, occupied_end_at) AS minutes
              FROM appointment
              WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
                AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE')
                AND occupied_start_at < ? AND occupied_end_at > ?
              UNION ALL
              SELECT TIMESTAMPDIFF(MINUTE, occupied_start_at, occupied_end_at) AS minutes
              FROM booking_time_lock
              WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
                AND status = 'HELD' AND expires_at > CURRENT_TIMESTAMP(3)
                AND occupied_start_at < ? AND occupied_end_at > ?
            ) load_minutes
            """,
            Long.class,
            tenantId, shopId, staff.id(), Timestamp.valueOf(dayEnd), Timestamp.valueOf(dayStart),
            tenantId, shopId, staff.id(), Timestamp.valueOf(dayEnd), Timestamp.valueOf(dayStart)
        );
        List<Map<String, Object>> cursor = jdbcTemplate.queryForList(
            """
            SELECT assignment_count AS assignmentCount, last_assigned_at AS lastAssignedAt
            FROM booking_assignment_cursor
            WHERE tenant_id = ? AND shop_id = ? AND service_id = ? AND staff_id = ?
            """,
            tenantId, shopId, serviceId, staff.id()
        );
        long count = cursor.isEmpty() ? 0 : ((Number) cursor.getFirst().get("assignmentCount")).longValue();
        LocalDateTime last = cursor.isEmpty() || cursor.getFirst().get("lastAssignedAt") == null
            ? null : timestamp(cursor.getFirst().get("lastAssignedAt"));
        return new RankedCandidate(staff, occupied == null ? 0 : occupied, count, last);
    }

    private void advanceAssignmentCursor(
        long tenantId, long shopId, long serviceId, long staffId, LocalDateTime now
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO booking_assignment_cursor (
                tenant_id, shop_id, service_id, staff_id, assignment_count, last_assigned_at
            ) VALUES (?, ?, ?, ?, 1, ?)
            ON DUPLICATE KEY UPDATE assignment_count = assignment_count + 1,
                                    last_assigned_at = VALUES(last_assigned_at),
                                    version = version + 1
            """,
            tenantId, shopId, serviceId, staffId, Timestamp.valueOf(now)
        );
    }

    private Map<String, Object> lockByToken(TenantPrincipal principal, String token) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, member_id AS memberId, staff_id AS staffId,
                   service_id AS serviceId, start_at AS startAt, end_at AS endAt,
                   occupied_start_at AS occupiedStartAt, occupied_end_at AS occupiedEndAt,
                   source, status, expires_at AS expiresAt, terms_version AS termsVersion,
                   rule_snapshot_json AS ruleSnapshotJson
            FROM booking_time_lock
            WHERE tenant_id = ? AND lock_token = ? AND created_by = ?
            FOR UPDATE
            """,
            principal.tenantId(), token, principal.accountId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "预约时间锁不存在");
        return rows.getFirst();
    }

    private void lockStaff(long tenantId, long staffId) {
        List<Long> rows = jdbcTemplate.queryForList(
            "SELECT id FROM staff WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' FOR UPDATE",
            Long.class, tenantId, staffId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "技师当前不可预约");
    }

    private String acquireStaffMutex(long tenantId, long shopId, long staffId) {
        String name = "sc3-booking:" + tenantId + ":" + shopId + ":" + staffId;
        Integer acquired = jdbcTemplate.queryForObject("SELECT GET_LOCK(?, 10)", Integer.class, name);
        if (acquired == null || acquired != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "该技师预约请求较多，请稍后重试");
        }
        return name;
    }

    private void releaseStaffMutex(String name) {
        try {
            jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, name);
        } catch (RuntimeException ignored) {
            // Connection close also releases MySQL named locks; do not hide the booking outcome.
        }
    }

    private void requireNoCurrentOverlap(
        long tenantId,
        long shopId,
        long staffId,
        LocalDateTime occupiedStart,
        LocalDateTime occupiedEnd
    ) {
        List<Long> appointments = jdbcTemplate.queryForList(
            """
            SELECT id FROM appointment
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE')
              AND occupied_start_at < ? AND occupied_end_at > ?
            FOR UPDATE
            """,
            Long.class, tenantId, shopId, staffId,
            Timestamp.valueOf(occupiedEnd), Timestamp.valueOf(occupiedStart)
        );
        List<Long> locks = jdbcTemplate.queryForList(
            """
            SELECT id FROM booking_time_lock
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND status = 'HELD' AND expires_at > CURRENT_TIMESTAMP(3)
              AND occupied_start_at < ? AND occupied_end_at > ?
            FOR UPDATE
            """,
            Long.class, tenantId, shopId, staffId,
            Timestamp.valueOf(occupiedEnd), Timestamp.valueOf(occupiedStart)
        );
        if (!appointments.isEmpty() || !locks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "该技师的完整占用时段已被预约或锁定");
        }
    }

    private long currentMemberId(TenantPrincipal principal) {
        List<Long> rows = jdbcTemplate.queryForList(
            "SELECT member_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND member_id IS NOT NULL",
            Long.class, principal.accountId(), principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "当前账号未关联会员资料");
        return rows.getFirst();
    }

    private void requireMember(long tenantId, long shopId, long memberId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM member WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            Integer.class, memberId, tenantId
        );
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "代客预约会员不存在");
    }

    private String snapshot(BookingAvailabilityService.CandidateWindow selected, String proxyReason) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("service_id", selected.service().id());
        snapshot.put("service_name", selected.service().name());
        snapshot.put("duration_minutes", selected.rule().durationMinutes());
        snapshot.put("slot_interval_minutes", selected.rule().slotIntervalMinutes());
        snapshot.put("buffer_before_minutes", selected.rule().bufferBeforeMinutes());
        snapshot.put("buffer_after_minutes", selected.rule().bufferAfterMinutes());
        snapshot.put("minimum_advance_minutes", selected.rule().minimumAdvanceMinutes());
        snapshot.put("same_day_booking_allowed", selected.rule().sameDayBookingAllowed());
        snapshot.put("free_cancel_minutes", selected.rule().freeCancelMinutes());
        snapshot.put("reschedule_cutoff_minutes", selected.rule().rescheduleCutoffMinutes());
        snapshot.put("max_reschedules", selected.rule().maxReschedules());
        snapshot.put("late_cancel_policy", selected.rule().lateCancelPolicy());
        snapshot.put("late_cancel_value", selected.rule().lateCancelValue());
        snapshot.put("terms_version", selected.rule().termsVersion());
        snapshot.put("booking_notice", selected.service().bookingNotice());
        if (proxyReason != null && !proxyReason.isBlank()) snapshot.put("proxy_reason", proxyReason.trim());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约规则快照生成失败");
        }
    }

    private Map<String, Object> lockResponse(
        long id,
        String token,
        BookingAvailabilityService.CandidateWindow selected,
        LocalDateTime expiresAt,
        String snapshot,
        String source,
        String proxyReason
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("lock_token", token);
        result.put("status", "HELD");
        result.put("source", source);
        result.put("staff", Map.of("id", selected.staff().id(), "name", selected.staff().name()));
        result.put("service", Map.of("id", selected.service().id(), "name", selected.service().name()));
        result.put("start_at", selected.slot().serviceStart().toString());
        result.put("end_at", selected.slot().serviceEnd(selected.rule()).toString());
        result.put("occupied_start_at", selected.slot().occupiedStart().toString());
        result.put("occupied_end_at", selected.slot().occupiedEnd().toString());
        result.put("expires_at", expiresAt.toString());
        result.put("lock_minutes", LOCK_MINUTES);
        result.put("terms_version", Integer.parseInt(selected.rule().termsVersion()));
        result.put("rule_snapshot", snapshot);
        if (proxyReason != null && !proxyReason.isBlank()) result.put("proxy_reason", proxyReason.trim());
        return result;
    }

    private void expireLock(long lockId, String reason) {
        jdbcTemplate.update(
            """
            UPDATE booking_time_lock
            SET status = 'EXPIRED', released_at = CURRENT_TIMESTAMP(3),
                release_reason = ?, version = version + 1
            WHERE id = ? AND status = 'HELD'
            """,
            reason, lockId
        );
    }

    private LocalDateTime nowForShop(long shopId) {
        String timezone = jdbcTemplate.queryForObject("SELECT timezone FROM shop WHERE id = ?", String.class, shopId);
        return now(timezone);
    }

    private LocalDateTime now(String timezone) {
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? DEFAULT_ZONE : ZoneId.of(timezone);
        } catch (RuntimeException ignored) {
            zone = DEFAULT_ZONE;
        }
        return LocalDateTime.ofInstant(clock.instant(), zone);
    }

    private LocalDateTime timestamp(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约时间数据不完整");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record HoldCommand(
        Long shopId,
        long serviceId,
        Long staffId,
        String assignmentMode,
        LocalDateTime startAt,
        Long memberId,
        boolean bypassMinimumAdvance,
        String proxyReason,
        Long waitlistId
    ) {
    }

    private record RankedCandidate(
        BookingAvailabilityService.StaffCandidate staff,
        long occupiedMinutes,
        long assignmentCount,
        LocalDateTime lastAssignedAt
    ) {
    }
}
