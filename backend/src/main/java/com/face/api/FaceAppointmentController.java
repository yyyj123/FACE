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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/appointments")
public class FaceAppointmentController {
    private final JdbcTemplate jdbcTemplate;
    private final FaceAppointmentService appointmentService;

    @Autowired
    public FaceAppointmentController(JdbcTemplate jdbcTemplate, FaceAppointmentService appointmentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") Long shopId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String date) {
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.appointment_no AS appointmentNo, a.start_at AS startAt, a.end_at AS endAt, " +
                "a.status, a.source, a.version, m.id AS memberId, m.name AS memberName, m.phone AS memberPhone, " +
                "s.id AS staffId, s.name AS staffName FROM appointment a " +
                "JOIN member m ON m.id = a.member_id JOIN staff s ON s.id = a.staff_id WHERE a.shop_id = ?"
        );
        java.util.ArrayList<Object> args = new java.util.ArrayList<Object>();
        args.add(shopId);
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND a.status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (date != null && !date.trim().isEmpty()) {
            sql.append(" AND DATE(a.start_at) = ?");
            args.add(java.sql.Date.valueOf(date));
        }
        sql.append(" ORDER BY a.start_at DESC");
        return FaceResponses.ok(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        enforceCreateScope(body, request);
        return FaceResponses.ok(appointmentService.create(body));
    }

    @PostMapping("/{id}/status")
    public Map<String, Object> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = body.get("status") == null ? null : body.get("status").toString();
        Integer version = body.get("version") == null ? null : Integer.valueOf(body.get("version").toString());
        appointmentService.changeStatus(id, status, version);
        return FaceResponses.ok(null);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, SecurityException.class})
    public Map<String, Object> handleBusinessException(RuntimeException exception) {
        int code = exception instanceof SecurityException ? 403 : 409;
        return FaceResponses.error(code, exception.getMessage());
    }

    private void enforceCreateScope(Map<String, Object> body, HttpServletRequest request) {
        Object accountId = request.getSession().getAttribute("userId");
        if (accountId == null) throw new SecurityException("登录状态已失效");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT shop_id AS shopId, role_code AS roleCode, member_id AS memberId " +
                "FROM account WHERE id = ? AND status = 'ACTIVE' LIMIT 1",
            Long.valueOf(accountId.toString())
        );
        if (rows.isEmpty()) throw new SecurityException("账号不存在或已停用");
        Map<String, Object> account = rows.get(0);
        String role = account.get("roleCode").toString();
        Long shopId = ((Number) account.get("shopId")).longValue();
        if (body.get("shopId") == null || Long.valueOf(body.get("shopId").toString()).longValue() != shopId.longValue()) {
            throw new SecurityException("不能跨门店创建预约");
        }
        if ("MEMBER".equals(role)) {
            Object memberId = account.get("memberId");
            if (memberId == null) throw new SecurityException("会员账号未关联会员档案");
            body.put("memberId", ((Number) memberId).longValue());
            return;
        }
        if (!java.util.Arrays.asList("OWNER", "MANAGER", "FRONT_DESK").contains(role)) {
            throw new SecurityException("当前账号无权创建预约");
        }
    }
}
