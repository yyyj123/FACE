package com.face.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantIdentityScopeTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService service = new TenantAccessService(jdbcTemplate);

    @Test
    void memberIdentityComesFromTheAuthenticatedAccount() {
        TenantPrincipal principal = principal("MEMBER");
        when(jdbcTemplate.queryForObject(
            "SELECT member_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            Long.class,
            principal.accountId(),
            principal.tenantId()
        )).thenReturn(88L);

        assertThat(service.requireMemberId(principal)).isEqualTo(88L);
    }

    @Test
    void technicianCannotBeTreatedAsAMember() {
        TenantPrincipal principal = principal("BEAUTICIAN");

        assertThatThrownBy(() -> service.requireMemberId(principal))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("会员");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void technicianIdentityComesFromTheAuthenticatedAccount() {
        TenantPrincipal principal = principal("BEAUTICIAN");
        when(jdbcTemplate.queryForObject(
            "SELECT staff_id FROM account WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            Long.class,
            principal.accountId(),
            principal.tenantId()
        )).thenReturn(66L);

        assertThat(service.requireStaffId(principal)).isEqualTo(66L);
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
