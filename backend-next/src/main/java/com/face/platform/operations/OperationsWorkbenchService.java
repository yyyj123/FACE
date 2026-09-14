package com.face.platform.operations;

import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class OperationsWorkbenchService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public OperationsWorkbenchService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public Map<String, Object> workbench(TenantPrincipal principal, long shopId) {
        if (principal.roles().stream().noneMatch(Set.of("ADMIN", "SUPER_ADMIN")::contains)) {
            throw new AccessDeniedException("只有运营管理员可以访问高频工作台");
        }
        accessService.requireShopAccess(principal, shopId);
        long tenantId = principal.tenantId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", Map.of(
            "todayAppointments", count("SELECT COUNT(*) FROM appointment WHERE tenant_id = ? AND shop_id = ? AND DATE(start_at) = CURRENT_DATE", tenantId, shopId),
            "waitingCustomers", count("SELECT COUNT(*) FROM booking_waitlist WHERE tenant_id = ? AND shop_id = ? AND status IN ('WAITING', 'NOTIFIED')", tenantId, shopId),
            "pendingFulfillment", count("SELECT COUNT(*) FROM customer_confirmation WHERE tenant_id = ? AND shop_id = ? AND status = 'PENDING'", tenantId, shopId),
            "pendingShipments", count("SELECT COUNT(*) FROM mall_sub_order WHERE tenant_id = ? AND status = 'PAID'", tenantId),
            "openAfterSales", count("SELECT COUNT(*) FROM after_sale_case WHERE tenant_id = ? AND shop_id = ? AND status IN ('OPEN', 'TRIAGED', 'PROCESSING', 'WAITING_CUSTOMER', 'REOPENED')", tenantId, shopId),
            "activeMembers", count("SELECT COUNT(*) FROM member_shop_profile WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'", tenantId, shopId),
            "availablePoints", count("SELECT COALESCE(SUM(pa.available_points), 0) FROM points_account pa JOIN member_shop_profile msp ON msp.member_id = pa.member_id AND msp.tenant_id = pa.tenant_id WHERE pa.tenant_id = ? AND msp.shop_id = ? AND msp.status = 'ACTIVE'", tenantId, shopId),
            "mallOrders30d", count("SELECT COUNT(*) FROM mall_order WHERE tenant_id = ? AND shop_id = ? AND created_at >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY)", tenantId, shopId)
        ));
        result.put("todayAppointments", jdbcTemplate.queryForList(
            """
            SELECT a.id, a.appointment_no AS appointmentNo, a.start_at AS startAt, a.end_at AS endAt,
                   a.status, m.name AS memberName, st.name AS staffName,
                   (SELECT GROUP_CONCAT(ai.service_name_snapshot ORDER BY ai.sort_order SEPARATOR '、')
                    FROM appointment_item ai WHERE ai.appointment_id = a.id) AS serviceNames
            FROM appointment a
            JOIN member m ON m.id = a.member_id AND m.tenant_id = a.tenant_id
            JOIN staff st ON st.id = a.staff_id AND st.tenant_id = a.tenant_id
            WHERE a.tenant_id = ? AND a.shop_id = ? AND DATE(a.start_at) = CURRENT_DATE
            ORDER BY a.start_at, a.id LIMIT 30
            """, tenantId, shopId
        ));
        result.put("staffSchedule", jdbcTemplate.queryForList(
            """
            SELECT ss.id, ss.staff_id AS staffId, st.name AS staffName, ss.start_time AS startTime,
                   ss.end_time AS endTime, ss.schedule_type AS scheduleType, ss.remark
            FROM staff_schedule ss JOIN staff st ON st.id = ss.staff_id AND st.tenant_id = ss.tenant_id
            WHERE ss.tenant_id = ? AND ss.shop_id = ? AND ss.schedule_date = CURRENT_DATE AND ss.status = 'ACTIVE'
            ORDER BY ss.start_time, st.name LIMIT 40
            """, tenantId, shopId
        ));
        result.put("waitlist", jdbcTemplate.queryForList(
            """
            SELECT bw.id, bw.status, bw.date_from AS dateFrom, bw.date_to AS dateTo,
                   bw.time_from AS timeFrom, bw.time_to AS timeTo, m.name AS memberName,
                   si.name AS serviceName, st.name AS requestedStaffName
            FROM booking_waitlist bw
            JOIN member m ON m.id = bw.member_id AND m.tenant_id = bw.tenant_id
            JOIN service_item si ON si.id = bw.service_id AND si.tenant_id = bw.tenant_id
            LEFT JOIN staff st ON st.id = bw.requested_staff_id AND st.tenant_id = bw.tenant_id
            WHERE bw.tenant_id = ? AND bw.shop_id = ? AND bw.status IN ('WAITING', 'NOTIFIED')
            ORDER BY bw.created_at LIMIT 20
            """, tenantId, shopId
        ));
        result.put("shipments", jdbcTemplate.queryForList(
            """
            SELECT mso.id, mso.sub_order_no AS subOrderNo, mo.order_no AS orderNo,
                   mso.delivery_mode AS deliveryMode, mso.status, mo.created_at AS createdAt
            FROM mall_sub_order mso JOIN mall_order mo ON mo.id = mso.mall_order_id AND mo.tenant_id = mso.tenant_id
            WHERE mso.tenant_id = ? AND mo.shop_id = ? AND mso.status IN ('PAID', 'SHIPPED')
            ORDER BY FIELD(mso.status, 'PAID', 'SHIPPED'), mso.id DESC LIMIT 20
            """, tenantId, shopId
        ));
        result.put("afterSales", jdbcTemplate.queryForList(
            """
            SELECT ascx.id, ascx.case_no AS caseNo, ascx.priority, ascx.summary, ascx.status,
                   ascx.customer_response_due_at AS customerResponseDueAt, m.name AS memberName
            FROM after_sale_case ascx LEFT JOIN member m ON m.id = ascx.member_id AND m.tenant_id = ascx.tenant_id
            WHERE ascx.tenant_id = ? AND ascx.shop_id = ?
              AND ascx.status IN ('OPEN', 'TRIAGED', 'PROCESSING', 'WAITING_CUSTOMER', 'REOPENED')
            ORDER BY FIELD(ascx.priority, 'URGENT', 'HIGH', 'NORMAL', 'LOW'), ascx.updated_at LIMIT 20
            """, tenantId, shopId
        ));
        return result;
    }

    private long count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.longValue();
    }
}
