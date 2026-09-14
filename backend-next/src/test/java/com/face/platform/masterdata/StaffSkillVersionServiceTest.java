package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffSkillVersionServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final StaffSkillVersionService service =
        new StaffSkillVersionService(jdbcTemplate, accessService);
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
    void changingSkillClosesCurrentVersionAndInsertsANewVersionWithoutDeletingHistory() {
        LocalDateTime effectiveFrom = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(accessService.requireShopPermission(principal, 2L, "staff:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L)))
            .thenReturn(List.of(Map.of("id", 21L)));
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L), eq(31L)))
            .thenReturn(List.of(Map.of(
                "id", 101L,
                "version", 3,
                "effective_from", LocalDateTime.of(2026, 7, 1, 0, 0)
            )));
        when(jdbcTemplate.update(
            anyString(),
            eq(effectiveFrom),
            eq(11L),
            eq(101L),
            eq(1L),
            eq(3)
        )).thenReturn(1);
        when(jdbcTemplate.update(
            anyString(),
            eq(1L),
            eq(2L),
            eq(21L),
            eq(31L),
            eq(false),
            eq(75),
            eq(effectiveFrom),
            eq(4),
            eq(11L)
        )).thenReturn(1);

        service.changeSkill(
            principal,
            2L,
            21L,
            31L,
            false,
            75,
            effectiveFrom,
            3
        );

        verify(accessService).requireShopPermission(principal, 2L, "staff:manage");
        verify(jdbcTemplate).update(
            anyString(),
            eq(effectiveFrom),
            eq(11L),
            eq(101L),
            eq(1L),
            eq(3)
        );
        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq(2L),
            eq(21L),
            eq(31L),
            eq(false),
            eq(75),
            eq(effectiveFrom),
            eq(4),
            eq(11L)
        );
        verify(jdbcTemplate, never()).update(
            org.mockito.ArgumentMatchers.argThat(sql -> sql.toUpperCase().contains("DELETE")),
            any(Object[].class)
        );
    }

    @Test
    void staleVersionIsRejectedBeforeAnyHistoryMutation() {
        when(accessService.requireShopPermission(principal, 2L, "staff:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L)))
            .thenReturn(List.of(Map.of("id", 21L)));
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L), eq(31L)))
            .thenReturn(List.of(Map.of(
                "id", 101L,
                "version", 4,
                "effective_from", LocalDateTime.of(2026, 7, 1, 0, 0)
            )));

        assertThatThrownBy(() -> service.changeSkill(
            principal,
            2L,
            21L,
            31L,
            true,
            null,
            LocalDateTime.of(2026, 8, 1, 0, 0),
            3
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(409)
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void effectiveTimeMustAdvanceAndCustomDurationMustBePositive() {
        when(accessService.requireShopPermission(principal, 2L, "staff:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L)))
            .thenReturn(List.of(Map.of("id", 21L)));
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L), eq(21L), eq(31L)))
            .thenReturn(List.of(Map.of(
                "id", 101L,
                "version", 3,
                "effective_from", LocalDateTime.of(2026, 8, 1, 0, 0)
            )));

        assertThatThrownBy(() -> service.changeSkill(
            principal,
            2L,
            21L,
            31L,
            true,
            0,
            LocalDateTime.of(2026, 7, 31, 0, 0),
            3
        )).isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
