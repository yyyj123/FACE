package com.face.platform.resource;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResourceBookingService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public ResourceBookingService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    @Transactional
    public void reserve(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        List<Long> resourceIds,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        validateInterval(startAt, endAt);
        accessService.requireShopPermission(principal, shopId, "appointment:manage");
        reserveForAuthorizedAppointment(
            principal,
            shopId,
            appointmentId,
            resourceIds,
            startAt,
            endAt
        );
    }

    public void reserveForAuthorizedAppointment(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        List<Long> resourceIds,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        validateInterval(startAt, endAt);
        List<Long> orderedResourceIds = normalizeResourceIds(resourceIds);
        if (orderedResourceIds.isEmpty()) {
            return;
        }

        Map<Long, Integer> capacities = new HashMap<>();
        for (Long resourceId : orderedResourceIds) {
            List<Map<String, Object>> resources = jdbcTemplate.queryForList(
                """
                SELECT id, status, capacity
                FROM service_resource
                WHERE tenant_id = ?
                  AND shop_id = ?
                  AND id = ?
                FOR UPDATE
                """,
                principal.tenantId(),
                shopId,
                resourceId
            );
            if (resources.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "服务资源不存在或不属于当前门店");
            }
            if (!"ACTIVE".equals(resources.getFirst().get("status"))) {
                throw new ApiException(HttpStatus.CONFLICT, "房间或设备已停用，无法预约");
            }
            capacities.put(resourceId, ((Number) resources.getFirst().get("capacity")).intValue());
        }

        for (Long resourceId : orderedResourceIds) {
            List<Long> overlaps = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM resource_booking
                WHERE tenant_id = ?
                  AND resource_id = ?
                  AND status = 'RESERVED'
                  AND start_at < ?
                  AND end_at > ?
                  AND appointment_id <> ?
                FOR UPDATE
                """,
                Long.class,
                principal.tenantId(),
                resourceId,
                endAt,
                startAt,
                appointmentId
            );
            if (overlaps.size() >= capacities.get(resourceId)) {
                throw new ApiException(HttpStatus.CONFLICT, "房间或设备在该时段已达到可用容量");
            }
        }

        for (Long resourceId : orderedResourceIds) {
            jdbcTemplate.update(
                """
                INSERT INTO resource_booking (
                    tenant_id, shop_id, appointment_id, resource_id,
                    start_at, end_at, status, created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, 'RESERVED', ?)
                """,
                principal.tenantId(),
                shopId,
                appointmentId,
                resourceId,
                startAt,
                endAt,
                principal.accountId()
            );
        }
    }

    @Transactional
    public void releaseForAppointment(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        String reason
    ) {
        accessService.requireShopPermission(principal, shopId, "appointment:manage");
        releaseForAuthorizedAppointment(principal, shopId, appointmentId, reason);
    }

    public void releaseForAuthorizedAppointment(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        String reason
    ) {
        jdbcTemplate.update(
            """
            UPDATE resource_booking
            SET status = 'RELEASED',
                release_reason = ?,
                released_by = ?,
                released_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE tenant_id = ?
              AND shop_id = ?
              AND appointment_id = ?
              AND status = 'RESERVED'
            """,
            reason,
            principal.accountId(),
            principal.tenantId(),
            shopId,
            appointmentId
        );
    }

    public List<Long> activeResourceIds(long tenantId, long shopId, long appointmentId) {
        return jdbcTemplate.queryForList(
            """
            SELECT resource_id
            FROM resource_booking
            WHERE tenant_id = ?
              AND shop_id = ?
              AND appointment_id = ?
              AND status = 'RESERVED'
            ORDER BY resource_id
            """,
            Long.class,
            tenantId,
            shopId,
            appointmentId
        );
    }

    private List<Long> normalizeResourceIds(List<Long> resourceIds) {
        if (resourceIds == null) {
            return List.of();
        }
        if (resourceIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "服务资源编号无效");
        }
        return resourceIds.stream().distinct().sorted().toList();
    }

    private void validateInterval(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源占用时段无效");
        }
    }
}
