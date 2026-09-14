package com.face.platform.analytics;

import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.transaction.TransactionAnalyticsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionAnalyticsQueryServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService tenantAccessService = mock(TenantAccessService.class);
    private final TransactionAnalyticsQueryService service =
        new TransactionAnalyticsQueryService(jdbcTemplate, tenantAccessService);

    @Test
    void usesDedicatedAnalyticsPermissionAndReturnsSafeEmptyResultWithoutScope() {
        TenantPrincipal principal = new TenantPrincipal(
            10L,
            1L,
            2L,
            "manager",
            List.of("MANAGER"),
            Set.of(),
            Set.of(2L),
            false
        );
        when(tenantAccessService.accessibleShopIds(principal, "analytics:view"))
            .thenReturn(List.of());

        Map<String, Object> result = service.overview(
            principal,
            null,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 28),
            "ALL"
        );

        verify(tenantAccessService).accessibleShopIds(principal, "analytics:view");
        verify(tenantAccessService).requireManagementPermission(principal, "analytics:view");
        verifyNoInteractions(jdbcTemplate);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> dataQuality = (Map<String, Object>) result.get("dataQuality");
        assertThat(summary)
            .containsEntry("orderCount", 0L)
            .containsEntry("netCollectedAmount", 0);
        assertThat(dataQuality)
            .containsEntry("refundAllocation", "ORDER_LEVEL_ONLY")
            .containsEntry("metricVersion", "M6-04-v1");
        assertThat(result)
            .containsEntry("brandComposition", List.of())
            .containsEntry("shopBreakdown", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> comparison = (Map<String, Object>) result.get("comparison");
        assertThat(comparison)
            .containsEntry("previousFromDate", LocalDate.of(2026, 6, 3))
            .containsEntry("previousToDate", LocalDate.of(2026, 6, 30));
    }
}
