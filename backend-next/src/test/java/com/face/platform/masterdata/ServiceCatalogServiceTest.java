package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCatalogServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final ServiceCatalogService service =
        new ServiceCatalogService(jdbcTemplate, accessService);
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
    void updateUsesShopPermissionAndOptimisticVersion() {
        when(accessService.requireShopPermission(principal, 2L, "service:manage")).thenReturn(2L);
        when(jdbcTemplate.update(
            anyString(),
            eq("深层补水护理"),
            eq("/face-next/upload/service.webp"),
            eq(60),
            eq(10),
            eq(new BigDecimal("398.00")),
            eq(new BigDecimal("328.00")),
            eq("ACTIVE"),
            eq(11L),
            eq(31L),
            eq(1L),
            eq(2L),
            eq(4)
        )).thenReturn(1);

        service.update(
            principal,
            2L,
            31L,
            "深层补水护理",
            "/face-next/upload/service.webp",
            60,
            10,
            new BigDecimal("398.00"),
            new BigDecimal("328.00"),
            "ACTIVE",
            4
        );

        verify(accessService).requireShopPermission(principal, 2L, "service:manage");
    }

    @Test
    void staleServiceVersionReturnsConflict() {
        when(accessService.requireShopPermission(principal, 2L, "service:manage")).thenReturn(2L);
        when(jdbcTemplate.update(
            anyString(),
            eq("深层补水护理"),
            eq("/face-next/upload/service.webp"),
            eq(60),
            eq(10),
            eq(new BigDecimal("398.00")),
            eq(new BigDecimal("328.00")),
            eq("ACTIVE"),
            eq(11L),
            eq(31L),
            eq(1L),
            eq(2L),
            eq(3)
        )).thenReturn(0);

        assertThatThrownBy(() -> service.update(
            principal,
            2L,
            31L,
            "深层补水护理",
            "/face-next/upload/service.webp",
            60,
            10,
            new BigDecimal("398.00"),
            new BigDecimal("328.00"),
            "ACTIVE",
            3
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(409)
        );
    }
}
