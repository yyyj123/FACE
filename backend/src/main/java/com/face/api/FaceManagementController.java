package com.face.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/management")
public class FaceManagementController {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public FaceManagementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/members")
    public Map<String, Object> members(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int limit,
                                       @RequestParam(required = false) String keyword,
                                       HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int offset = (safePage - 1) * safeLimit;
        String like = keyword == null ? "" : keyword.trim();

        String filter = like.isEmpty() ? "" : " AND (m.name LIKE ? OR m.phone LIKE ? OR m.member_no LIKE ? OR a.username LIKE ?)";
        String from = " FROM member m LEFT JOIN account a ON a.member_id = m.id AND a.role_code = 'MEMBER' " +
            "WHERE m.shop_id = ? AND m.status = 'ACTIVE'";
        Object total;
        java.util.List<Map<String, Object>> records;
        if (like.isEmpty()) {
            total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, shopId);
            records = jdbcTemplate.queryForList(
                "SELECT m.id, m.member_no AS memberNo, COALESCE(a.username, m.member_no) AS username, " +
                    "m.name, m.phone, m.gender, m.birthday, m.avatar_url AS avatarUrl, " +
                    "m.source, m.points, m.status, m.created_at AS createdAt" + from +
                    " ORDER BY m.id DESC LIMIT ? OFFSET ?",
                shopId, safeLimit, offset
            );
        } else {
            String pattern = "%" + like + "%";
            total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from + filter,
                Long.class, shopId, pattern, pattern, pattern, pattern
            );
            records = jdbcTemplate.queryForList(
                "SELECT m.id, m.member_no AS memberNo, COALESCE(a.username, m.member_no) AS username, " +
                    "m.name, m.phone, m.gender, m.birthday, m.avatar_url AS avatarUrl, " +
                    "m.source, m.points, m.status, m.created_at AS createdAt" + from + filter +
                    " ORDER BY m.id DESC LIMIT ? OFFSET ?",
                shopId, pattern, pattern, pattern, pattern, safeLimit, offset
            );
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", safePage);
        data.put("limit", safeLimit);
        return FaceResponses.ok(data);
    }

    @PostMapping("/members")
    @Transactional
    public Map<String, Object> createMember(@RequestBody final Map<String, Object> body,
                                            HttpServletRequest request) {
        final Long shopId = requireManagementShop(request);
        final String name = required(body, "name");
        final String phone = required(body, "phone");
        final String memberNo = body.get("memberNo") == null || body.get("memberNo").toString().trim().isEmpty()
            ? "M" + System.currentTimeMillis() : body.get("memberNo").toString().trim();
        final String gender = optional(body, "gender");
        final String source = optional(body, "source");
        final String notes = optional(body, "notes");
        final String username = optional(body, "username") == null ? memberNo : optional(body, "username");
        final String password = optional(body, "password");
        final Long tenantId = jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM shop WHERE id = ? AND status = 'ACTIVE'", Long.class, shopId
        );
        final String globalMemberNo = "GM" + System.currentTimeMillis();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws java.sql.SQLException {
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO member (tenant_id, home_shop_id, shop_id, member_no, global_member_no, name, phone, gender, source, notes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, tenantId);
                statement.setLong(2, shopId);
                statement.setLong(3, shopId);
                statement.setString(4, memberNo);
                statement.setString(5, globalMemberNo);
                statement.setString(6, name);
                statement.setString(7, phone);
                statement.setString(8, gender);
                statement.setString(9, source);
                statement.setString(10, notes);
                return statement;
            }
        }, keyHolder);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", keyHolder.getKey().longValue());
        result.put("memberNo", memberNo);
        if (password != null && !password.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, member_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'MEMBER', ?)",
                tenantId, shopId, shopId, username, name, passwordEncoder.encode(password), keyHolder.getKey().longValue()
            );
        }
        return FaceResponses.ok(result);
    }

    @GetMapping("/members/{id}")
    public Map<String, Object> member(@PathVariable Long id, HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        java.util.List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT m.id, m.member_no AS memberNo, COALESCE(a.username, m.member_no) AS username, " +
                "m.name, m.phone, m.gender, m.avatar_url AS avatarUrl, m.source, m.points, m.status, " +
                "m.created_at AS createdAt FROM member m LEFT JOIN account a ON a.member_id = m.id " +
                "WHERE m.id = ? AND m.shop_id = ? LIMIT 1",
            id, shopId
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("会员不存在");
        return FaceResponses.ok(rows.get(0));
    }

    @PostMapping("/members/update")
    @Transactional
    public Map<String, Object> updateMember(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        Long id = Long.valueOf(required(body, "id"));
        String memberNo = required(body, "memberNo");
        String name = required(body, "name");
        String phone = required(body, "phone");
        int changed = jdbcTemplate.update(
            "UPDATE member SET member_no = ?, name = ?, phone = ?, gender = ?, avatar_url = ?, source = ?, notes = ? " +
                "WHERE id = ? AND shop_id = ?",
            memberNo, name, phone, optional(body, "gender"), optional(body, "avatarUrl"),
            optional(body, "source"), optional(body, "notes"), id, shopId
        );
        if (changed == 0) throw new IllegalArgumentException("会员不存在");
        String username = optional(body, "username");
        String password = optional(body, "password");
        if (username != null) {
            jdbcTemplate.update("UPDATE account SET username = ? WHERE member_id = ? AND shop_id = ?", username, id, shopId);
        }
        if (password != null && !password.isEmpty() && !"******".equals(password)) {
            jdbcTemplate.update("UPDATE account SET password_hash = ? WHERE member_id = ? AND shop_id = ?",
                passwordEncoder.encode(password), id, shopId);
        }
        return FaceResponses.ok(null);
    }

    @PostMapping("/members/delete")
    @Transactional
    public Map<String, Object> deleteMembers(@RequestBody java.util.List<Long> ids, HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE account SET status = 'INACTIVE' WHERE member_id = ? AND shop_id = ?", id, shopId);
            jdbcTemplate.update("UPDATE member SET status = 'INACTIVE' WHERE id = ? AND shop_id = ?", id, shopId);
        }
        return FaceResponses.ok(null);
    }

    @GetMapping("/members/gender-summary")
    public Map<String, Object> memberGenderSummary(HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        return FaceResponses.ok(jdbcTemplate.queryForList(
            "SELECT COALESCE(gender, '未填写') AS name, COUNT(*) AS value FROM member " +
                "WHERE shop_id = ? AND status = 'ACTIVE' GROUP BY gender ORDER BY value DESC",
            shopId
        ));
    }

    @PostMapping("/schedules")
    public Map<String, Object> createSchedule(@RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        Long tenantId = jdbcTemplate.queryForObject("SELECT tenant_id FROM shop WHERE id = ?", Long.class, shopId);
        Long staffId = Long.valueOf(required(body, "staffId"));
        String date = required(body, "scheduleDate");
        String type = body.get("scheduleType") == null ? "WORK" : body.get("scheduleType").toString().toUpperCase();
        if (!java.util.Arrays.asList("WORK", "LEAVE", "BLOCKED").contains(type)) {
            throw new IllegalArgumentException("不支持的排班类型");
        }
        String startTime = optional(body, "startTime");
        String endTime = optional(body, "endTime");
        jdbcTemplate.update(
            "INSERT INTO staff_schedule (tenant_id, shop_id, staff_id, schedule_date, start_time, end_time, schedule_type, remark) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            tenantId, shopId, staffId, java.sql.Date.valueOf(date),
            startTime == null ? null : java.sql.Time.valueOf(normalizeTime(startTime)),
            endTime == null ? null : java.sql.Time.valueOf(normalizeTime(endTime)),
            type, optional(body, "remark")
        );
        return FaceResponses.ok(null);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        Long shopId = requireManagementShop(request);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("memberCount", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chezhu", Long.class));
        summary.put("todayAppointments", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM fuwuyuyue WHERE DATE(yuyueshijian) = CURRENT_DATE", Long.class));
        summary.put("todayCompleted", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM weixiujilu WHERE DATE(weixiushijian) = CURRENT_DATE", Long.class));
        summary.put("monthRevenue", jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(zongjia), 0) FROM weixiujilu " +
                "WHERE weixiushijian >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
            java.math.BigDecimal.class));
        data.put("summary", summary);
        data.put("appointmentStatus", jdbcTemplate.queryForList(
            "SELECT COALESCE(NULLIF(weixiuzhuangtai, ''), '未设置') AS name, COUNT(*) AS value " +
                "FROM fuwuyuyue WHERE yuyueshijian >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) " +
                "GROUP BY weixiuzhuangtai ORDER BY value DESC"));
        data.put("dailyTrend", jdbcTemplate.queryForList(
            "SELECT DATE_FORMAT(activity_date, '%m-%d') AS date, SUM(appointment_count) AS appointmentCount, " +
                "SUM(completed_count) AS completedCount FROM (" +
                "SELECT DATE(yuyueshijian) AS activity_date, COUNT(*) AS appointment_count, 0 AS completed_count " +
                "FROM fuwuyuyue WHERE yuyueshijian >= DATE_SUB(CURRENT_DATE, INTERVAL 13 DAY) " +
                "GROUP BY DATE(yuyueshijian) UNION ALL " +
                "SELECT DATE(weixiushijian) AS activity_date, 0 AS appointment_count, COUNT(*) AS completed_count " +
                "FROM weixiujilu WHERE weixiushijian >= DATE_SUB(CURRENT_DATE, INTERVAL 13 DAY) " +
                "GROUP BY DATE(weixiushijian)) activity " +
                "GROUP BY activity_date ORDER BY activity_date"));
        data.put("dailyRevenue", jdbcTemplate.queryForList(
            "SELECT DATE_FORMAT(weixiushijian, '%m-%d') AS date, COALESCE(SUM(zongjia), 0) AS revenue " +
                "FROM weixiujilu WHERE weixiushijian >= DATE_SUB(CURRENT_DATE, INTERVAL 13 DAY) " +
                "GROUP BY DATE_FORMAT(weixiushijian, '%m-%d') ORDER BY date"));
        data.put("popularServices", jdbcTemplate.queryForList(
            "SELECT COALESCE(NULLIF(fuwumingcheng, ''), '未命名项目') AS name, COUNT(*) AS value " +
                "FROM fuwuyuyue WHERE addtime >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) " +
                "GROUP BY fuwumingcheng ORDER BY value DESC LIMIT 8"));
        return FaceResponses.ok(data);
    }

    @GetMapping("/technician-dashboard")
    public Map<String, Object> technicianDashboard(HttpServletRequest request) {
        Object roleObject = request.getSession().getAttribute("role");
        if (!"BEAUTICIAN".equals(roleObject == null ? "" : roleObject.toString())) {
            throw new SecurityException("当前账号不是美容师账号");
        }
        Object accountId = request.getSession().getAttribute("userId");
        if (accountId == null) throw new SecurityException("登录状态已失效");
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT shop_id, staff_id, username FROM account WHERE id = ? AND status = 'ACTIVE' AND staff_id IS NOT NULL",
            accountId
        );
        Long shopId = ((Number) account.get("shop_id")).longValue();
        Long staffId = ((Number) account.get("staff_id")).longValue();
        String staffUsername = account.get("username").toString();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("memberCount", jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT zhanghao) FROM fuwuyuyue WHERE weixiuzhanghao = ?",
            Long.class, staffUsername));
        summary.put("todayAppointments", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM fuwuyuyue WHERE weixiuzhanghao = ? AND DATE(yuyueshijian) = CURRENT_DATE",
            Long.class, staffUsername));
        summary.put("todayCompleted", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM weixiujilu WHERE weixiuzhanghao = ? AND DATE(weixiushijian) = CURRENT_DATE",
            Long.class, staffUsername));
        summary.put("monthRevenue", 0);
        data.put("summary", summary);
        data.put("appointmentStatus", jdbcTemplate.queryForList(
            "SELECT COALESCE(NULLIF(weixiuzhuangtai, ''), '未设置') AS name, COUNT(*) AS value " +
                "FROM fuwuyuyue WHERE weixiuzhanghao = ? " +
                "AND yuyueshijian >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) " +
                "GROUP BY weixiuzhuangtai ORDER BY value DESC",
            staffUsername));
        data.put("dailyTrend", jdbcTemplate.queryForList(
            "SELECT DATE_FORMAT(activity_date, '%m-%d') AS date, SUM(appointment_count) AS appointmentCount, " +
                "SUM(completed_count) AS completedCount FROM (" +
                "SELECT DATE(yuyueshijian) AS activity_date, COUNT(*) AS appointment_count, 0 AS completed_count " +
                "FROM fuwuyuyue WHERE weixiuzhanghao = ? " +
                "AND yuyueshijian >= DATE_SUB(CURRENT_DATE, INTERVAL 13 DAY) GROUP BY DATE(yuyueshijian) UNION ALL " +
                "SELECT DATE(weixiushijian) AS activity_date, 0 AS appointment_count, COUNT(*) AS completed_count " +
                "FROM weixiujilu WHERE weixiuzhanghao = ? " +
                "AND weixiushijian >= DATE_SUB(CURRENT_DATE, INTERVAL 13 DAY) GROUP BY DATE(weixiushijian)) activity " +
                "GROUP BY activity_date ORDER BY activity_date",
            staffUsername, staffUsername));
        data.put("dailyRevenue", java.util.Collections.emptyList());
        data.put("popularServices", jdbcTemplate.queryForList(
            "SELECT COALESCE(NULLIF(fuwumingcheng, ''), '未命名项目') AS name, COUNT(*) AS value " +
                "FROM fuwuyuyue WHERE weixiuzhanghao = ? " +
                "AND addtime >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) " +
                "GROUP BY fuwumingcheng ORDER BY value DESC LIMIT 8",
            staffUsername));
        return FaceResponses.ok(data);
    }

    private Long requireManagementShop(HttpServletRequest request) {
        Object roleObject = request.getSession().getAttribute("role");
        String role = roleObject == null ? "" : roleObject.toString();
        if (!java.util.Arrays.asList("OWNER", "MANAGER", "FRONT_DESK").contains(role)) {
            throw new SecurityException("当前账号没有管理权限");
        }
        Object accountId = request.getSession().getAttribute("userId");
        if (accountId == null) throw new SecurityException("登录状态已失效");
        return jdbcTemplate.queryForObject(
            "SELECT shop_id FROM account WHERE id = ? AND status = 'ACTIVE'", Long.class, accountId
        );
    }

    private String required(Map<String, Object> body, String key) {
        String value = optional(body, key);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("缺少参数：" + key);
        return value;
    }

    private String optional(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString().trim();
    }

    private String normalizeTime(String value) {
        return value.length() == 5 ? value + ":00" : value;
    }

    @ExceptionHandler(SecurityException.class)
    public Map<String, Object> handleForbidden(SecurityException exception) {
        return FaceResponses.error(403, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, DuplicateKeyException.class})
    public Map<String, Object> handleBadRequest(RuntimeException exception) {
        String message = exception instanceof DuplicateKeyException ? "会员手机号、编号或排班记录重复" : exception.getMessage();
        return FaceResponses.error(400, message);
    }
}
