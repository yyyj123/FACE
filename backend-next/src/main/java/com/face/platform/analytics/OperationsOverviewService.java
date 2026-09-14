package com.face.platform.analytics;

import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationsOverviewService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final TransactionAnalyticsQueryPort transactionAnalytics;

    public OperationsOverviewService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        TransactionAnalyticsQueryPort transactionAnalytics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.transactionAnalytics = transactionAnalytics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        AnalyticsQueryPolicy.DateRange range = AnalyticsQueryPolicy.dateRange(fromDate, toDate);
        List<Long> scope = tenantAccessService.accessibleShopIds(principal, "analytics:view");
        if (shopId != null) {
            tenantAccessService.requireShopPermission(principal, shopId, "analytics:view");
            scope = List.of(shopId);
        } else if (scope.isEmpty()) {
            tenantAccessService.requireManagementPermission(principal, "analytics:view");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromDate", range.fromDate());
        result.put("toDate", range.toDate());
        result.put("shopIds", scope);
        result.put("asOf", Instant.now());
        result.put("sales", transactionAnalytics.overview(
            principal, shopId, range.fromDate(), range.toDate(), "ALL"
        ));
        if (scope.isEmpty()) {
            result.put("summary", Map.of(
                "appointmentCount", 0L,
                "completedAppointmentCount", 0L,
                "completionRate", 0,
                "activeMemberCount", 0L,
                "newMemberCount", 0L
            ));
            result.put("appointmentTrend", List.of());
            result.put("appointmentStatus", List.of());
            result.put("serviceRanking", List.of());
            result.put("memberTrend", List.of());
            result.put("staffWorkload", List.of());
            return result;
        }

        String placeholders = String.join(",", Collections.nCopies(scope.size(), "?"));
        List<Object> periodArgs = periodArgs(principal, scope, range);
        Map<String, Object> appointmentSummary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS appointmentCount,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0)
                     AS completedAppointmentCount
            FROM appointment
            WHERE tenant_id = ?
              AND shop_id IN (%s)
              AND start_at >= ?
              AND start_at < ?
            """.formatted(placeholders),
            periodArgs.toArray()
        );
        long appointmentCount = ((Number) appointmentSummary.get("appointmentCount")).longValue();
        long completedCount = ((Number) appointmentSummary.get("completedAppointmentCount")).longValue();

        Map<String, Object> memberSummary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS activeMemberCount,
                   COALESCE(SUM(CASE WHEN created_at >= ? AND created_at < ? THEN 1 ELSE 0 END), 0)
                     AS newMemberCount
            FROM member
            WHERE tenant_id = ?
              AND shop_id IN (%s)
              AND status = 'ACTIVE'
            """.formatted(placeholders),
            reorderMemberArgs(principal, scope, range).toArray()
        );
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.putAll(appointmentSummary);
        summary.put("completionRate", appointmentCount == 0 ? 0 : completedCount * 100.0 / appointmentCount);
        summary.putAll(memberSummary);
        result.put("summary", summary);

        result.put("appointmentTrend", jdbcTemplate.queryForList(
            """
            SELECT DATE(start_at) AS businessDate,
                   COUNT(*) AS appointmentCount,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedCount
            FROM appointment
            WHERE tenant_id = ? AND shop_id IN (%s) AND start_at >= ? AND start_at < ?
            GROUP BY DATE(start_at)
            ORDER BY businessDate
            """.formatted(placeholders), periodArgs.toArray()
        ));
        result.put("appointmentStatus", jdbcTemplate.queryForList(
            """
            SELECT status, COUNT(*) AS value
            FROM appointment
            WHERE tenant_id = ? AND shop_id IN (%s) AND start_at >= ? AND start_at < ?
            GROUP BY status
            ORDER BY value DESC
            """.formatted(placeholders), periodArgs.toArray()
        ));
        result.put("serviceRanking", jdbcTemplate.queryForList(
            """
            SELECT ai.service_id AS serviceId,
                   ai.service_name_snapshot AS serviceName,
                   COUNT(DISTINCT a.id) AS appointmentCount,
                   COALESCE(SUM(ai.price_snapshot), 0) AS bookedAmount
            FROM appointment_item ai
            JOIN appointment a ON a.id = ai.appointment_id
            WHERE a.tenant_id = ? AND a.shop_id IN (%s) AND a.start_at >= ? AND a.start_at < ?
            GROUP BY ai.service_id, ai.service_name_snapshot
            ORDER BY appointmentCount DESC, bookedAmount DESC, serviceName
            LIMIT 10
            """.formatted(placeholders), periodArgs.toArray()
        ));
        result.put("memberTrend", jdbcTemplate.queryForList(
            """
            SELECT DATE(created_at) AS businessDate, COUNT(*) AS newMemberCount
            FROM member
            WHERE tenant_id = ? AND shop_id IN (%s) AND status = 'ACTIVE'
              AND created_at >= ? AND created_at < ?
            GROUP BY DATE(created_at)
            ORDER BY businessDate
            """.formatted(placeholders), periodArgs.toArray()
        ));
        result.put("staffWorkload", jdbcTemplate.queryForList(
            """
            SELECT st.id AS staffId, st.name AS staffName,
                   COUNT(a.id) AS appointmentCount,
                   COALESCE(SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedCount
            FROM appointment a
            JOIN staff st ON st.id = a.staff_id AND st.tenant_id = a.tenant_id
            WHERE a.tenant_id = ? AND a.shop_id IN (%s) AND a.start_at >= ? AND a.start_at < ?
            GROUP BY st.id, st.name
            ORDER BY appointmentCount DESC, staffName
            LIMIT 12
            """.formatted(placeholders), periodArgs.toArray()
        ));
        return result;
    }

    private List<Object> periodArgs(
        TenantPrincipal principal,
        List<Long> scope,
        AnalyticsQueryPolicy.DateRange range
    ) {
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.addAll(scope);
        args.add(range.fromDate().atStartOfDay());
        args.add(range.toDate().plusDays(1).atStartOfDay());
        return args;
    }

    private List<Object> reorderMemberArgs(
        TenantPrincipal principal,
        List<Long> scope,
        AnalyticsQueryPolicy.DateRange range
    ) {
        List<Object> args = new ArrayList<>();
        args.add(range.fromDate().atStartOfDay());
        args.add(range.toDate().plusDays(1).atStartOfDay());
        args.add(principal.tenantId());
        args.addAll(scope);
        return args;
    }
}
