package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffScheduleServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final StaffScheduleService service =
        new StaffScheduleService(jdbcTemplate, accessService);
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
    void overlappingActiveScheduleIsRejected() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(12, 0);
        when(accessService.requireShopPermission(principal, 2L, "staff:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L)))
            .thenReturn(List.of(Map.of("id", 21L)));
        when(jdbcTemplate.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(1L),
            eq(2L),
            eq(21L),
            eq(date),
            eq(end),
            eq(start)
        )).thenReturn(1);

        assertThatThrownBy(() -> service.create(
            principal,
            2L,
            21L,
            date,
            start,
            end,
            "WORK",
            null
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(409)
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void wholeDayLeaveAllowsNullTimesAndIsPersistedAsActiveVersionOne() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(accessService.requireShopPermission(principal, 2L, "staff:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L)))
            .thenReturn(List.of(Map.of("id", 21L)));
        when(jdbcTemplate.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(1L),
            eq(2L),
            eq(21L),
            eq(date)
        )).thenReturn(0);

        service.create(
            principal,
            2L,
            21L,
            date,
            null,
            null,
            "LEAVE",
            "年假"
        );

        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq(2L),
            eq(21L),
            eq(date),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            eq("LEAVE"),
            eq("年假"),
            eq(11L),
            eq(11L)
        );
    }

    @Test
    void workScheduleRequiresAValidTimeRange() {
        when(accessService.requireShopPermission(principal, 2L, "staff:manage")).thenReturn(2L);

        assertThatThrownBy(() -> service.create(
            principal,
            2L,
            21L,
            LocalDate.of(2026, 8, 1),
            LocalTime.of(12, 0),
            LocalTime.of(9, 0),
            "WORK",
            null
        )).isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }
}
