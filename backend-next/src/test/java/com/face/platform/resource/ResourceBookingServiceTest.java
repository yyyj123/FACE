package com.face.platform.resource;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceBookingServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final ResourceBookingService service =
        new ResourceBookingService(jdbcTemplate, accessService);
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
    private final LocalDateTime startAt = LocalDateTime.of(2026, 8, 1, 10, 0);
    private final LocalDateTime endAt = LocalDateTime.of(2026, 8, 1, 11, 0);

    @Test
    void resourcesAreLockedInStableOrderBeforeOverlapChecksAndReservations() {
        when(accessService.requireShopPermission(principal, 2L, "appointment:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(8L)))
            .thenReturn(List.of(Map.of("id", 8L, "status", "ACTIVE", "capacity", 1)));
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(9L)))
            .thenReturn(List.of(Map.of("id", 9L, "status", "ACTIVE", "capacity", 1)));
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(Long.class),
            eq(1L),
            any(Long.class),
            eq(endAt),
            eq(startAt),
            eq(7001L)
        )).thenReturn(List.of());

        service.reserve(
            principal,
            2L,
            7001L,
            List.of(9L, 8L),
            startAt,
            endAt
        );

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).queryForList(anyString(), eq(1L), eq(2L), eq(8L));
        inOrder.verify(jdbcTemplate).queryForList(anyString(), eq(1L), eq(2L), eq(9L));
        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq(2L),
            eq(7001L),
            eq(8L),
            eq(startAt),
            eq(endAt),
            eq(11L)
        );
        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq(2L),
            eq(7001L),
            eq(9L),
            eq(startAt),
            eq(endAt),
            eq(11L)
        );
    }

    @Test
    void overlappingReservationIsRejected() {
        when(accessService.requireShopPermission(principal, 2L, "appointment:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(8L)))
            .thenReturn(List.of(Map.of("id", 8L, "status", "ACTIVE", "capacity", 1)));
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(Long.class),
            eq(1L),
            eq(8L),
            eq(endAt),
            eq(startAt),
            eq(7001L)
        )).thenReturn(List.of(6001L));

        assertThatThrownBy(() -> service.reserve(
            principal,
            2L,
            7001L,
            List.of(8L),
            startAt,
            endAt
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(409)
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void resourceCapacityAllowsReservationsUntilTheLimitIsReached() {
        when(accessService.requireShopPermission(principal, 2L, "appointment:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(8L)))
            .thenReturn(List.of(Map.of("id", 8L, "status", "ACTIVE", "capacity", 2)));
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(Long.class),
            eq(1L),
            eq(8L),
            eq(endAt),
            eq(startAt),
            eq(7001L)
        )).thenReturn(List.of(6001L));

        service.reserve(
            principal,
            2L,
            7001L,
            List.of(8L),
            startAt,
            endAt
        );

        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq(2L),
            eq(7001L),
            eq(8L),
            eq(startAt),
            eq(endAt),
            eq(11L)
        );
    }

    @Test
    void inactiveResourceCannotBeReserved() {
        when(accessService.requireShopPermission(principal, 2L, "appointment:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(8L)))
            .thenReturn(List.of(Map.of("id", 8L, "status", "INACTIVE", "capacity", 1)));

        assertThatThrownBy(() -> service.reserve(
            principal,
            2L,
            7001L,
            List.of(8L),
            startAt,
            endAt
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(409)
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void releaseChangesStatusAndNeverDeletesHistory() {
        when(accessService.requireShopPermission(principal, 2L, "appointment:manage")).thenReturn(2L);

        service.releaseForAppointment(principal, 2L, 7001L, "RESCHEDULED");

        verify(jdbcTemplate).update(
            org.mockito.ArgumentMatchers.argThat(sql ->
                sql.toUpperCase().contains("UPDATE RESOURCE_BOOKING")
                    && !sql.toUpperCase().contains("DELETE")
            ),
            eq("RESCHEDULED"),
            eq(11L),
            eq(1L),
            eq(2L),
            eq(7001L)
        );
    }
}
