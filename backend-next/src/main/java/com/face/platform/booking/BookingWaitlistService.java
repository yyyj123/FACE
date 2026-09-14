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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingWaitlistService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final BookingTimeLockService timeLockService;
    private final BookingWaitlistPolicy policy;
    private final ShopContextService shopContextService;
    private final TenantAccessService tenantAccessService;
    private final Clock clock;

    public BookingWaitlistService(
        JdbcTemplate jdbcTemplate,
        BookingTimeLockService timeLockService,
        BookingWaitlistPolicy policy,
        ShopContextService shopContextService,
        TenantAccessService tenantAccessService,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeLockService = timeLockService;
        this.policy = policy;
        this.shopContextService = shopContextService;
        this.tenantAccessService = tenantAccessService;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> join(TenantPrincipal principal, JoinCommand command) {
        requireMemberRole(principal);
        var shop = shopContextService.requireTenantShop(principal.tenantId(), command.shopId());
        long memberId = currentMemberId(principal);
        LocalDate today = now(shop.timezone()).toLocalDate();
        if (command.dateFrom() == null || command.dateTo() == null
            || command.dateFrom().isBefore(today) || command.dateTo().isBefore(command.dateFrom())
            || command.dateTo().isAfter(today.plusDays(BookingPolicy.MAX_ADVANCE_DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "候补日期必须位于未来 30 天内");
        }
        if (command.timeFrom() == null || command.timeTo() == null
            || !command.timeTo().isAfter(command.timeFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "候补时间范围不正确");
        }
        requireServiceAndStaff(
            principal.tenantId(), shop.shopId(), command.serviceId(), command.requestedStaffId()
        );
        Integer active = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM booking_waitlist
            WHERE tenant_id = ? AND shop_id = ? AND member_id = ? AND service_id = ?
              AND status IN ('WAITING', 'MATCHED', 'WAITING_CONFIRMATION')
            """,
            Integer.class, principal.tenantId(), shop.shopId(), memberId, command.serviceId()
        );
        if (active != null && active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该项目已有生效中的候补意向");
        }

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime latestNotifyAt = LocalDateTime.of(command.dateTo(), command.timeTo());
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO booking_waitlist (
                    tenant_id, shop_id, member_id, service_id, requested_staff_id,
                    date_from, date_to, time_from, time_to, flexibility_minutes,
                    accept_other_staff, latest_notify_at, status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAITING', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shop.shopId());
            statement.setLong(3, memberId);
            statement.setLong(4, command.serviceId());
            if (command.requestedStaffId() == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, command.requestedStaffId());
            statement.setDate(6, Date.valueOf(command.dateFrom()));
            statement.setDate(7, Date.valueOf(command.dateTo()));
            statement.setTime(8, Time.valueOf(command.timeFrom()));
            statement.setTime(9, Time.valueOf(command.timeTo()));
            statement.setInt(10, command.flexibilityMinutes());
            statement.setBoolean(11, command.acceptOtherStaff());
            statement.setTimestamp(12, Timestamp.valueOf(latestNotifyAt));
            statement.setLong(13, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "候补意向创建失败");
        history(principal.tenantId(), shop.shopId(), key.longValue(), null, "WAITING", "MEMBER_JOINED", principal.accountId());
        return Map.of("id", key.longValue(), "status", "WAITING", "joined_at", now(shop.timezone()).toString());
    }

    public List<Map<String, Object>> mine(TenantPrincipal principal) {
        requireMemberRole(principal);
        long memberId = currentMemberId(principal);
        return jdbcTemplate.queryForList(
            """
            SELECT w.id, w.shop_id AS shopId, w.service_id AS serviceId, si.name AS serviceName,
                   w.requested_staff_id AS requestedStaffId, requested.name AS requestedStaffName,
                   w.matched_staff_id AS matchedStaffId, matched.name AS matchedStaffName,
                   w.date_from AS dateFrom, w.date_to AS dateTo,
                   w.time_from AS timeFrom, w.time_to AS timeTo,
                   w.flexibility_minutes AS flexibilityMinutes,
                   w.accept_other_staff AS acceptOtherStaff,
                   w.status, w.confirmation_expires_at AS confirmationExpiresAt,
                   lock_row.lock_token AS lockToken, lock_row.start_at AS matchedStartAt,
                   lock_row.terms_version AS termsVersion, w.created_at AS createdAt
            FROM booking_waitlist w
            JOIN service_item si ON si.id = w.service_id
            LEFT JOIN staff requested ON requested.id = w.requested_staff_id
            LEFT JOIN staff matched ON matched.id = w.matched_staff_id
            LEFT JOIN booking_time_lock lock_row ON lock_row.id = w.time_lock_id
            WHERE w.tenant_id = ? AND w.member_id = ?
            ORDER BY w.created_at DESC, w.id DESC
            """,
            principal.tenantId(), memberId
        );
    }

    @Transactional
    public Map<String, Object> cancel(TenantPrincipal principal, long waitlistId) {
        requireMemberRole(principal);
        Map<String, Object> current = lockMine(principal, waitlistId);
        String status = String.valueOf(current.get("status"));
        if (!policy.canTransition(status, "CANCELLED")) {
            throw new ApiException(HttpStatus.CONFLICT, "当前候补状态不能取消");
        }
        jdbcTemplate.update(
            """
            UPDATE booking_waitlist
            SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ?
            """,
            waitlistId
        );
        Object lockId = current.get("timeLockId");
        if (lockId != null) {
            jdbcTemplate.update(
                """
                UPDATE booking_time_lock
                SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP(3),
                    release_reason = 'WAITLIST_CANCELLED', version = version + 1
                WHERE id = ? AND status = 'HELD'
                """,
                ((Number) lockId).longValue()
            );
        }
        history(
            principal.tenantId(), ((Number) current.get("shopId")).longValue(), waitlistId,
            status, "CANCELLED", "MEMBER_CANCELLED", principal.accountId()
        );
        return Map.of("id", waitlistId, "status", "CANCELLED");
    }

    @Transactional
    public Map<String, Object> processVacancy(
        TenantPrincipal operator,
        Long requestedShopId,
        long serviceId,
        long staffId,
        LocalDateTime startAt
    ) {
        var shop = shopContextService.requireTenantShop(operator.tenantId(), requestedShopId);
        tenantAccessService.requireShopPermission(operator, shop.shopId(), "waitlist:manage");
        expireWaitingConfirmations(operator.tenantId(), shop.shopId());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT w.id, w.member_id AS memberId, w.created_by AS memberAccountId,
                   a.username AS memberUsername
            FROM booking_waitlist w
            JOIN account a ON a.id = w.created_by AND a.member_id = w.member_id
            WHERE w.tenant_id = ? AND w.shop_id = ? AND w.service_id = ?
              AND w.status = 'WAITING' AND w.latest_notify_at > CURRENT_TIMESTAMP(3)
              AND ? BETWEEN w.date_from AND w.date_to
              AND ? >= SUBTIME(w.time_from, SEC_TO_TIME(w.flexibility_minutes * 60))
              AND ? <= ADDTIME(w.time_to, SEC_TO_TIME(w.flexibility_minutes * 60))
              AND (w.requested_staff_id IS NULL OR w.requested_staff_id = ? OR w.accept_other_staff = 1)
            ORDER BY w.created_at, w.id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """,
            operator.tenantId(), shop.shopId(), serviceId, Date.valueOf(startAt.toLocalDate()),
            Time.valueOf(startAt.toLocalTime()), Time.valueOf(startAt.toLocalTime()), staffId
        );
        if (rows.isEmpty()) return Map.of("matched", false, "reason", "NO_ELIGIBLE_CANDIDATE");
        Map<String, Object> candidate = rows.getFirst();
        long waitlistId = ((Number) candidate.get("id")).longValue();
        transition(
            operator.tenantId(), shop.shopId(), waitlistId,
            "WAITING", "MATCHED", "VACANCY_MATCHED", operator.accountId()
        );

        TenantPrincipal memberPrincipal = new TenantPrincipal(
            ((Number) candidate.get("memberAccountId")).longValue(), operator.tenantId(), shop.shopId(),
            String.valueOf(candidate.get("memberUsername")), List.of("MEMBER"),
            Set.of(), Set.of(shop.shopId()), false
        );
        Map<String, Object> lock = timeLockService.hold(
            memberPrincipal,
            new BookingTimeLockService.HoldCommand(
                shop.shopId(), serviceId, staffId, "SPECIFIED", startAt,
                ((Number) candidate.get("memberId")).longValue(), false,
                null, waitlistId
            )
        );
        long lockId = ((Number) lock.get("id")).longValue();
        LocalDateTime expiresAt = LocalDateTime.parse(String.valueOf(lock.get("expires_at")));
        int changed = jdbcTemplate.update(
            """
            UPDATE booking_waitlist
            SET status = 'WAITING_CONFIRMATION', matched_staff_id = ?, time_lock_id = ?,
                matched_at = CURRENT_TIMESTAMP(3), confirmation_expires_at = ?, version = version + 1
            WHERE id = ? AND status = 'MATCHED'
            """,
            staffId, lockId, Timestamp.valueOf(expiresAt), waitlistId
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "候补状态已被其他操作更新");
        history(
            operator.tenantId(), shop.shopId(), waitlistId,
            "MATCHED", "WAITING_CONFIRMATION", "FIFTEEN_MINUTE_CONFIRMATION_LOCK", operator.accountId()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", true);
        result.put("waitlist_id", waitlistId);
        result.put("lock_token", lock.get("lock_token"));
        result.put("confirmation_expires_at", lock.get("expires_at"));
        result.put("appointment_created", false);
        return result;
    }

    @Transactional
    public Map<String, Object> confirm(
        TenantPrincipal principal,
        long waitlistId,
        int termsVersion,
        boolean termsConfirmed,
        String memberNote
    ) {
        requireMemberRole(principal);
        Map<String, Object> current = lockMine(principal, waitlistId);
        if (!"WAITING_CONFIRMATION".equals(current.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "候补尚未进入待确认状态");
        }
        LocalDateTime expiresAt = timestamp(current.get("confirmationExpiresAt"));
        if (!expiresAt.isAfter(nowForShop(((Number) current.get("shopId")).longValue()))) {
            expireOne(principal.tenantId(), waitlistId, ((Number) current.get("timeLockId")).longValue());
            throw new ApiException(HttpStatus.CONFLICT, "候补确认已超时，时段已让给下一位");
        }
        List<String> tokens = jdbcTemplate.queryForList(
            "SELECT lock_token FROM booking_time_lock WHERE id = ?",
            String.class, ((Number) current.get("timeLockId")).longValue()
        );
        if (tokens.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "候补时间锁不存在");
        Map<String, Object> appointment = timeLockService.confirm(
            principal, tokens.getFirst(), termsVersion, termsConfirmed, memberNote
        );
        transition(
            principal.tenantId(), ((Number) current.get("shopId")).longValue(), waitlistId,
            "WAITING_CONFIRMATION", "CONFIRMED", "MEMBER_RECONFIRMED_TERMS", principal.accountId()
        );
        jdbcTemplate.update(
            "UPDATE booking_waitlist SET confirmed_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
            waitlistId
        );
        Map<String, Object> result = new LinkedHashMap<>(appointment);
        result.put("waitlist_id", waitlistId);
        result.put("waitlist_status", "CONFIRMED");
        return result;
    }

    @Transactional
    public int expireWaitingConfirmations(long tenantId, long shopId) {
        List<Map<String, Object>> expired = jdbcTemplate.queryForList(
            """
            SELECT id, time_lock_id AS timeLockId FROM booking_waitlist
            WHERE tenant_id = ? AND shop_id = ? AND status = 'WAITING_CONFIRMATION'
              AND confirmation_expires_at <= CURRENT_TIMESTAMP(3)
            FOR UPDATE
            """,
            tenantId, shopId
        );
        for (Map<String, Object> row : expired) {
            expireOne(tenantId, ((Number) row.get("id")).longValue(), ((Number) row.get("timeLockId")).longValue());
        }
        return expired.size();
    }

    private void expireOne(long tenantId, long waitlistId, long lockId) {
        jdbcTemplate.update(
            """
            UPDATE booking_time_lock
            SET status = 'EXPIRED', released_at = CURRENT_TIMESTAMP(3),
                release_reason = 'WAITLIST_CONFIRMATION_TIMEOUT', version = version + 1
            WHERE id = ? AND status = 'HELD'
            """,
            lockId
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT shop_id AS shopId, status FROM booking_waitlist WHERE tenant_id = ? AND id = ? FOR UPDATE",
            tenantId, waitlistId
        );
        if (!rows.isEmpty() && "WAITING_CONFIRMATION".equals(rows.getFirst().get("status"))) {
            long shopId = ((Number) rows.getFirst().get("shopId")).longValue();
            jdbcTemplate.update(
                "UPDATE booking_waitlist SET status = 'EXPIRED', version = version + 1 WHERE id = ?",
                waitlistId
            );
            history(tenantId, shopId, waitlistId, "WAITING_CONFIRMATION", "EXPIRED", "CONFIRMATION_TIMEOUT", null);
        }
    }

    private void transition(
        long tenantId, long shopId, long waitlistId,
        String from, String to, String reason, Long actor
    ) {
        if (!policy.canTransition(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "不允许的候补状态变化：" + from + " -> " + to);
        }
        int changed = jdbcTemplate.update(
            "UPDATE booking_waitlist SET status = ?, version = version + 1 WHERE id = ? AND status = ?",
            to, waitlistId, from
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "候补状态已被其他操作更新");
        history(tenantId, shopId, waitlistId, from, to, reason, actor);
    }

    private void history(
        long tenantId, long shopId, long waitlistId,
        String from, String to, String reason, Long actor
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO booking_waitlist_status_history (
                tenant_id, shop_id, waitlist_id, from_status, to_status, reason, changed_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            tenantId, shopId, waitlistId, from, to, reason, actor
        );
    }

    private Map<String, Object> lockMine(TenantPrincipal principal, long waitlistId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT w.id, w.shop_id AS shopId, w.status,
                   w.time_lock_id AS timeLockId,
                   w.confirmation_expires_at AS confirmationExpiresAt
            FROM booking_waitlist w
            JOIN account a ON a.id = ? AND a.member_id = w.member_id
            WHERE w.id = ? AND w.tenant_id = ?
            FOR UPDATE
            """,
            principal.accountId(), waitlistId, principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "候补意向不存在");
        return rows.getFirst();
    }

    private long currentMemberId(TenantPrincipal principal) {
        List<Long> rows = jdbcTemplate.queryForList(
            "SELECT member_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND member_id IS NOT NULL",
            Long.class, principal.accountId(), principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "当前账号未关联会员资料");
        return rows.getFirst();
    }

    private void requireMemberRole(TenantPrincipal principal) {
        if (!principal.roles().contains("MEMBER")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有会员可以操作自己的候补意向");
        }
    }

    private void requireServiceAndStaff(long tenantId, long shopId, long serviceId, Long staffId) {
        Integer service = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM service_item WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'",
            Integer.class, serviceId, tenantId, shopId
        );
        if (service == null || service == 0) throw new ApiException(HttpStatus.NOT_FOUND, "护理项目不存在");
        if (staffId != null) {
            Integer staff = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM staff_service ss
                JOIN staff st ON st.id = ss.staff_id AND st.tenant_id = ? AND st.status = 'ACTIVE'
                WHERE ss.staff_id = ? AND ss.service_id = ? AND ss.enabled = 1
                """,
                Integer.class, tenantId, staffId, serviceId
            );
            if (staff == null || staff == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "所选技师不可提供当前项目");
        }
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
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "候补时间数据不完整");
    }

    public record JoinCommand(
        Long shopId,
        long serviceId,
        Long requestedStaffId,
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalTime timeFrom,
        LocalTime timeTo,
        int flexibilityMinutes,
        boolean acceptOtherStaff
    ) {
    }
}
