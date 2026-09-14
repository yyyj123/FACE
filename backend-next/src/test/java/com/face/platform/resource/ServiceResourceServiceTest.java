package com.face.platform.resource;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceResourceServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final ServiceResourceService service =
        new ServiceResourceService(jdbcTemplate, accessService);
    private final TenantPrincipal principal = new TenantPrincipal(
        11L,
        1L,
        2L,
        "manager01",
        List.of("MANAGER"),
        Set.of(),
        Set.of(2L),
        false
    );

    @Test
    void deactivateUsesOptimisticVersionAndKeepsHistoricalBookingsUntouched() {
        when(accessService.requireShopPermission(principal, 2L, "resource:manage")).thenReturn(2L);
        when(jdbcTemplate.update(
            anyString(),
            eq(11L),
            eq(81L),
            eq(1L),
            eq(2L),
            eq(3)
        )).thenReturn(1);

        service.deactivate(principal, 2L, 81L, 3);

        verify(jdbcTemplate).update(
            org.mockito.ArgumentMatchers.argThat(sql -> {
                String normalized = sql.toUpperCase();
                return normalized.contains("UPDATE SERVICE_RESOURCE")
                    && normalized.contains("VERSION = VERSION + 1")
                    && !normalized.contains("DELETE")
                    && !normalized.contains("RESOURCE_BOOKING");
            }),
            eq(11L),
            eq(81L),
            eq(1L),
            eq(2L),
            eq(3)
        );
    }

    @Test
    void staleResourceVersionReturnsConflict() {
        when(accessService.requireShopPermission(principal, 2L, "resource:manage")).thenReturn(2L);
        when(jdbcTemplate.update(
            anyString(),
            eq(11L),
            eq(81L),
            eq(1L),
            eq(2L),
            eq(2)
        )).thenReturn(0);

        assertThatThrownBy(() -> service.deactivate(principal, 2L, 81L, 2))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(409)
            );
    }
}
