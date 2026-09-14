package com.face.platform.commission;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommissionSourceSnapshotSelfScopeTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final CommissionSourceSnapshotService service = new CommissionSourceSnapshotService(
        jdbcTemplate,
        accessService,
        mock(OutboxEventService.class),
        new ObjectMapper()
    );

    @Test
    void beauticianCannotReadAnotherStaffMembersCommissionSnapshot() {
        TenantPrincipal beautician = new TenantPrincipal(
            11L,
            1L,
            1L,
            "jishi01",
            List.of("BEAUTICIAN"),
            Set.of(),
            Set.of(1L),
            false
        );
        when(accessService.requireStaffId(beautician)).thenReturn(2L);
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(901L),
            eq(1L),
            eq(1L)
        )).thenReturn(List.of(Map.of(
            "id", 901L,
            "shopId", 1L,
            "sourceType", "SERVICE",
            "sourceId", 701L,
            "staffId", 99L,
            "businessNo", "SR-701",
            "baseAmount", new BigDecimal("300.00"),
            "snapshotData", "{}",
            "snapshotHash", "safe-hash",
            "status", "CAPTURED"
        )));

        assertThatThrownBy(() -> service.detail(beautician, 1L, 901L))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(404)
            );
    }
}
