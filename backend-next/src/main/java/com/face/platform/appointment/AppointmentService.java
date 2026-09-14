package com.face.platform.appointment;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.resource.ResourceBookingService;
import com.face.platform.servicecare.ServiceRecordLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AppointmentService {

    private static final List<String> OCCUPYING_STATUSES = List.of(
        "PENDING", "CONFIRMED", "CHECKED_IN", "IN_SERVICE"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final ServiceRecordLifecycleService serviceRecordLifecycleService;
    private final ResourceBookingService resourceBookingService;

    public AppointmentService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        ServiceRecordLifecycleService serviceRecordLifecycleService,
        ResourceBookingService resourceBookingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.serviceRecordLifecycleService = serviceRecordLifecycleService;
        this.resourceBookingService = resourceBookingService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        String keyword,
        int page,
        int pageSize
    ) {
        requireManagementAppointmentAccess(principal);
        List<Long> scope = tenantAccessService.accessibleShopIds(principal, "appointment:view");
        if (shopId != null) {
            tenantAccessService.requireShopPermission(principal, shopId, "appointment:view");
            scope = List.of(shopId);
        }
        if (scope.isEmpty()) {
            return pageResult(List.of(), 0L, Map.of(), Math.max(page, 1), safePageSize(pageSize));
        }

        LocalDate safeFrom = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate safeTo = toDate == null ? safeFrom : toDate;
        if (safeTo.isBefore(safeFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (safeFrom.plusDays(62).isBefore(safeTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单次最多查询63天预约");
        }

        int safePage = Math.max(page, 1);
        int safeLimit = safePageSize(pageSize);
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = trimToNull(keyword);
        List<Object> args = new ArrayList<>();
        String where = buildWhere(
            principal, scope, shopId, safeFrom, safeTo, normalizedStatus, normalizedKeyword, args
        );

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeLimit);
        listArgs.add((safePage - 1) * safeLimit);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT a.id,
                   a.appointment_no AS appointmentNo,
                   a.shop_id AS shopId,
                   sh.name AS shopName,
                   a.member_id AS memberId,
                   m.member_no AS memberNo,
                   m.name AS memberName,
                   m.phone AS memberPhone,
                   a.staff_id AS staffId,
                   st.name AS staffName,
                   st.level_name AS staffLevel,
                   a.start_at AS startAt,
                   a.end_at AS endAt,
                   a.status,
                   a.source,
                   a.member_note AS memberNote,
                   a.internal_note AS internalNote,
                   a.cancel_reason AS cancelReason,
                   a.version,
                   a.created_at AS createdAt,
                   (SELECT GROUP_CONCAT(ai.service_name_snapshot ORDER BY ai.sort_order SEPARATOR '、')
                    FROM appointment_item ai
                    WHERE ai.appointment_id = a.id) AS serviceNames,
                   (SELECT GROUP_CONCAT(ai.service_id ORDER BY ai.sort_order)
                    FROM appointment_item ai
                    WHERE ai.appointment_id = a.id) AS serviceIds,
                   COALESCE((SELECT SUM(ai.price_snapshot)
                             FROM appointment_item ai
                             WHERE ai.appointment_id = a.id), 0) AS totalPrice
            FROM appointment a
            JOIN shop sh ON sh.id = a.shop_id
            JOIN member m ON m.id = a.member_id
            JOIN staff st ON st.id = a.staff_id
            %s
            ORDER BY a.start_at, a.id
            LIMIT ? OFFSET ?
            """.formatted(where),
            listArgs.toArray()
        );
        long total = count(where, args);
        Map<String, Long> summary = statusSummary(
            principal, scope, shopId, safeFrom, safeTo, normalizedKeyword
        );
        return pageResult(records, total, summary, safePage, safeLimit);
    }

    public Map<String, Object> resources(
        TenantPrincipal principal,
        long shopId,
        LocalDate date
    ) {
        requireManagementAppointmentAccess(principal);
        tenantAccessService.requireShopPermission(principal, shopId, "appointment:view");
        LocalDate safeDate = date == null ? LocalDate.now() : date;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("members", jdbcTemplate.queryForList(
            """
            SELECT m.id, m.member_no AS memberNo, m.name, m.phone
            FROM member m
            JOIN member_shop_profile msp
              ON msp.member_id = m.id
             AND msp.tenant_id = m.tenant_id
            WHERE m.tenant_id = ?
              AND msp.shop_id = ?
              AND m.status = 'ACTIVE'
              AND msp.status = 'ACTIVE'
            ORDER BY m.name, m.id
            """,
            principal.tenantId(),
            shopId
        ));
        result.put("services", jdbcTemplate.queryForList(
            """
            SELECT si.id, si.service_code AS serviceCode, si.name,
                   si.duration_minutes AS durationMinutes,
                   si.cleanup_minutes AS cleanupMinutes,
                   si.list_price AS listPrice,
                   COALESCE(si.member_price, si.list_price) AS memberPrice
            FROM service_item si
            WHERE si.tenant_id = ? AND si.shop_id = ? AND si.status = 'ACTIVE'
            ORDER BY si.name, si.id
            """,
            principal.tenantId(),
            shopId
        ));
        result.put("staff", jdbcTemplate.queryForList(
            """
            SELECT st.id, st.staff_no AS staffNo, st.name,
                   st.job_role AS jobRole, st.level_name AS levelName,
                   GROUP_CONCAT(ss.service_id ORDER BY ss.service_id) AS serviceIds
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id
             AND ssa.tenant_id = st.tenant_id
             AND ssa.shop_id = ?
             AND ssa.status = 'ACTIVE'
             AND ssa.effective_from <= ?
             AND (ssa.effective_to IS NULL OR ssa.effective_to >= ?)
            LEFT JOIN staff_service ss
              ON ss.staff_id = st.id AND ss.enabled = 1
            WHERE st.tenant_id = ? AND st.status = 'ACTIVE'
            GROUP BY st.id, st.staff_no, st.name, st.job_role, st.level_name
            ORDER BY st.name, st.id
            """,
            shopId,
            safeDate,
            safeDate,
            principal.tenantId()
        ));
        result.put("schedules", jdbcTemplate.queryForList(
            """
            SELECT ss.id, ss.staff_id AS staffId, st.name AS staffName,
                   ss.start_time AS startTime, ss.end_time AS endTime,
                   ss.schedule_type AS scheduleType, ss.remark
            FROM staff_schedule ss
            JOIN staff st ON st.id = ss.staff_id
            WHERE ss.tenant_id = ? AND ss.shop_id = ? AND ss.schedule_date = ?
            ORDER BY st.name, ss.start_time, ss.id
            """,
            principal.tenantId(),
            shopId,
            safeDate
        ));
        result.put("date", safeDate.toString());
        return result;
    }

    private void requireManagementAppointmentAccess(TenantPrincipal principal) {
        if (principal.roles().contains("BEAUTICIAN")) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "技师账号请使用独立技师端的本人预约接口"
            );
        }
    }

    public Map<String, Object> availability(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        LocalDate date,
        List<Long> serviceIds
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "appointment:view");
        if (date == null) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择预约日期");
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少选择一个美容项目");
        }
        List<Map<String, Object>> services = loadServices(principal, shopId, serviceIds);
        validateStaff(principal, shopId, staffId, date);
        validateStaffSkills(staffId, serviceIds);
        int durationMinutes = totalDuration(staffId, services);
        return calculateAvailability(
            principal, shopId, staffId, date, durationMinutes, null
        );
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        AppointmentCreateRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "appointment:manage"
        );
        return createValidated(principal, request, shopId);
    }

    @Transactional
    public Map<String, Object> createForMember(
        TenantPrincipal principal,
        AppointmentCreateRequest request
    ) {
        requireClientRole(principal, "MEMBER");
        long shopId = tenantAccessService.requireShopAccess(principal, request.shopId());
        Long memberId = jdbcTemplate.queryForObject(
            "SELECT member_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            Long.class,
            principal.accountId(),
            principal.tenantId()
        );
        if (memberId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前会员账号未关联会员资料");
        }
        AppointmentCreateRequest scopedRequest = new AppointmentCreateRequest(
            shopId,
            memberId,
            request.staffId(),
            request.serviceIds(),
            request.resourceIds(),
            request.startAt(),
            "ONLINE",
            request.memberNote(),
            null
        );
        return createValidated(principal, scopedRequest, shopId);
    }

    private Map<String, Object> createValidated(
        TenantPrincipal principal,
        AppointmentCreateRequest request,
        long shopId
    ) {
        String source = request.source() == null ? "FRONT_DESK" : request.source();
        LocalDateTime startAt = request.startAt();
        if (startAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能创建过去时间的预约");
        }

        validateMember(principal, shopId, request.memberId());
        List<Map<String, Object>> services = loadServices(principal, shopId, request.serviceIds());
        validateStaff(principal, shopId, request.staffId(), startAt.toLocalDate());
        validateStaffSkills(request.staffId(), request.serviceIds());
        int durationMinutes = totalDuration(request.staffId(), services);
        LocalDateTime endAt = startAt.plusMinutes(durationMinutes);
        validateSameBusinessDay(startAt, endAt);
        validateSchedule(principal, shopId, request.staffId(), startAt, endAt);
        lockStaff(principal, request.staffId());
        validateNoOverlap(principal, request.staffId(), startAt, endAt, null);

        String appointmentNo = createAppointmentNo();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO appointment (
                    tenant_id, shop_id, appointment_no, member_id, staff_id,
                    start_at, end_at, occupied_start_at, occupied_end_at,
                    status, source, member_note, internal_note,
                    version, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, appointmentNo);
            statement.setLong(4, request.memberId());
            statement.setLong(5, request.staffId());
            statement.setTimestamp(6, Timestamp.valueOf(startAt));
            statement.setTimestamp(7, Timestamp.valueOf(endAt));
            statement.setTimestamp(8, Timestamp.valueOf(startAt));
            statement.setTimestamp(9, Timestamp.valueOf(endAt));
            statement.setString(10, source);
            statement.setString(11, trimToNull(request.memberNote()));
            statement.setString(12, trimToNull(request.internalNote()));
            statement.setLong(13, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约编号生成失败");
        long appointmentId = key.longValue();
        insertAppointmentItems(appointmentId, request.staffId(), services);
        resourceBookingService.reserveForAuthorizedAppointment(
            principal,
            shopId,
            appointmentId,
            request.resourceIds(),
            startAt,
            endAt
        );
        recordHistory(
            principal, shopId, appointmentId, null, "PENDING", "创建预约", 0
        );
        audit(principal, shopId, "APPOINTMENT_CREATE", appointmentId, "PENDING", startAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", appointmentId);
        result.put("appointmentNo", appointmentNo);
        result.put("startAt", startAt);
        result.put("endAt", endAt);
        result.put("status", "PENDING");
        result.put("version", 0);
        return result;
    }

    @Transactional
    public Map<String, Object> reschedule(
        TenantPrincipal principal,
        long appointmentId,
        AppointmentRescheduleRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "appointment:manage"
        );
        Map<String, Object> current = lockAppointment(
            principal, shopId, appointmentId, request.version()
        );
        String status = current.get("status").toString();
        if (!List.of("PENDING", "CONFIRMED").contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "当前状态不能改期");
        }
        LocalDateTime startAt = request.startAt();
        if (startAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能改到过去时间");
        }

        List<Long> serviceIds = jdbcTemplate.queryForList(
            "SELECT service_id FROM appointment_item WHERE appointment_id = ? ORDER BY sort_order",
            Long.class,
            appointmentId
        );
        List<Map<String, Object>> services = loadServices(principal, shopId, serviceIds);
        validateStaff(principal, shopId, request.staffId(), startAt.toLocalDate());
        validateStaffSkills(request.staffId(), serviceIds);
        int durationMinutes = totalDuration(request.staffId(), services);
        LocalDateTime endAt = startAt.plusMinutes(durationMinutes);
        validateSameBusinessDay(startAt, endAt);
        validateSchedule(principal, shopId, request.staffId(), startAt, endAt);
        lockStaff(principal, request.staffId());
        validateNoOverlap(principal, request.staffId(), startAt, endAt, appointmentId);

        int changed = jdbcTemplate.update(
            """
            UPDATE appointment
            SET staff_id = ?, rescheduled_from_at = start_at,
                start_at = ?, end_at = ?, occupied_start_at = ?, occupied_end_at = ?,
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            request.staffId(),
            Timestamp.valueOf(startAt),
            Timestamp.valueOf(endAt),
            Timestamp.valueOf(startAt),
            Timestamp.valueOf(endAt),
            principal.accountId(),
            appointmentId,
            principal.tenantId(),
            shopId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "预约已被其他人修改，请刷新后重试");
        }
        List<Long> resourceIds = request.resourceIds() == null
            ? resourceBookingService.activeResourceIds(
                principal.tenantId(),
                shopId,
                appointmentId
            )
            : request.resourceIds();
        resourceBookingService.releaseForAuthorizedAppointment(
            principal,
            shopId,
            appointmentId,
            "RESCHEDULED"
        );
        resourceBookingService.reserveForAuthorizedAppointment(
            principal,
            shopId,
            appointmentId,
            resourceIds,
            startAt,
            endAt
        );
        audit(principal, shopId, "APPOINTMENT_RESCHEDULE", appointmentId, status, startAt);
        if (trimToNull(request.reason()) != null) {
            jdbcTemplate.update(
                """
                INSERT INTO appointment_status_history (
                    tenant_id, shop_id, appointment_id, from_status, to_status,
                    reason, changed_by, appointment_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                principal.tenantId(),
                shopId,
                appointmentId,
                status,
                status,
                "改期：" + request.reason().trim(),
                principal.accountId(),
                request.version() + 1
            );
        }
        return Map.of(
            "id", appointmentId,
            "startAt", startAt,
            "endAt", endAt,
            "version", request.version() + 1
        );
    }

    @Transactional
    public Map<String, Object> changeStatus(
        TenantPrincipal principal,
        long appointmentId,
        AppointmentStatusRequest request
    ) {
        rejectGenericCompletion(request.status());
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "appointment:manage"
        );
        return changeStatusValidated(principal, appointmentId, request, shopId);
    }

    @Transactional
    public Map<String, Object> changeStatusForClient(
        TenantPrincipal principal,
        long appointmentId,
        String status,
        Integer version,
        String reason
    ) {
        if (version == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少预约数据版本");
        }
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        rejectGenericCompletion(normalizedStatus);
        boolean member = principal.roles().contains("MEMBER");
        boolean beautician = principal.roles().contains("BEAUTICIAN");
        if (!member && !beautician) {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号不属于客户端角色");
        }
        if (member && !"CANCELLED".equals(normalizedStatus)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "会员只能取消自己的预约");
        }
        if (beautician && !List.of(
            "CONFIRMED", "CHECKED_IN", "IN_SERVICE", "NO_SHOW"
        ).contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的预约状态");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT a.shop_id AS shopId
            FROM appointment a
            JOIN account ac ON ac.id = ? AND ac.tenant_id = a.tenant_id
            WHERE a.id = ? AND a.tenant_id = ?
              AND (
                    (? = 1 AND a.member_id = ac.member_id)
                 OR (? = 1 AND a.staff_id = ac.staff_id)
              )
            LIMIT 1
            """,
            principal.accountId(),
            appointmentId,
            principal.tenantId(),
            member ? 1 : 0,
            beautician ? 1 : 0
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "预约不存在");
        }
        long shopId = ((Number) rows.getFirst().get("shopId")).longValue();
        tenantAccessService.requireShopAccess(principal, shopId);
        String safeReason = reason;
        if ("CANCELLED".equals(normalizedStatus) && trimToNull(safeReason) == null) {
            safeReason = "会员从客户端取消";
        }
        AppointmentStatusRequest scopedRequest = new AppointmentStatusRequest(
            shopId,
            normalizedStatus,
            version,
            safeReason
        );
        return changeStatusValidated(principal, appointmentId, scopedRequest, shopId);
    }

    private Map<String, Object> changeStatusValidated(
        TenantPrincipal principal,
        long appointmentId,
        AppointmentStatusRequest request,
        long shopId
    ) {
        rejectGenericCompletion(request.status());
        Map<String, Object> current = lockAppointment(
            principal, shopId, appointmentId, request.version()
        );
        String fromStatus = current.get("status").toString();
        String toStatus = request.status().trim().toUpperCase();
        AppointmentStatusPolicy.requireTransition(fromStatus, toStatus);
        if (List.of("CANCELLED", "NO_SHOW").contains(toStatus)
            && trimToNull(request.reason()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "取消或爽约必须填写原因");
        }

        int changed = jdbcTemplate.update(
            """
            UPDATE appointment
            SET status = ?,
                version = version + 1,
                updated_by = ?,
                confirmed_by = CASE WHEN ? = 'CONFIRMED' THEN ? ELSE confirmed_by END,
                confirmed_at = CASE WHEN ? = 'CONFIRMED' THEN CURRENT_TIMESTAMP(3) ELSE confirmed_at END,
                checked_in_at = CASE WHEN ? = 'CHECKED_IN' THEN CURRENT_TIMESTAMP(3) ELSE checked_in_at END,
                started_at = CASE WHEN ? = 'IN_SERVICE' THEN CURRENT_TIMESTAMP(3) ELSE started_at END,
                completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP(3) ELSE completed_at END,
                cancelled_at = CASE WHEN ? = 'CANCELLED' THEN CURRENT_TIMESTAMP(3) ELSE cancelled_at END,
                no_show_at = CASE WHEN ? = 'NO_SHOW' THEN CURRENT_TIMESTAMP(3) ELSE no_show_at END,
                cancel_reason = CASE WHEN ? IN ('CANCELLED', 'NO_SHOW') THEN ? ELSE cancel_reason END
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            toStatus,
            principal.accountId(),
            toStatus,
            principal.accountId(),
            toStatus,
            toStatus,
            toStatus,
            toStatus,
            toStatus,
            toStatus,
            toStatus,
            trimToNull(request.reason()),
            appointmentId,
            principal.tenantId(),
            shopId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "预约已被其他人修改，请刷新后重试");
        }
        if ("IN_SERVICE".equals(toStatus)) {
            serviceRecordLifecycleService.ensureInProgress(principal, shopId, appointmentId);
        } else if ("COMPLETED".equals(toStatus)) {
            serviceRecordLifecycleService.completeFromAppointment(
                principal, shopId, appointmentId
            );
        }
        if (List.of("CANCELLED", "NO_SHOW", "COMPLETED").contains(toStatus)) {
            resourceBookingService.releaseForAuthorizedAppointment(
                principal,
                shopId,
                appointmentId,
                toStatus
            );
        }
        recordHistory(
            principal,
            shopId,
            appointmentId,
            fromStatus,
            toStatus,
            trimToNull(request.reason()),
            request.version() + 1
        );
        audit(principal, shopId, "APPOINTMENT_STATUS_" + toStatus, appointmentId, toStatus, null);
        return Map.of(
            "id", appointmentId,
            "status", toStatus,
            "version", request.version() + 1,
            "allowedNext", AppointmentStatusPolicy.allowedNext(toStatus)
        );
    }

    private void requireClientRole(TenantPrincipal principal, String role) {
        if (!principal.roles().contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号没有客户端操作权限");
        }
    }

    private String buildWhere(
        TenantPrincipal principal,
        List<Long> scope,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        String keyword,
        List<Object> args
    ) {
        StringBuilder where = new StringBuilder(
            "WHERE a.tenant_id = ? AND a.start_at >= ? AND a.start_at < ?"
        );
        args.add(principal.tenantId());
        args.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        args.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        if (scope.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号没有该功能的可管理门店");
        }
        where.append(" AND a.shop_id IN (").append(placeholders(scope.size())).append(")");
        args.addAll(scope);
        if (status != null) {
            where.append(" AND a.status = ?");
            args.add(status);
        }
        if (keyword != null) {
            where.append(
                " AND (a.appointment_no LIKE ? OR m.name LIKE ? OR m.phone LIKE ? OR st.name LIKE ?)"
            );
            String pattern = "%" + keyword + "%";
            Collections.addAll(args, pattern, pattern, pattern, pattern);
        }
        return where.toString();
    }

    private void rejectGenericCompletion(String status) {
        if (status != null && "COMPLETED".equals(status.trim().toUpperCase())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "预约完成必须通过护理记录完成接口提交"
            );
        }
    }

    private Map<String, Long> statusSummary(
        TenantPrincipal principal,
        List<Long> scope,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String keyword
    ) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(
            principal, scope, shopId, fromDate, toDate, null, keyword, args
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT a.status, COUNT(*) AS count
            FROM appointment a
            JOIN member m ON m.id = a.member_id
            JOIN staff st ON st.id = a.staff_id
            %s
            GROUP BY a.status
            """.formatted(where),
            args.toArray()
        );
        Map<String, Long> summary = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            summary.put(row.get("status").toString(), ((Number) row.get("count")).longValue());
        }
        return summary;
    }

    private long count(String where, List<Object> args) {
        Long value = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM appointment a
            JOIN member m ON m.id = a.member_id
            JOIN staff st ON st.id = a.staff_id
            %s
            """.formatted(where),
            Long.class,
            args.toArray()
        );
        return value == null ? 0 : value;
    }

    private void validateMember(TenantPrincipal principal, long shopId, long memberId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM member m
            JOIN member_shop_profile msp
              ON msp.member_id = m.id AND msp.tenant_id = m.tenant_id
            WHERE m.id = ? AND m.tenant_id = ? AND m.status = 'ACTIVE'
              AND msp.shop_id = ? AND msp.status = 'ACTIVE'
            """,
            Integer.class,
            memberId,
            principal.tenantId(),
            shopId
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "会员不存在、已停用或不属于当前门店");
        }
    }

    private void validateStaff(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        LocalDate date
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id
             AND ssa.tenant_id = st.tenant_id
            WHERE st.id = ? AND st.tenant_id = ? AND st.status = 'ACTIVE'
              AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
              AND ssa.effective_from <= ?
              AND (ssa.effective_to IS NULL OR ssa.effective_to >= ?)
            """,
            Integer.class,
            staffId,
            principal.tenantId(),
            shopId,
            date,
            date
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "美容师未在该门店有效任职");
        }
    }

    private List<Map<String, Object>> loadServices(
        TenantPrincipal principal,
        long shopId,
        List<Long> serviceIds
    ) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少选择一个美容项目");
        }
        List<Long> distinctIds = serviceIds.stream().distinct().toList();
        String placeholders = placeholders(distinctIds.size());
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        args.addAll(distinctIds);
        List<Map<String, Object>> services = jdbcTemplate.queryForList(
            """
            SELECT id, name, duration_minutes AS durationMinutes,
                   cleanup_minutes AS cleanupMinutes,
                   COALESCE(member_price, list_price) AS price
            FROM service_item
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
              AND id IN (%s)
            ORDER BY FIELD(id, %s)
            """.formatted(placeholders, placeholders),
            concat(args, distinctIds).toArray()
        );
        if (services.size() != distinctIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "部分美容项目不存在或已停用");
        }
        return services;
    }

    private void validateStaffSkills(long staffId, List<Long> serviceIds) {
        List<Long> distinctIds = serviceIds.stream().distinct().toList();
        List<Object> args = new ArrayList<>();
        args.add(staffId);
        args.addAll(distinctIds);
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM staff_service
            WHERE staff_id = ? AND enabled = 1
              AND service_id IN (%s)
            """.formatted(placeholders(distinctIds.size())),
            Integer.class,
            args.toArray()
        );
        if (count == null || count != distinctIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "所选美容师不能提供全部预约项目");
        }
    }

    private int totalDuration(long staffId, List<Map<String, Object>> services) {
        int total = 0;
        for (Map<String, Object> service : services) {
            long serviceId = ((Number) service.get("id")).longValue();
            Integer custom = jdbcTemplate.query(
                """
                SELECT custom_duration_minutes
                FROM staff_service
                WHERE staff_id = ? AND service_id = ? AND enabled = 1
                """,
                resultSet -> resultSet.next() ? (Integer) resultSet.getObject(1) : null,
                staffId,
                serviceId
            );
            int duration = custom == null
                ? ((Number) service.get("durationMinutes")).intValue()
                : custom;
            total += duration + ((Number) service.get("cleanupMinutes")).intValue();
        }
        return total;
    }

    private void validateSchedule(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        Integer workCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND schedule_date = ? AND schedule_type = 'WORK'
              AND start_time <= ? AND end_time >= ?
            """,
            Integer.class,
            principal.tenantId(),
            shopId,
            staffId,
            startAt.toLocalDate(),
            startAt.toLocalTime(),
            endAt.toLocalTime()
        );
        if (workCount == null || workCount == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "所选时间不在美容师排班范围内");
        }
        Integer blockedCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND schedule_date = ? AND schedule_type IN ('LEAVE', 'BLOCKED')
              AND (start_time IS NULL OR start_time < ?)
              AND (end_time IS NULL OR end_time > ?)
            """,
            Integer.class,
            principal.tenantId(),
            shopId,
            staffId,
            startAt.toLocalDate(),
            endAt.toLocalTime(),
            startAt.toLocalTime()
        );
        if (blockedCount != null && blockedCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "美容师在该时段请假或不可预约");
        }
    }

    private void lockStaff(TenantPrincipal principal, long staffId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id FROM staff WHERE id = ? AND tenant_id = ? FOR UPDATE",
            staffId,
            principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "美容师不存在");
    }

    private void validateNoOverlap(
        TenantPrincipal principal,
        long staffId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long excludeAppointmentId
    ) {
        List<Object> args = new ArrayList<>(List.of(
            principal.tenantId(),
            staffId,
            Timestamp.valueOf(endAt),
            Timestamp.valueOf(startAt)
        ));
        String exclude = "";
        if (excludeAppointmentId != null) {
            exclude = " AND id <> ?";
            args.add(excludeAppointmentId);
        }
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM appointment
            WHERE tenant_id = ? AND staff_id = ?
              AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE')
              AND occupied_start_at < ? AND occupied_end_at > ?%s
            """.formatted(exclude),
            Integer.class,
            args.toArray()
        );
        if (count != null && count > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该美容师的预约时段已被占用");
        }
        Integer lockCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM booking_time_lock
            WHERE tenant_id = ? AND staff_id = ? AND status = 'HELD'
              AND expires_at > CURRENT_TIMESTAMP(3)
              AND occupied_start_at < ? AND occupied_end_at > ?
            """,
            Integer.class,
            principal.tenantId(),
            staffId,
            Timestamp.valueOf(endAt),
            Timestamp.valueOf(startAt)
        );
        if (lockCount != null && lockCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该美容师的预约时段正在被其他预约锁定");
        }
    }

    private Map<String, Object> calculateAvailability(
        TenantPrincipal principal,
        long shopId,
        long staffId,
        LocalDate date,
        int durationMinutes,
        Long excludeAppointmentId
    ) {
        List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
            """
            SELECT start_time AS startTime, end_time AS endTime, schedule_type AS scheduleType
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND schedule_date = ?
            ORDER BY start_time
            """,
            principal.tenantId(),
            shopId,
            staffId,
            date
        );
        List<Map<String, Object>> bookings = jdbcTemplate.queryForList(
            """
            SELECT id, appointment_no AS appointmentNo, start_at AS startAt,
                   end_at AS endAt, status
            FROM appointment
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND start_at >= ? AND start_at < ?
              AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE')
            ORDER BY start_at
            """,
            principal.tenantId(),
            shopId,
            staffId,
            Timestamp.valueOf(date.atStartOfDay()),
            Timestamp.valueOf(date.plusDays(1).atStartOfDay())
        );

        List<Map<String, Object>> slots = new ArrayList<>();
        for (Map<String, Object> schedule : schedules) {
            if (!"WORK".equals(schedule.get("scheduleType"))) continue;
            LocalTime workStart = toLocalTime(schedule.get("startTime"));
            LocalTime workEnd = toLocalTime(schedule.get("endTime"));
            if (workStart == null || workEnd == null) continue;
            for (LocalDateTime slotStart = LocalDateTime.of(date, workStart);
                 !slotStart.plusMinutes(durationMinutes).isAfter(LocalDateTime.of(date, workEnd));
                 slotStart = slotStart.plusMinutes(30)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
                if (slotStart.isBefore(LocalDateTime.now().plusMinutes(4))) continue;
                if (overlapsBlocked(schedules, slotStart, slotEnd)) continue;
                if (overlapsBookings(bookings, slotStart, slotEnd, excludeAppointmentId)) continue;
                slots.add(Map.of("startAt", slotStart, "endAt", slotEnd));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("staffId", staffId);
        result.put("durationMinutes", durationMinutes);
        result.put("slots", slots);
        result.put("schedules", schedules);
        result.put("bookings", bookings);
        return result;
    }

    private boolean overlapsBlocked(
        List<Map<String, Object>> schedules,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        for (Map<String, Object> schedule : schedules) {
            if ("WORK".equals(schedule.get("scheduleType"))) continue;
            LocalTime blockedStart = toLocalTime(schedule.get("startTime"));
            LocalTime blockedEnd = toLocalTime(schedule.get("endTime"));
            if (blockedStart == null || blockedEnd == null) return true;
            if (startAt.toLocalTime().isBefore(blockedEnd)
                && endAt.toLocalTime().isAfter(blockedStart)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsBookings(
        List<Map<String, Object>> bookings,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long excludeAppointmentId
    ) {
        for (Map<String, Object> booking : bookings) {
            long id = ((Number) booking.get("id")).longValue();
            if (excludeAppointmentId != null && id == excludeAppointmentId) continue;
            LocalDateTime bookedStart = toLocalDateTime(booking.get("startAt"));
            LocalDateTime bookedEnd = toLocalDateTime(booking.get("endAt"));
            if (bookedStart.isBefore(endAt) && bookedEnd.isAfter(startAt)) return true;
        }
        return false;
    }

    private Map<String, Object> lockAppointment(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        int version
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, status, version, start_at AS startAt, staff_id AS staffId
            FROM appointment
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            appointmentId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "预约不存在或不在当前管理范围");
        }
        int currentVersion = ((Number) rows.getFirst().get("version")).intValue();
        if (currentVersion != version) {
            throw new ApiException(HttpStatus.CONFLICT, "预约已被其他人修改，请刷新后重试");
        }
        return rows.getFirst();
    }

    private void insertAppointmentItems(
        long appointmentId,
        long staffId,
        List<Map<String, Object>> services
    ) {
        int sortOrder = 0;
        for (Map<String, Object> service : services) {
            long serviceId = ((Number) service.get("id")).longValue();
            Integer custom = jdbcTemplate.query(
                """
                SELECT custom_duration_minutes
                FROM staff_service
                WHERE staff_id = ? AND service_id = ? AND enabled = 1
                """,
                resultSet -> resultSet.next() ? (Integer) resultSet.getObject(1) : null,
                staffId,
                serviceId
            );
            int duration = custom == null
                ? ((Number) service.get("durationMinutes")).intValue()
                : custom;
            jdbcTemplate.update(
                """
                INSERT INTO appointment_item (
                    appointment_id, service_id, service_name_snapshot,
                    duration_minutes_snapshot, price_snapshot, sort_order
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                appointmentId,
                serviceId,
                service.get("name"),
                duration,
                service.get("price"),
                sortOrder++
            );
        }
    }

    private void recordHistory(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        String fromStatus,
        String toStatus,
        String reason,
        int version
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO appointment_status_history (
                tenant_id, shop_id, appointment_id, from_status, to_status,
                reason, changed_by, appointment_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            appointmentId,
            fromStatus,
            toStatus,
            reason,
            principal.accountId(),
            version
        );
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        long appointmentId,
        String status,
        LocalDateTime startAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (
                ?, ?, ?, ?, 'APPOINTMENT', ?,
                JSON_OBJECT('status', ?, 'startAt', ?)
            )
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            appointmentId,
            status,
            startAt == null ? null : startAt.toString()
        );
    }

    private Map<String, Object> pageResult(
        List<Map<String, Object>> records,
        long total,
        Map<String, Long> summary,
        int page,
        int pageSize
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("summary", summary);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private void validateSameBusinessDay(LocalDateTime startAt, LocalDateTime endAt) {
        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "预约项目不能跨越两个营业日");
        }
    }

    private String createAppointmentNo() {
        return "AP"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String normalizeStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        normalized = normalized.toUpperCase();
        List<String> allowed = List.of(
            "PENDING", "CONFIRMED", "CHECKED_IN", "IN_SERVICE",
            "COMPLETED", "CANCELLED", "NO_SHOW"
        );
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的预约状态");
        }
        return normalized;
    }

    private int safePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private String placeholders(int size) {
        if (size <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "至少需要一个有效选项");
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private List<Object> concat(List<Object> first, List<Long> second) {
        List<Object> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalTime toLocalTime(Object value) {
        if (value instanceof Time time) return time.toLocalTime();
        if (value instanceof LocalTime localTime) return localTime;
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约时间数据不完整");
    }
}
