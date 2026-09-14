package com.face.api;

import com.face.service.FaceAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/client")
public class FaceClientController {
    private final JdbcTemplate jdbcTemplate;
    private final FaceAppointmentService appointmentService;

    @Autowired
    public FaceClientController(JdbcTemplate jdbcTemplate, FaceAppointmentService appointmentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Map<String, Object> account = currentAccount(request);
        return FaceResponses.ok(profile(account));
    }

    @PostMapping("/me")
    public Map<String, Object> updateMe(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> account = currentAccount(request);
        String role = account.get("roleCode").toString();
        Long shopId = number(account.get("shopId"));
        if ("MEMBER".equals(role)) {
            Long memberId = number(account.get("memberId"));
            requireText(body, "name");
            requireText(body, "phone");
            jdbcTemplate.update(
                "UPDATE member SET name = ?, phone = ?, gender = ?, avatar_url = ? WHERE id = ? AND shop_id = ?",
                text(body.get("name")), text(body.get("phone")), nullableText(body.get("gender")),
                nullableText(body.get("avatarUrl")), memberId, shopId
            );
        } else if ("BEAUTICIAN".equals(role)) {
            Long staffId = number(account.get("staffId"));
            requireText(body, "name");
            jdbcTemplate.update(
                "UPDATE staff SET name = ?, phone = ?, avatar_url = ?, bio = ? WHERE id = ? AND shop_id = ?",
                text(body.get("name")), nullableText(body.get("phone")), nullableText(body.get("avatarUrl")),
                nullableText(body.get("bio")), staffId, shopId
            );
        } else {
            throw new SecurityException("管理账号请在管理端维护个人资料");
        }
        return FaceResponses.ok(profile(currentAccount(request)));
    }

    @GetMapping("/appointments")
    public Map<String, Object> appointments(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String date,
                                            HttpServletRequest request) {
        Map<String, Object> account = currentAccount(request);
        return FaceResponses.ok(queryAppointments(account, status, date, 100));
    }

