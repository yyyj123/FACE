package com.face.platform.client;

import com.face.platform.api.ApiException;
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
public class ClientCatalogService {

    private final JdbcTemplate jdbcTemplate;

    public ClientCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> categories(long shopId) {
        requireActiveShop(shopId);
        return jdbcTemplate.queryForList(
            """
            SELECT id, name, sort_order AS sortOrder
            FROM service_category
            WHERE shop_id = ? AND status = 'ACTIVE'
            ORDER BY sort_order, id
            """,
            shopId
        );
    }

    public List<Map<String, Object>> services(
        long shopId,
        Long categoryId,
        Boolean featured,
        String sort
    ) {
        requireActiveShop(shopId);
        StringBuilder sql = new StringBuilder(
            """
            SELECT si.id, si.service_code AS serviceCode, si.name, si.subtitle,
                   si.cover_url AS coverUrl, si.description,
                   si.duration_minutes AS durationMinutes,
                   si.cleanup_minutes AS cleanupMinutes,
                   si.list_price AS listPrice, si.member_price AS memberPrice,
                   si.is_featured AS featured,
                   (SELECT COUNT(*) FROM audit_log al
                    WHERE al.tenant_id = si.tenant_id
                      AND al.shop_id = si.shop_id
                      AND al.entity_type = 'SERVICE_ITEM'
                      AND al.entity_id = si.id
                      AND al.action = 'VIEW') AS clicknum,
                   0 AS storeupnum,
                   sc.id AS categoryId, sc.name AS categoryName
            FROM service_item si
            JOIN service_category sc
              ON sc.id = si.category_id
             AND sc.tenant_id = si.tenant_id
            WHERE si.shop_id = ?
              AND si.status = 'ACTIVE'
              AND sc.status = 'ACTIVE'
            """
        );
        List<Object> args = new ArrayList<>();
        args.add(shopId);
        if (categoryId != null) {
            sql.append(" AND si.category_id = ?");
            args.add(categoryId);
        }
        if (Boolean.TRUE.equals(featured)) {
            sql.append(" AND si.is_featured = 1");
        }
        if ("clicknum".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY clicknum DESC, si.id DESC");
        } else if ("storeupnum".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY storeupnum DESC, si.id DESC");
        } else {
            sql.append(" ORDER BY si.is_featured DESC, si.id DESC");
        }
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> service(long shopId, long serviceId) {
        requireActiveShop(shopId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT si.id, si.shop_id AS shopId, si.service_code AS serviceCode,
                   si.name, si.subtitle, si.cover_url AS coverUrl, si.description,
                   si.duration_minutes AS durationMinutes,
                   si.cleanup_minutes AS cleanupMinutes,
                   si.list_price AS listPrice, si.member_price AS memberPrice,
                   si.is_featured AS featured,
                   (SELECT COUNT(*) FROM audit_log al
                    WHERE al.tenant_id = si.tenant_id
                      AND al.shop_id = si.shop_id
                      AND al.entity_type = 'SERVICE_ITEM'
                      AND al.entity_id = si.id
                      AND al.action = 'VIEW') AS clicknum,
                   0 AS storeupnum,
                   sc.id AS categoryId, sc.name AS categoryName
            FROM service_item si
            JOIN service_category sc
              ON sc.id = si.category_id
             AND sc.tenant_id = si.tenant_id
            JOIN shop sh ON sh.id = si.shop_id AND sh.status = 'ACTIVE'
            WHERE si.id = ? AND si.shop_id = ?
              AND si.status = 'ACTIVE' AND sc.status = 'ACTIVE'
            LIMIT 1
            """,
            serviceId,
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "项目不存在或已下架");
        }
        return rows.getFirst();
    }

    @Transactional
    public boolean recordServiceView(long shopId, long serviceId) {
        requireActiveShop(shopId);
        int inserted = jdbcTemplate.update(
            """
            INSERT INTO audit_log
                (tenant_id, shop_id, account_id, action, entity_type, entity_id)
            SELECT tenant_id, shop_id, NULL, 'VIEW', 'SERVICE_ITEM', id
            FROM service_item
            WHERE id = ? AND shop_id = ? AND status = 'ACTIVE'
            """,
            serviceId,
            shopId
        );
        if (inserted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "项目不存在或已下架");
        }
        return true;
    }

    public List<Map<String, Object>> staff(long shopId, Long serviceId) {
        long tenantId = requireActiveShop(shopId);
        String serviceJoin = serviceId == null
            ? ""
            : " JOIN staff_service svc ON svc.staff_id = st.id AND svc.service_id = ? AND svc.enabled = 1";
        List<Object> args = new ArrayList<>();
        if (serviceId != null) args.add(serviceId);
        args.add(shopId);
        args.add(LocalDate.now());
        args.add(LocalDate.now());
        args.add(tenantId);
        return jdbcTemplate.queryForList(
            """
            SELECT st.id, st.staff_no AS staffNo, st.name,
                   st.job_role AS jobRole, st.level_name AS levelName,
                   st.avatar_url AS avatarUrl, st.bio,
                   GROUP_CONCAT(DISTINCT skill_item.name ORDER BY skill_item.name SEPARATOR '、') AS specialties
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id
             AND ssa.tenant_id = st.tenant_id
            %s
            LEFT JOIN staff_service public_skill
              ON public_skill.staff_id = st.id AND public_skill.enabled = 1
            LEFT JOIN service_item skill_item
              ON skill_item.id = public_skill.service_id
             AND skill_item.tenant_id = st.tenant_id
             AND skill_item.status = 'ACTIVE'
            WHERE ssa.shop_id = ?
              AND ssa.status = 'ACTIVE'
              AND ssa.effective_from <= ?
              AND (ssa.effective_to IS NULL OR ssa.effective_to >= ?)
              AND st.tenant_id = ?
              AND st.status = 'ACTIVE'
            GROUP BY st.id, st.staff_no, st.name, st.job_role, st.level_name, st.avatar_url, st.bio
            ORDER BY st.id
            """.formatted(serviceJoin),
            args.toArray()
        );
    }

    public List<Map<String, Object>> banners(long shopId) {
        requireActiveShop(shopId);
        return jdbcTemplate.queryForList(
            """
            SELECT id, title, image_url AS imageUrl,
                   target_type AS targetType, target_value AS targetValue
            FROM banner
            WHERE shop_id = ? AND status = 'PUBLISHED'
              AND (start_at IS NULL OR start_at <= CURRENT_TIMESTAMP(3))
              AND (end_at IS NULL OR end_at >= CURRENT_TIMESTAMP(3))
            ORDER BY sort_order, id
            """,
            shopId
        );
    }

    public Map<String, Object> availability(long shopId, long staffId, LocalDate date) {
        long tenantId = requireActiveShop(shopId);
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择预约日期");
        }
        Integer assigned = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
            WHERE st.id = ? AND st.tenant_id = ? AND st.status = 'ACTIVE'
              AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
              AND ssa.effective_from <= ?
              AND (ssa.effective_to IS NULL OR ssa.effective_to >= ?)
            """,
            Integer.class,
            staffId,
            tenantId,
            shopId,
            date,
            date
        );
        if (assigned == null || assigned == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "美容师在所选日期不可预约");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schedules", jdbcTemplate.queryForList(
            """
            SELECT id, start_time AS startTime, end_time AS endTime,
                   schedule_type AS scheduleType, remark
            FROM staff_schedule
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ? AND schedule_date = ?
              AND status = 'ACTIVE'
            ORDER BY start_time, id
            """,
            tenantId,
            shopId,
            staffId,
            Date.valueOf(date)
        ));
        result.put("bookings", jdbcTemplate.queryForList(
            """
            SELECT id, appointment_no AS appointmentNo, start_at AS startAt,
                   end_at AS endAt, status
            FROM appointment
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
              AND DATE(start_at) = ?
              AND status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE')
            ORDER BY start_at
            """,
            tenantId,
            shopId,
            staffId,
            Date.valueOf(date)
        ));
        return result;
    }

    private long requireActiveShop(long shopId) {
        List<Long> rows = jdbcTemplate.queryForList(
            "SELECT tenant_id FROM shop WHERE id = ? AND status = 'ACTIVE' LIMIT 1",
            Long.class,
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "门店不存在或已停用");
        }
        return rows.getFirst();
    }
}
