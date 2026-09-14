package com.face.platform.appointment;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.servicecare.ServiceRecordLifecycleService;
import com.face.platform.resource.ResourceBookingService;
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

class AppointmentSelfScopeTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AppointmentService service = new AppointmentService(
        jdbcTemplate,
        mock(TenantAccessService.class),
        mock(ServiceRecordLifecycleService.class),
        mock(ResourceBookingService.class)
    );

    @Test
    void beauticianCannotOperateAppointmentAssignedToAnotherStaffMember() {
        TenantPrincipal beautician = new TenantPrincipal(
            11L,
            1L,
            2L,
            "jishi01",
            List.of("BEAUTICIAN"),
            Set.of(),
            Set.of(2L),
            false
        );
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(11L),
            eq(9001L),
            eq(1L),
            eq(0),
            eq(1)
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.changeStatusForClient(
            beautician,
            9001L,
            "IN_SERVICE",
            0,
            null
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(404)
        );

        verify(jdbcTemplate).queryForList(
            anyString(),
            eq(11L),
            eq(9001L),
            eq(1L),
            eq(0),
            eq(1)
        );
    }

    @Test
    void beauticianCannotUseShopWideManagementAppointmentList() {
        TenantPrincipal beautician = new TenantPrincipal(
            11L,
            1L,
            2L,
            "jishi01",
            List.of("BEAUTICIAN"),
            Set.of(),
            Set.of(2L),
            false
        );

        assertThatThrownBy(() -> service.list(
            beautician,
            2L,
            null,
            null,
            null,
            null,
            1,
            20
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status().value()).isEqualTo(403);
            assertThat(exception.getMessage()).contains("独立技师端");
        });
    }
}
