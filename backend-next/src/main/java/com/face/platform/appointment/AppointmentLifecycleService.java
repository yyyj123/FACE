package com.face.platform.appointment;

import com.face.platform.api.ApiException;
import com.face.platform.resource.ResourceBookingService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AppointmentLifecycleService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final ResourceBookingService resourceBookingService;

    public AppointmentLifecycleService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        ResourceBookingService resourceBookingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.resourceBookingService = resourceBookingService;
    }

    public int startService(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        int expectedVersion,
        String reason
    ) {
        Map<String, Object> appointment = lock(principal, shopId, appointmentId);
        requireAssignedTechnician(principal, appointment);
        requireVersionAndStatus(appointment, expectedVersion, "CHECKED_IN", "只有已到店预约可以开始服务");
        int changed = jdbcTemplate.update(
            """
            UPDATE appointment
            SET status = 'IN_SERVICE', started_at = CURRENT_TIMESTAMP(3),
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'CHECKED_IN'
            """,
            principal.accountId(),
            appointmentId,
            principal.tenantId(),
            shopId,
            expectedVersion
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "预约状态已变化，请刷新后重试");
        }
        recordHistory(
            principal,
            shopId,
            appointmentId,
            "CHECKED_IN",
            "IN_SERVICE",
            reason,
            expectedVersion + 1
        );
        return expectedVersion + 1;
    }

    public int completeFromServiceRecord(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        String reason
    ) {
        Map<String, Object> appointment = lock(principal, shopId, appointmentId);
        requireAssignedTechnician(principal, appointment);
        if (!"IN_SERVICE".equals(appointment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "关联预约已不在服务中，请刷新后重试");
        }
        int version = ((Number) appointment.get("version")).intValue();
        int changed = jdbcTemplate.update(
            """
            UPDATE appointment
            SET status = 'COMPLETED',
                fulfillment_status = 'PENDING_CUSTOMER_CONFIRMATION',
                completed_at = CURRENT_TIMESTAMP(3),
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'IN_SERVICE'
            """,
            principal.accountId(),
            appointmentId,
            principal.tenantId(),
            shopId,
            version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "预约已被其他人修改，请刷新后重试");
        }
        recordHistory(
            principal,
            shopId,
            appointmentId,
            "IN_SERVICE",
            "COMPLETED",
            reason,
            version + 1
        );
        resourceBookingService.releaseForAuthorizedAppointment(
            principal,
            shopId,
            appointmentId,
            "预约护理已完成"
        );
        return version + 1;
    }

    private Map<String, Object> lock(
        TenantPrincipal principal,
        long shopId,
        long appointmentId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, staff_id AS staffId, status, version
            FROM appointment
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            appointmentId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "预约不存在");
        }
        return rows.getFirst();
    }

    private void requireAssignedTechnician(
        TenantPrincipal principal,
        Map<String, Object> appointment
    ) {
        if (!principal.roles().contains("BEAUTICIAN")) {
            return;
        }
        long staffId = tenantAccessService.requireStaffId(principal);
        long assignedStaffId = ((Number) appointment.get("staffId")).longValue();
        if (staffId != assignedStaffId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "技师只能操作分配给自己的预约");
        }
    }

    private void requireVersionAndStatus(
        Map<String, Object> appointment,
        int expectedVersion,
        String expectedStatus,
        String statusMessage
    ) {
        if (((Number) appointment.get("version")).intValue() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "预约已被其他人修改，请刷新后重试");
        }
        if (!expectedStatus.equals(appointment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, statusMessage);
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
}
