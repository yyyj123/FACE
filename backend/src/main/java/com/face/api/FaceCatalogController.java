package com.face.api;

import com.annotation.IgnoreAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FaceCatalogController {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public FaceCatalogController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @IgnoreAuth
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("service", "FACE salon backend");
        data.put("status", "UP");
        return FaceResponses.ok(data);
    }

    @IgnoreAuth
    @GetMapping("/service-categories")
    public Map<String, Object> categories(@RequestParam(defaultValue = "1") Long shopId) {
        return FaceResponses.ok(jdbcTemplate.queryForList(
            "SELECT id, name, sort_order AS sortOrder FROM service_category " +
                "WHERE shop_id = ? AND status = 'ACTIVE' ORDER BY sort_order, id",
            shopId
        ));
    }

    @IgnoreAuth
    @GetMapping("/services")
    public Map<String, Object> services(@RequestParam(defaultValue = "1") Long shopId,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) Boolean featured,
                                        @RequestParam(required = false) String sort) {
        StringBuilder sql = new StringBuilder(
            "SELECT s.id, s.service_code AS serviceCode, s.name, s.subtitle, s.cover_url AS coverUrl, " +
                "s.description, s.duration_minutes AS durationMinutes, s.cleanup_minutes AS cleanupMinutes, " +
                "s.list_price AS listPrice, s.member_price AS memberPrice, s.is_featured AS featured, " +
                "(SELECT COUNT(*) FROM audit_log al WHERE al.shop_id = s.shop_id " +
                "AND al.entity_type = 'SERVICE_ITEM' AND al.entity_id = s.id AND al.action = 'VIEW') AS clicknum, " +
                "(SELECT COUNT(*) FROM storeup su WHERE su.tablename = 'shouhoufuwu' " +
                "AND su.refid = s.id AND su.type = 1) AS storeupnum, " +
                "c.id AS categoryId, c.name AS categoryName FROM service_item s " +
                "JOIN service_category c ON c.id = s.category_id WHERE s.shop_id = ? AND s.status = 'ACTIVE'");
        java.util.ArrayList<Object> args = new java.util.ArrayList<Object>();
        args.add(shopId);
        if (categoryId != null) {
            sql.append(" AND s.category_id = ?");
            args.add(categoryId);
        }
        if (Boolean.TRUE.equals(featured)) {
            sql.append(" AND s.is_featured = 1");
        }
        if ("clicknum".equals(sort)) {
            sql.append(" ORDER BY clicknum DESC, s.id DESC");
        } else if ("storeupnum".equals(sort)) {
            sql.append(" ORDER BY storeupnum DESC, s.id DESC");
        } else {
            sql.append(" ORDER BY s.is_featured DESC, s.id DESC");
        }
        return FaceResponses.ok(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @IgnoreAuth
    @GetMapping("/services/{serviceId}")
    public Map<String, Object> serviceDetail(@PathVariable Long serviceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT s.id, s.shop_id AS shopId, s.service_code AS serviceCode, s.name, s.subtitle, " +
                "s.cover_url AS coverUrl, s.description, s.duration_minutes AS durationMinutes, " +
                "s.cleanup_minutes AS cleanupMinutes, s.list_price AS listPrice, " +
                "s.member_price AS memberPrice, s.is_featured AS featured, " +
                "(SELECT COUNT(*) FROM audit_log al WHERE al.shop_id = s.shop_id " +
                "AND al.entity_type = 'SERVICE_ITEM' AND al.entity_id = s.id AND al.action = 'VIEW') AS clicknum, " +
                "(SELECT COUNT(*) FROM storeup su WHERE su.tablename = 'shouhoufuwu' " +
                "AND su.refid = s.id AND su.type = 1) AS storeupnum, " +
                "c.id AS categoryId, c.name AS categoryName " +
                "FROM service_item s JOIN service_category c ON c.id = s.category_id " +
                "WHERE s.id = ? AND s.status = 'ACTIVE'",
            serviceId
        );
        if (rows.isEmpty()) {
            return FaceResponses.error(404, "项目不存在或已下架");
        }
        return FaceResponses.ok(rows.get(0));
    }

    @IgnoreAuth
    @PostMapping("/services/{serviceId}/view")
    public Map<String, Object> recordServiceView(@PathVariable Long serviceId) {
        int inserted = jdbcTemplate.update(
            "INSERT INTO audit_log (shop_id, account_id, action, entity_type, entity_id) " +
                "SELECT shop_id, NULL, 'VIEW', 'SERVICE_ITEM', id FROM service_item " +
                "WHERE id = ? AND status = 'ACTIVE'",
            serviceId
        );
        if (inserted == 0) {
            return FaceResponses.error(404, "项目不存在或已下架");
        }
        return FaceResponses.ok(true);
    }

    @IgnoreAuth
    @GetMapping("/staff")
    public Map<String, Object> staff(@RequestParam(defaultValue = "1") Long shopId,
                                     @RequestParam(required = false) Long serviceId) {
        if (serviceId == null) {
            return FaceResponses.ok(jdbcTemplate.queryForList(
                "SELECT id, staff_no AS staffNo, name, job_role AS jobRole, level_name AS levelName, " +
                    "avatar_url AS avatarUrl, bio FROM staff WHERE shop_id = ? AND status = 'ACTIVE' ORDER BY id",
                shopId
            ));
        }
        return FaceResponses.ok(jdbcTemplate.queryForList(
            "SELECT s.id, s.staff_no AS staffNo, s.name, s.job_role AS jobRole, s.level_name AS levelName, " +
                "s.avatar_url AS avatarUrl, s.bio FROM staff s JOIN staff_service ss ON ss.staff_id = s.id " +
                "WHERE s.shop_id = ? AND s.status = 'ACTIVE' AND ss.service_id = ? AND ss.enabled = 1 ORDER BY s.id",
            shopId, serviceId
        ));
    }

    @IgnoreAuth
    @GetMapping("/banners")
    public Map<String, Object> banners(@RequestParam(defaultValue = "1") Long shopId) {
        return FaceResponses.ok(jdbcTemplate.queryForList(
            "SELECT id, title, image_url AS imageUrl, target_type AS targetType, target_value AS targetValue " +
                "FROM banner WHERE shop_id = ? AND status = 'ACTIVE' " +
                "AND (start_at IS NULL OR start_at <= CURRENT_TIMESTAMP(3)) " +
                "AND (end_at IS NULL OR end_at >= CURRENT_TIMESTAMP(3)) ORDER BY sort_order, id",
            shopId
        ));
    }

    @IgnoreAuth
    @GetMapping("/availability")
    public Map<String, Object> availability(@RequestParam Long staffId, @RequestParam String date) {
        Date scheduleDate = Date.valueOf(date);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
            "SELECT id, start_time AS startTime, end_time AS endTime, schedule_type AS scheduleType, remark " +
                "FROM staff_schedule WHERE staff_id = ? AND schedule_date = ? ORDER BY start_time",
            staffId, scheduleDate
        );
        List<Map<String, Object>> bookings = jdbcTemplate.queryForList(
            "SELECT id, appointment_no AS appointmentNo, start_at AS startAt, end_at AS endAt, status " +
                "FROM appointment WHERE staff_id = ? AND DATE(start_at) = ? " +
                "AND status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE') ORDER BY start_at",
            staffId, scheduleDate
        );
        data.put("schedules", schedules);
        data.put("bookings", bookings);
        return FaceResponses.ok(data);
    }
}
