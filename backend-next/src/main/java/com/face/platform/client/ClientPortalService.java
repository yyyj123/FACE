package com.face.platform.client;

import com.face.platform.api.ApiException;
import com.face.platform.appointment.AppointmentCreateRequest;
import com.face.platform.appointment.AppointmentService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClientPortalService {

    private final JdbcTemplate jdbcTemplate;
    private final AppointmentService appointmentService;
    private final ShopContextService shopContextService;

    public ClientPortalService(
        JdbcTemplate jdbcTemplate,
        AppointmentService appointmentService,
        ShopContextService shopContextService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.appointmentService = appointmentService;
        this.shopContextService = shopContextService;
    }

    public Map<String, Object> profile(TenantPrincipal principal) {
        Map<String, Object> account = currentAccount(principal);
        String role = account.get("roleCode").toString();
        Map<String, Object> result = new LinkedHashMap<>(account);
        if ("MEMBER".equals(role)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT member_no AS memberNo, global_member_no AS globalMemberNo,
                       name, phone, gender, birthday, avatar_url AS avatarUrl,
                       source, points, status, created_at AS createdAt
                FROM member
                WHERE id = ? AND tenant_id = ?
                LIMIT 1
                """,
                requiredNumber(account.get("memberId")),
                principal.tenantId()
            );
            if (rows.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "会员资料不存在");
            }
            result.putAll(rows.getFirst());
        } else if ("BEAUTICIAN".equals(role)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT staff_no AS staffNo, name, phone, job_role AS jobRole,
                       level_name AS levelName, avatar_url AS avatarUrl, bio,
                       hire_date AS hireDate, status
                FROM staff
                WHERE id = ? AND tenant_id = ?
                LIMIT 1
                """,
                requiredNumber(account.get("staffId")),
                principal.tenantId()
            );
            if (rows.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "技师资料不存在");
            }
            result.putAll(rows.getFirst());
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "管理账号请在管理端维护个人资料");
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateProfile(TenantPrincipal principal, Map<String, Object> body) {
        Map<String, Object> account = currentAccount(principal);
        String role = account.get("roleCode").toString();
        String name = requiredText(body, "name", "请填写姓名");
        if ("MEMBER".equals(role)) {
            String phone = requiredText(body, "phone", "请填写手机号");
            long memberId = requiredNumber(account.get("memberId"));
            Integer duplicate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE tenant_id = ? AND phone = ? AND id <> ?",
                Integer.class,
                principal.tenantId(),
                phone,
                memberId
            );
            if (duplicate != null && duplicate > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "该手机号已经属于另一位会员");
            }
            jdbcTemplate.update(
                """
                UPDATE member
                SET name = ?, phone = ?, gender = ?, avatar_url = ?, version = version + 1
                WHERE id = ? AND tenant_id = ?
                """,
                name,
                phone,
                nullableText(body.get("gender")),
                nullableText(body.get("avatarUrl")),
                memberId,
                principal.tenantId()
            );
        } else if ("BEAUTICIAN".equals(role)) {
            jdbcTemplate.update(
                """
                UPDATE staff
                SET name = ?, phone = ?, avatar_url = ?, bio = ?
                WHERE id = ? AND tenant_id = ?
                """,
                name,
                nullableText(body.get("phone")),
                nullableText(body.get("avatarUrl")),
                nullableText(body.get("bio")),
                requiredNumber(account.get("staffId")),
                principal.tenantId()
            );
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "管理账号请在管理端维护个人资料");
        }
        jdbcTemplate.update(
            "UPDATE account SET display_name = ? WHERE id = ? AND tenant_id = ?",
            name,
            principal.accountId(),
            principal.tenantId()
        );
        return profile(principal);
    }

    public List<Map<String, Object>> appointments(
        TenantPrincipal principal,
        String status,
        LocalDate date,
        int limit
    ) {
        Map<String, Object> account = currentAccount(principal);
        String role = account.get("roleCode").toString();
        StringBuilder sql = new StringBuilder(
            """
            SELECT a.id, a.appointment_no AS appointmentNo,
                   a.shop_id AS shopId, sh.name AS shopName,
                   a.start_at AS startAt, a.end_at AS endAt,
                   a.status, a.source, a.member_note AS memberNote,
                   a.cancel_reason AS cancelReason, a.version,
                   m.id AS memberId, m.name AS memberName, m.phone AS memberPhone,
                   st.id AS staffId, st.name AS staffName,
                   st.avatar_url AS staffAvatarUrl,
                   sr.id AS serviceRecordId, sr.status AS serviceRecordStatus,
                   sr.version AS serviceRecordVersion,
                   cc.id AS confirmationId, cc.status AS confirmationStatus,
                   cc.version AS confirmationVersion,
                   so.id AS orderId, so.status AS orderStatus,
                   so.payable_amount AS payableAmount,
                   so.paid_amount AS paidAmount, so.version AS orderVersion,
                   (SELECT GROUP_CONCAT(
                      ai.service_name_snapshot ORDER BY ai.sort_order SEPARATOR '、'
                    )
                    FROM appointment_item ai
                    WHERE ai.appointment_id = a.id) AS serviceNames,
                   (SELECT COALESCE(SUM(ai.price_snapshot), 0)
                    FROM appointment_item ai
                    WHERE ai.appointment_id = a.id) AS totalPrice
            FROM appointment a
            JOIN shop sh ON sh.id = a.shop_id
            JOIN member m ON m.id = a.member_id AND m.tenant_id = a.tenant_id
            JOIN staff st ON st.id = a.staff_id AND st.tenant_id = a.tenant_id
            LEFT JOIN service_record sr
              ON sr.appointment_id = a.id AND sr.tenant_id = a.tenant_id
            LEFT JOIN customer_confirmation cc
              ON cc.service_record_id = sr.id
             AND cc.tenant_id = a.tenant_id
             AND cc.confirmation_type = 'SERVICE_RESULT'
            LEFT JOIN sales_order so
              ON so.id = (
                SELECT MAX(so2.id)
                FROM sales_order so2
                WHERE so2.appointment_id = a.id
                  AND so2.tenant_id = a.tenant_id
              )
            WHERE a.tenant_id = ?
            """
        );
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        if ("MEMBER".equals(role)) {
            sql.append(" AND a.member_id = ?");
            args.add(requiredNumber(account.get("memberId")));
        } else if ("BEAUTICIAN".equals(role)) {
            sql.append(" AND a.staff_id = ?");
            args.add(requiredNumber(account.get("staffId")));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号不属于客户端角色");
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND a.status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (date != null) {
            sql.append(" AND DATE(a.start_at) = ?");
            args.add(Date.valueOf(date));
        }
        sql.append(
            """
             ORDER BY a.start_at DESC, a.id DESC
             LIMIT ?
            """
        );
        args.add(Math.min(Math.max(limit, 1), 100));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> dashboard(TenantPrincipal principal) {
        Map<String, Object> account = currentAccount(principal);
        String role = account.get("roleCode").toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", profile(principal));
        Map<String, Object> summary = new LinkedHashMap<>();
        if ("BEAUTICIAN".equals(role)) {
            long staffId = requiredNumber(account.get("staffId"));
            summary.put("todayAppointments", scalar(
                "SELECT COUNT(*) FROM appointment WHERE tenant_id = ? AND staff_id = ? AND DATE(start_at) = CURRENT_DATE",
                principal.tenantId(), staffId
            ));
            summary.put("todayCompleted", scalar(
                "SELECT COUNT(*) FROM appointment WHERE tenant_id = ? AND staff_id = ? AND DATE(start_at) = CURRENT_DATE AND status = 'COMPLETED'",
                principal.tenantId(), staffId
            ));
            summary.put("monthAppointments", scalar(
                "SELECT COUNT(*) FROM appointment WHERE tenant_id = ? AND staff_id = ? AND start_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
                principal.tenantId(), staffId
            ));
            summary.put("servedMembers", scalar(
                "SELECT COUNT(DISTINCT member_id) FROM appointment WHERE tenant_id = ? AND staff_id = ? AND status = 'COMPLETED'",
                principal.tenantId(), staffId
            ));
            data.put("todaySchedule", jdbcTemplate.queryForList(
                """
                SELECT id, shop_id AS shopId, schedule_date AS scheduleDate,
                       start_time AS startTime, end_time AS endTime,
                       schedule_type AS scheduleType, remark
                FROM staff_schedule
                WHERE tenant_id = ? AND staff_id = ?
                  AND schedule_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 6 DAY)
                ORDER BY schedule_date, start_time
                """,
                principal.tenantId(),
                staffId
            ));
            data.put("appointments", appointments(principal, null, null, 12));
        } else if ("MEMBER".equals(role)) {
            long memberId = requiredNumber(account.get("memberId"));
            summary.put("allAppointments", scalar(
                "SELECT COUNT(*) FROM appointment WHERE tenant_id = ? AND member_id = ?",
                principal.tenantId(), memberId
            ));
            summary.put("completed", scalar(
                "SELECT COUNT(*) FROM appointment WHERE tenant_id = ? AND member_id = ? AND status = 'COMPLETED'",
                principal.tenantId(), memberId
            ));
            summary.put("upcoming", scalar(
                """
                SELECT COUNT(*) FROM appointment
                WHERE tenant_id = ? AND member_id = ? AND start_at >= CURRENT_TIMESTAMP(3)
                  AND status IN ('PENDING','CONFIRMED','CHECKED_IN')
                """,
                principal.tenantId(), memberId
            ));
            summary.put("points", jdbcTemplate.queryForObject(
                "SELECT points FROM member WHERE id = ? AND tenant_id = ?",
                Long.class,
                memberId,
                principal.tenantId()
            ));
            data.put("appointments", appointments(principal, null, null, 8));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号不属于客户端角色");
        }
        data.put("summary", summary);
        return data;
    }

    public Map<String, Object> createAppointment(
        TenantPrincipal principal,
        AppointmentCreateRequest request
    ) {
        shopContextService.requireTenantShop(principal.tenantId(), request.shopId());
        return appointmentService.createForMember(principal, request);
    }

    public Map<String, Object> cancelAppointment(
        TenantPrincipal principal,
        long appointmentId,
        Integer version
    ) {
        return appointmentService.changeStatusForClient(
            principal,
            appointmentId,
            "CANCELLED",
            version,
            "会员从客户端取消"
        );
    }

    public Map<String, Object> updateAppointmentStatus(
        TenantPrincipal principal,
        long appointmentId,
        String status,
        Integer version,
        String reason
    ) {
        return appointmentService.changeStatusForClient(
            principal,
            appointmentId,
            status,
            version,
            reason
        );
    }

    private Map<String, Object> currentAccount(TenantPrincipal principal) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT a.id AS accountId, a.tenant_id AS tenantId,
                   a.home_shop_id AS shopId, a.username,
                   a.role_code AS roleCode, a.staff_id AS staffId,
                   a.member_id AS memberId, sh.name AS shopName,
                   sh.phone AS shopPhone, sh.address AS shopAddress
            FROM account a
            JOIN shop sh ON sh.id = a.home_shop_id AND sh.tenant_id = a.tenant_id
            WHERE a.id = ? AND a.tenant_id = ? AND a.status = 'ACTIVE'
            LIMIT 1
            """,
            principal.accountId(),
            principal.tenantId()
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "账号不存在或已停用");
        }
        Map<String, Object> account = rows.getFirst();
        String role = account.get("roleCode").toString();
        if (!principal.roles().contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "账号角色授权已失效");
        }
        return account;
    }

    private long scalar(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0L : result;
    }

    private long requiredNumber(Object value) {
        if (!(value instanceof Number number)) {
            throw new ApiException(HttpStatus.CONFLICT, "账号关联资料不完整");
        }
        return number.longValue();
    }

    private String requiredText(Map<String, Object> body, String field, String message) {
        Object raw = body == null ? null : body.get(field);
        String value = raw == null ? "" : raw.toString().trim();
        if (value.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, message);
        return value;
    }

    private String nullableText(Object raw) {
        if (raw == null) return null;
        String value = raw.toString().trim();
        return value.isEmpty() ? null : value;
    }
}
