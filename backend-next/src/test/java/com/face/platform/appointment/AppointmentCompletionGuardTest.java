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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentCompletionGuardTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService tenantAccessService = mock(TenantAccessService.class);
    private final ServiceRecordLifecycleService lifecycleService = mock(ServiceRecordLifecycleService.class);
    private final AppointmentService service = new AppointmentService(
        jdbcTemplate,
        tenantAccessService,
        lifecycleService,
        mock(ResourceBookingService.class)
    );

    @Test
    void beauticianCannotCompleteAppointmentThroughGenericClientStatusEndpoint() {
        TenantPrincipal principal = new TenantPrincipal(
            20L,
            1L,
            2L,
            "jishi01",
            List.of("BEAUTICIAN"),
            Set.of(),
            Set.of(2L),
            false
        );

        assertThatThrownBy(() -> service.changeStatusForClient(
            principal,
            100L,
            "COMPLETED",
            0,
            null
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("护理记录");
    }

    @Test
    void managerCannotCompleteAppointmentThroughGenericManagementStatusEndpoint() {
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
        when(tenantAccessService.requireShopPermission(
            principal,
            2L,
            "appointment:manage"
        )).thenReturn(2L);

        assertThatThrownBy(() -> service.changeStatus(
            principal,
            100L,
            new AppointmentStatusRequest(2L, "COMPLETED", 0, null)
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("护理记录");
    }
}
