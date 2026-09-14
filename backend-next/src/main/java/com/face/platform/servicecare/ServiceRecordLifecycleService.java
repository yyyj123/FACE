package com.face.platform.servicecare;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ServiceRecordLifecycleService {

    private final JdbcTemplate jdbcTemplate;

    public ServiceRecordLifecycleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long ensureInProgress(TenantPrincipal principal, long shopId, long appointmentId) {
        List<Long> existing = jdbcTemplate.queryForList(
            """
            SELECT id
            FROM service_record
            WHERE tenant_id = ? AND shop_id = ? AND appointment_id = ?
            """,
            Long.class,
            principal.tenantId(),
            shopId,
            appointmentId
        );
        if (!existing.isEmpty()) return existing.getFirst();

        Map<String, Object> appointment = appointment(principal, shopId, appointmentId);
        String status = appointment.get("status").toString();
        if (!List.of("IN_SERVICE", "COMPLETED").contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "预约尚未进入服务中，不能建立服务记录");
        }
        String recordNo = recordNo();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO service_record (
                    tenant_id, record_no, shop_id, appointment_id,
                    member_id, staff_id, actual_start_at, actual_end_at,
                    service_summary, status, version, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?,
                          COALESCE(?, CURRENT_TIMESTAMP(3)), NULL,
                          NULL, 'IN_PROGRESS', 0, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setString(2, recordNo);
            statement.setLong(3, shopId);
            statement.setLong(4, appointmentId);
            statement.setLong(5, number(appointment.get("memberId")));
            statement.setLong(6, number(appointment.get("staffId")));
            statement.setObject(7, appointment.get("startedAt"));
            statement.setLong(8, principal.accountId());
            statement.setLong(9, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "服务记录编号生成失败");
        }
        long serviceRecordId = key.longValue();
        copyAppointmentItems(serviceRecordId, appointmentId);
        return serviceRecordId;
    }

    public long completeFromAppointment(
        TenantPrincipal principal,
        long shopId,
        long appointmentId
    ) {
        long serviceRecordId = ensureInProgress(principal, shopId, appointmentId);
        jdbcTemplate.update(
            """
            UPDATE service_record
            SET actual_end_at = CASE
                  WHEN CURRENT_TIMESTAMP(3) > actual_start_at
                    THEN CURRENT_TIMESTAMP(3)
                  ELSE DATE_ADD(actual_start_at, INTERVAL 1 SECOND)
                END,
                service_summary = COALESCE(
                  NULLIF(service_summary, ''),
                  '预约已完成，护理详情待补充'
                ),
                status = 'COMPLETED',
                version = version + 1,
                updated_by = ?
            WHERE id = ? AND tenant_id = ? AND status = 'IN_PROGRESS'
            """,
            principal.accountId(),
            serviceRecordId,
            principal.tenantId()
        );
        return serviceRecordId;
    }

    private Map<String, Object> appointment(
        TenantPrincipal principal,
        long shopId,
        long appointmentId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, staff_id AS staffId,
                   started_at AS startedAt, status
            FROM appointment
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
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

    private void copyAppointmentItems(long serviceRecordId, long appointmentId) {
        jdbcTemplate.update(
            """
            INSERT INTO service_record_item (
                service_record_id, service_id, service_name_snapshot,
                duration_minutes_snapshot, price_snapshot, sort_order
            )
            SELECT ?, service_id, service_name_snapshot,
                   duration_minutes_snapshot, price_snapshot, sort_order
            FROM appointment_item
            WHERE appointment_id = ?
            """,
            serviceRecordId,
            appointmentId
        );
    }

    private String recordNo() {
        return "SR"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}

