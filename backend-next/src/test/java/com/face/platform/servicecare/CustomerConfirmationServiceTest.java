package com.face.platform.servicecare;

import com.face.platform.api.ApiException;
import com.face.platform.benefit.BenefitApplicationService;
import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerConfirmationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final BenefitApplicationService benefitService = mock(BenefitApplicationService.class);
    private final PointsApplicationService pointsService = mock(PointsApplicationService.class);
    private final CustomerConfirmationService service =
        new CustomerConfirmationService(
            jdbcTemplate, accessService, benefitService, pointsService
        );

    @Test
    void memberCanConfirmOnlyTheirOwnPendingCareResult() {
        TenantPrincipal principal = memberPrincipal();
        when(accessService.requireMemberId(principal)).thenReturn(88L);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq("confirm-501")))
            .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(501L), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 501L,
                "shopId", 2L,
                "memberId", 88L,
                "serviceRecordId", 701L,
                "status", "PENDING",
                "version", 0
            )));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForMap(anyString(), eq(501L), eq(1L), eq(88L)))
            .thenReturn(Map.of("id", 501L, "status", "CONFIRMED", "version", 1));

        Map<String, Object> result = service.act(
            principal,
            501L,
            new CustomerConfirmationActionRequest(
                "CONFIRMED",
                null,
                0,
                "confirm-501"
            )
        );

        assertThat(result.get("status")).isEqualTo("CONFIRMED");
        verify(accessService).requireMemberId(principal);
    }

    @Test
    void technicianCannotUseMemberConfirmationEndpoint() {
        TenantPrincipal principal = technicianPrincipal();
        when(accessService.requireMemberId(principal))
            .thenThrow(new org.springframework.security.access.AccessDeniedException("仅会员本人可确认"));

        assertThatThrownBy(() -> service.act(
            principal,
            501L,
            new CustomerConfirmationActionRequest(
                "CONFIRMED",
                null,
                0,
                "confirm-501"
            )
        ))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectionRequiresAReason() {
        assertThatThrownBy(() -> service.act(
            memberPrincipal(),
            501L,
            new CustomerConfirmationActionRequest(
                "REJECTED",
                " ",
                0,
                "reject-501"
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("原因");
        verifyNoInteractions(jdbcTemplate);
    }

    private TenantPrincipal memberPrincipal() {
        return principal("MEMBER");
    }

    private TenantPrincipal technicianPrincipal() {
        return principal("BEAUTICIAN");
    }

    private TenantPrincipal principal(String role) {
        return new TenantPrincipal(
            10L,
            1L,
            2L,
            role.toLowerCase(),
            List.of(role),
            Set.of(),
            Set.of(2L),
            false
        );
    }
}
