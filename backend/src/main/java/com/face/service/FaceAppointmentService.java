package com.face.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FaceAppointmentService {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public FaceAppointmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request) {
        final Long shopId = requiredLong(request, "shopId");
        final Long memberId = requiredLong(request, "memberId");
        final Long staffId = requiredLong(request, "staffId");
        final Long tenantId = jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM shop WHERE id = ? AND status = 'ACTIVE'", Long.class, shopId
        );
        if (tenantId == null) {
            throw new IllegalArgumentException("门店不存在或已停用");
        }
        final List<Long> serviceIds = requiredLongList(request.get("serviceIds"));
        final LocalDateTime startAt = parseDateTime(requiredString(request, "startAt"));

        List<Map<String, Object>> services = new ArrayList<Map<String, Object>>();
        int totalMinutes = 0;
        for (Long serviceId : serviceIds) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, duration_minutes, cleanup_minutes, COALESCE(member_price, list_price) AS price " +
                    "FROM service_item WHERE id = ? AND shop_id = ? AND status = 'ACTIVE'",
                serviceId, shopId
            );
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("美容项目不存在或已停用：" + serviceId);
            }
            Map<String, Object> service = rows.get(0);
            services.add(service);
            totalMinutes += ((Number) service.get("duration_minutes")).intValue();
            totalMinutes += ((Number) service.get("cleanup_minutes")).intValue();
        }
        final LocalDateTime endAt = startAt.plusMinutes(totalMinutes);
        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new IllegalArgumentException("预约项目不能跨越两个营业日");
        }

        validateMemberAndStaff(shopId, memberId, staffId);
        validateStaffSkills(staffId, serviceIds);
        validateSchedule(staffId, startAt, endAt);
        lockAndValidateNoOverlap(staffId, startAt, endAt);

        final String appointmentNo = createAppointmentNo();
        final String source = optionalEnum(request.get("source"), "ONLINE",
            java.util.Arrays.asList("ONLINE", "FRONT_DESK", "PHONE", "WECHAT"));
        final String memberNote = optionalString(request.get("memberNote"));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws java.sql.SQLException {
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO appointment " +
                        "(tenant_id, shop_id, appointment_no, member_id, staff_id, start_at, end_at, status, source, member_note) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, tenantId);
                statement.setLong(2, shopId);
                statement.setString(3, appointmentNo);
                statement.setLong(4, memberId);
                statement.setLong(5, staffId);
                statement.setTimestamp(6, Timestamp.valueOf(startAt));
                statement.setTimestamp(7, Timestamp.valueOf(endAt));
                statement.setString(8, source);
                statement.setString(9, memberNote);
                return statement;
            }
        }, keyHolder);
        final long appointmentId = keyHolder.getKey().longValue();

        int sortOrder = 0;
        for (Map<String, Object> service : services) {
            jdbcTemplate.update(
                "INSERT INTO appointment_item " +
                    "(appointment_id, service_id, service_name_snapshot, duration_minutes_snapshot, price_snapshot, sort_order) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                appointmentId,
                ((Number) service.get("id")).longValue(),
                service.get("name"),
                ((Number) service.get("duration_minutes")).intValue(),
                service.get("price"),
                sortOrder++
            );
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", appointmentId);
        result.put("appointmentNo", appointmentNo);
        result.put("startAt", startAt.toString());
        result.put("endAt", endAt.toString());
        result.put("status", "PENDING");
        return result;
    }

    @Transactional
    public void changeStatus(Long id, String requestedStatus, Integer version) {
        String next = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT status, version FROM appointment WHERE id = ? FOR UPDATE", id
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("预约不存在");
        }
        String current = rows.get(0).get("status").toString();
        int currentVersion = ((Number) rows.get(0).get("version")).intValue();
        if (version != null && version.intValue() != currentVersion) {
            throw new IllegalStateException("预约已被其他人修改，请刷新后重试");
        }
        if (!allowedNextStatuses(current).contains(next)) {
            throw new IllegalArgumentException("不允许从 " + current + " 变更为 " + next);
        }
        int changed = jdbcTemplate.update(
            "UPDATE appointment SET status = ?, version = version + 1, " +
                "confirmed_at = CASE WHEN ? = 'CONFIRMED' THEN CURRENT_TIMESTAMP(3) ELSE confirmed_at END, " +
                "checked_in_at = CASE WHEN ? = 'CHECKED_IN' THEN CURRENT_TIMESTAMP(3) ELSE checked_in_at END " +
                "WHERE id = ? AND version = ?",
            next, next, next, id, currentVersion
        );
        if (changed != 1) {
            throw new IllegalStateException("预约状态更新冲突，请刷新后重试");
        }
    }

    private void validateMemberAndStaff(Long shopId, Long memberId, Long staffId) {
        Integer memberCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM member WHERE id = ? AND shop_id = ? AND status = 'ACTIVE'",
            Integer.class, memberId, shopId
        );
        Integer staffCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM staff WHERE id = ? AND shop_id = ? AND status = 'ACTIVE'",
            Integer.class, staffId, shopId
        );
        if (memberCount == null || memberCount == 0) {
            throw new IllegalArgumentException("会员不存在或已停用");
        }
        if (staffCount == null || staffCount == 0) {
            throw new IllegalArgumentException("美容师不存在或已停用");
        }
    }

    private void validateStaffSkills(Long staffId, List<Long> serviceIds) {
        for (Long serviceId : serviceIds) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_service WHERE staff_id = ? AND service_id = ? AND enabled = 1",
                Integer.class, staffId, serviceId
            );
            if (count == null || count == 0) {
                throw new IllegalArgumentException("所选美容师不能提供项目：" + serviceId);
            }
        }
    }

    private void validateSchedule(Long staffId, LocalDateTime startAt, LocalDateTime endAt) {
        Integer workCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM staff_schedule WHERE staff_id = ? AND schedule_date = ? " +
                "AND schedule_type = 'WORK' AND start_time <= ? AND end_time >= ?",
            Integer.class,
            staffId,
            java.sql.Date.valueOf(startAt.toLocalDate()),
            java.sql.Time.valueOf(startAt.toLocalTime()),
            java.sql.Time.valueOf(endAt.toLocalTime())
        );
        if (workCount == null || workCount == 0) {
            throw new IllegalArgumentException("该时间不在美容师排班范围内");
        }
        Integer blockedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM staff_schedule WHERE staff_id = ? AND schedule_date = ? " +
                "AND schedule_type IN ('LEAVE','BLOCKED') " +
                "AND (start_time IS NULL OR start_time < ?) AND (end_time IS NULL OR end_time > ?)",
            Integer.class,
            staffId,
            java.sql.Date.valueOf(startAt.toLocalDate()),
            java.sql.Time.valueOf(endAt.toLocalTime()),
            java.sql.Time.valueOf(startAt.toLocalTime())
        );
        if (blockedCount != null && blockedCount > 0) {
            throw new IllegalArgumentException("美容师在该时段请假或不可预约");
        }
    }

    private void lockAndValidateNoOverlap(Long staffId, LocalDateTime startAt, LocalDateTime endAt) {
        List<Map<String, Object>> conflicts = jdbcTemplate.queryForList(
            "SELECT id FROM appointment WHERE staff_id = ? " +
                "AND status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE') " +
                "AND start_at < ? AND end_at > ? FOR UPDATE",
            staffId, Timestamp.valueOf(endAt), Timestamp.valueOf(startAt)
        );
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("该美容师的预约时段已被占用");
        }
    }

    private List<String> allowedNextStatuses(String status) {
        if ("PENDING".equals(status)) return java.util.Arrays.asList("CONFIRMED", "CANCELLED");
        if ("CONFIRMED".equals(status)) return java.util.Arrays.asList("CHECKED_IN", "CANCELLED", "NO_SHOW");
        if ("CHECKED_IN".equals(status)) return java.util.Arrays.asList("IN_SERVICE", "CANCELLED");
        if ("IN_SERVICE".equals(status)) return Collections.singletonList("COMPLETED");
        return Collections.emptyList();
    }

    private String createAppointmentNo() {
        return "AP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("startAt 必须是 ISO-8601 时间，例如 2026-07-22T10:30:00");
        }
    }

    private Long requiredLong(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) throw new IllegalArgumentException("缺少参数：" + key);
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
    }

    private List<Long> requiredLongList(Object value) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new IllegalArgumentException("serviceIds 至少包含一个美容项目");
        }
        List<Long> ids = new ArrayList<Long>();
        for (Object item : (List<?>) value) ids.add(Long.valueOf(item.toString()));
        return ids;
    }

    private String requiredString(Map<String, Object> request, String key) {
        String value = optionalString(request.get(key));
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("缺少参数：" + key);
        return value;
    }

    private String optionalString(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private String optionalEnum(Object value, String defaultValue, List<String> allowed) {
        String candidate = value == null ? defaultValue : value.toString().trim().toUpperCase();
        if (!allowed.contains(candidate)) throw new IllegalArgumentException("不支持的枚举值：" + candidate);
        return candidate;
    }
}
