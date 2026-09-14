package com.face.platform.booking;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingAdministrationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final ShopContextService shopContextService = mock(ShopContextService.class);
    private final BookingAdministrationService service = new BookingAdministrationService(
        jdbcTemplate, accessService, shopContextService
    );
    private final TenantPrincipal principal = new TenantPrincipal(
        11L, 1L, 2L, "manager01", List.of("MANAGER"),
        Set.of(), Set.of(2L), false
    );

    @Test
    void bookingPolicyRejectsNonHourlySlotInterval() {
        when(shopContextService.requireTenantShop(1L, 2L)).thenReturn(new ShopContextService.ShopContext(
            2L, 1L, "FACE", null, null, null, "Asia/Shanghai", "SINGLE_SHOP"
        ));
        when(accessService.requireShopPermission(principal, 2L, "schedule:manage")).thenReturn(2L);

        var command = new BookingAdministrationService.PolicyCommand(
            30, 0, 0, 30, true, 1440, 720, 1,
            "FULL_REFUND", BigDecimal.ZERO, ""
        );

        assertThatThrownBy(() -> service.updatePolicy(principal, 2L, 11L, command))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(400);
                assertThat(exception.getMessage()).contains("60 分钟");
            });
    }

    @Test
    void inactiveRecurringRuleCanBeActivatedWithVersionAndConflictChecks() {
        LocalDate effectiveFrom = LocalDate.of(2026, 8, 1);
        when(shopContextService.requireTenantShop(1L, 2L)).thenReturn(new ShopContextService.ShopContext(
            2L, 1L, "FACE", null, null, null, "Asia/Shanghai", "SINGLE_SHOP"
        ));
        when(accessService.requireShopPermission(principal, 2L, "schedule:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(71L), eq(1L), eq(2L), eq(3)))
            .thenReturn(List.of(Map.of(
                "staffId", 21L,
                "dayOfWeek", 1,
                "startTime", LocalTime.of(9, 0),
                "endTime", LocalTime.of(18, 0),
                "ruleType", "WORK",
                "effectiveFrom", effectiveFrom
            )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(21L), eq(1L), eq(2L)))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject(
            anyString(), eq(Integer.class), eq(1L), eq(2L), eq(21L), eq(1), eq(true),
            eq(LocalTime.of(18, 0)), eq(LocalTime.of(9, 0)),
            eq(LocalDate.of(9999, 12, 31)), eq(effectiveFrom)
        )).thenReturn(0);
        when(jdbcTemplate.update(
            org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("SET status = 'ACTIVE'")),
            eq(11L), eq(71L), eq(1L), eq(2L), eq(3)
        )).thenReturn(1);

        Map<String, Object> result = service.activateRule(principal, 2L, 71L, 3);

        assertThat(result).containsEntry("status", "ACTIVE").containsEntry("version", 4);
        verify(jdbcTemplate).update(
            org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("SET status = 'ACTIVE'")),
            eq(11L), eq(71L), eq(1L), eq(2L), eq(3)
        );
    }

    @Test
    void activeOrStaleRecurringRuleCannotBeActivatedAgain() {
        when(shopContextService.requireTenantShop(1L, 2L)).thenReturn(new ShopContextService.ShopContext(
            2L, 1L, "FACE", null, null, null, "Asia/Shanghai", "SINGLE_SHOP"
        ));
        when(accessService.requireShopPermission(principal, 2L, "schedule:manage")).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(71L), eq(1L), eq(2L), eq(3)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.activateRule(principal, 2L, 71L, 3))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(409)
            );
    }
}