    @PostMapping("/appointments/{id}/cancel")
    public Map<String, Object> cancelAppointment(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        Map<String, Object> account = currentAccount(request);
        if (!"MEMBER".equals(account.get("roleCode"))) {
            throw new SecurityException("只有会员可以从客户端取消预约");
        }
        Long memberId = number(account.get("memberId"));
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM appointment WHERE id = ? AND member_id = ? AND shop_id = ?",
            Integer.class, id, memberId, number(account.get("shopId"))
        );
        if (count == null || count == 0) throw new IllegalArgumentException("预约不存在");
        Integer version = body.get("version") == null ? null : Integer.valueOf(body.get("version").toString());
        appointmentService.changeStatus(id, "CANCELLED", version);
        return FaceResponses.ok(null);
    }

    @PostMapping("/appointments/{id}/status")
    public Map<String, Object> updateAppointmentStatus(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body,
                                                       HttpServletRequest request) {
        Map<String, Object> account = currentAccount(request);
        if (!"BEAUTICIAN".equals(account.get("roleCode"))) {
            throw new SecurityException("只有技师可以在客户端更新服务状态");
        }
        Long staffId = number(account.get("staffId"));
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM appointment WHERE id = ? AND staff_id = ? AND shop_id = ?",
            Integer.class, id, staffId, number(account.get("shopId"))
        );
        if (count == null || count == 0) throw new IllegalArgumentException("预约不存在");
        String status = requireText(body, "status").toUpperCase();
        if (!java.util.Arrays.asList("CONFIRMED", "CHECKED_IN", "IN_SERVICE", "COMPLETED", "NO_SHOW").contains(status)) {
            throw new IllegalArgumentException("不支持的预约状态");
        }
        Integer version = body.get("version") == null ? null : Integer.valueOf(body.get("version").toString());
        appointmentService.changeStatus(id, status, version);
        return FaceResponses.ok(null);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        Map<String, Object> account = currentAccount(request);
        String role = account.get("roleCode").toString();
        Long shopId = number(account.get("shopId"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("profile", profile(account));
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        if ("BEAUTICIAN".equals(role)) {
            Long staffId = number(account.get("staffId"));
            summary.put("todayAppointments", scalar(
                "SELECT COUNT(*) FROM appointment WHERE shop_id = ? AND staff_id = ? AND DATE(start_at) = CURRENT_DATE",
                shopId, staffId
            ));
            summary.put("todayCompleted", scalar(
                "SELECT COUNT(*) FROM appointment WHERE shop_id = ? AND staff_id = ? AND DATE(start_at) = CURRENT_DATE AND status = 'COMPLETED'",
                shopId, staffId
            ));
            summary.put("monthAppointments", scalar(
                "SELECT COUNT(*) FROM appointment WHERE shop_id = ? AND staff_id = ? AND start_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
                shopId, staffId
            ));
            summary.put("servedMembers", scalar(
                "SELECT COUNT(DISTINCT member_id) FROM appointment WHERE shop_id = ? AND staff_id = ? AND status = 'COMPLETED'",
                shopId, staffId
            ));
            data.put("todaySchedule", jdbcTemplate.queryForList(
                "SELECT id, schedule_date AS scheduleDate, start_time AS startTime, end_time AS endTime, " +
                    "schedule_type AS scheduleType, remark FROM staff_schedule " +
                    "WHERE shop_id = ? AND staff_id = ? AND schedule_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 6 DAY) " +
                    "ORDER BY schedule_date, start_time",
                shopId, staffId
            ));
            data.put("appointments", queryAppointments(account, null, null, 12));
        } else if ("MEMBER".equals(role)) {
            Long memberId = number(account.get("memberId"));
            summary.put("allAppointments", scalar(
                "SELECT COUNT(*) FROM appointment WHERE shop_id = ? AND member_id = ?", shopId, memberId
            ));
            summary.put("completed", scalar(
                "SELECT COUNT(*) FROM appointment WHERE shop_id = ? AND member_id = ? AND status = 'COMPLETED'", shopId, memberId
            ));
            summary.put("upcoming", scalar(
                "SELECT COUNT(*) FROM appointment WHERE shop_id = ? AND member_id = ? AND start_at >= CURRENT_TIMESTAMP(3) " +
                    "AND status IN ('PENDING','CONFIRMED','CHECKED_IN')", shopId, memberId
            ));
            summary.put("points", jdbcTemplate.queryForObject(
                "SELECT points FROM member WHERE id = ? AND shop_id = ?", Long.class, memberId, shopId
            ));
            data.put("appointments", queryAppointments(account, null, null, 8));
        } else {
            throw new SecurityException("当前账号不属于客户端角色");
        }
        data.put("summary", summary);
        return FaceResponses.ok(data);
    }

    private Map<String, Object> currentAccount(HttpServletRequest request) {
        Object userId = request.getSession().getAttribute("userId");
        if (userId == null) throw new SecurityException("登录状态已失效");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT a.id AS accountId, a.shop_id AS shopId, a.username, a.role_code AS roleCode, " +
                "a.staff_id AS staffId, a.member_id AS memberId, sh.name AS shopName, sh.phone AS shopPhone, " +
                "sh.address AS shopAddress FROM account a JOIN shop sh ON sh.id = a.shop_id " +
                "WHERE a.id = ? AND a.status = 'ACTIVE' LIMIT 1",
            Long.valueOf(userId.toString())
        );
        if (rows.isEmpty()) throw new SecurityException("账号不存在或已停用");
        return rows.get(0);
    }

    private Map<String, Object> profile(Map<String, Object> account) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(account);
        String role = account.get("roleCode").toString();
        if ("MEMBER".equals(role)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT member_no AS memberNo, name, phone, gender, birthday, avatar_url AS avatarUrl, " +
                    "source, points, status, created_at AS createdAt FROM member WHERE id = ? AND shop_id = ?",
                number(account.get("memberId")), number(account.get("shopId"))
            );
            if (!rows.isEmpty()) result.putAll(rows.get(0));
        } else if ("BEAUTICIAN".equals(role)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT staff_no AS staffNo, name, phone, job_role AS jobRole, level_name AS levelName, " +
                    "avatar_url AS avatarUrl, bio, hire_date AS hireDate, status FROM staff WHERE id = ? AND shop_id = ?",
                number(account.get("staffId")), number(account.get("shopId"))
            );
            if (!rows.isEmpty()) result.putAll(rows.get(0));
        }
        return result;
    }

    private List<Map<String, Object>> queryAppointments(Map<String, Object> account,
                                                        String status,
                                                        String date,
                                                        int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.appointment_no AS appointmentNo, a.start_at AS startAt, a.end_at AS endAt, " +
                "a.status, a.source, a.member_note AS memberNote, a.version, " +
                "m.id AS memberId, m.name AS memberName, m.phone AS memberPhone, " +
                "s.id AS staffId, s.name AS staffName, s.avatar_url AS staffAvatarUrl, " +
                "GROUP_CONCAT(ai.service_name_snapshot ORDER BY ai.sort_order SEPARATOR '、') AS serviceNames, " +
                "COALESCE(SUM(ai.price_snapshot), 0) AS totalPrice FROM appointment a " +
                "JOIN member m ON m.id = a.member_id JOIN staff s ON s.id = a.staff_id " +
                "LEFT JOIN appointment_item ai ON ai.appointment_id = a.id WHERE a.shop_id = ?"
        );
        List<Object> args = new ArrayList<Object>();
        args.add(number(account.get("shopId")));
        String role = account.get("roleCode").toString();
        if ("MEMBER".equals(role)) {
            sql.append(" AND a.member_id = ?");
            args.add(number(account.get("memberId")));
        } else if ("BEAUTICIAN".equals(role)) {
            sql.append(" AND a.staff_id = ?");
            args.add(number(account.get("staffId")));
        } else {
            throw new SecurityException("当前账号不属于客户端角色");
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND a.status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (date != null && !date.trim().isEmpty()) {
            sql.append(" AND DATE(a.start_at) = ?");
            args.add(java.sql.Date.valueOf(date));
        }
        sql.append(" GROUP BY a.id, a.appointment_no, a.start_at, a.end_at, a.status, a.source, a.member_note, " +
            "a.version, m.id, m.name, m.phone, s.id, s.name, s.avatar_url ORDER BY a.start_at DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 100));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private Long scalar(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private Long number(Object value) {
        if (value == null) throw new IllegalArgumentException("缺少账号关联数据");
        return ((Number) value).longValue();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String nullableText(Object value) {
        String result = text(value);
        return result.isEmpty() ? null : result;
    }

    private String requireText(Map<String, Object> body, String field) {
        String value = text(body.get(field));
        if (value.isEmpty()) throw new IllegalArgumentException("请填写" + field);
        return value;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, SecurityException.class})
    public Map<String, Object> handleBusinessException(RuntimeException exception) {
        int code = exception instanceof SecurityException ? 403 : 409;
        return FaceResponses.error(code, exception.getMessage());
    }
}
