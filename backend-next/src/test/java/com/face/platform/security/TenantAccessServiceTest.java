package com.face.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class TenantAccessServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService service = new TenantAccessService(jdbcTemplate);

    @Test
    void rejectsBlankToken() {
        assertThat(service.resolve("  ")).isEmpty();
    }

    @Test
    void resolvesTenantAndScopedRolesFromServerSideToken() {
        when(jdbcTemplate.queryForList(anyString(), eq("valid-token"))).thenReturn(List.of(
            Map.of(
                "account_id", 10L,
                "tenant_id", 1L,
                "home_shop_id", 2L,
                "username", "manager"
            )
        ));
        when(jdbcTemplate.queryForList(anyString(), eq(10L), eq(1L))).thenReturn(List.of(
            Map.of(
                "role_code", "MANAGER",
                "scope_type", "SHOP",
                "shop_id", 2L
            )
        ));

        Optional<TenantPrincipal> result = service.resolve("valid-token");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().tenantId()).isEqualTo(1L);
        assertThat(result.orElseThrow().homeShopId()).isEqualTo(2L);
        assertThat(result.orElseThrow().roles()).containsExactly("MANAGER");
        assertThat(result.orElseThrow().shopIds()).containsExactly(2L);
        assertThat(result.orElseThrow().tenantWide()).isFalse();
    }

    @Test
    void resolvesV3PrincipalOnlyFromActiveHashedSession() {
        String rawToken = "v3-raw-access-token";
        String tokenHash = com.face.platform.v3.auth.SessionTokenCodec.sha256(rawToken);
        when(jdbcTemplate.queryForList(anyString(), eq(tokenHash))).thenReturn(List.of(
            Map.of(
                "account_id", 10L,
                "tenant_id", 1L,
                "home_shop_id", 2L,
                "username", "manager"
            )
        ));
        when(jdbcTemplate.queryForList(anyString(), eq(10L), eq(1L))).thenReturn(List.of(
            Map.of(
                "role_code", "MANAGER",
                "scope_type", "SHOP",
                "shop_id", 2L
            )
        ));

        Optional<TenantPrincipal> result = service.resolveV3(rawToken);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().tenantId()).isEqualTo(1L);
        assertThat(result.orElseThrow().roles()).containsExactly("MANAGER");
        assertThat(result.orElseThrow().shopIds()).containsExactly(2L);
    }

    @Test
    void rejectsShopOutsideServerResolvedScope() {
        TenantPrincipal principal = new TenantPrincipal(
            10L, 1L, 2L, "manager", List.of("MANAGER"), java.util.Set.of(), java.util.Set.of(2L), false
        );
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(
            Map.of("id", 2L)
        ));

        assertThat(service.requireShopAccess(principal, 2L)).isEqualTo(2L);
        assertThatThrownBy(() -> service.requireShopAccess(principal, 99L))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining("无权管理");
    }

    @Test
    void requiresManagementPermissionFromActiveServerSideGrant() {
        TenantPrincipal principal = new TenantPrincipal(
            10L, 1L, 2L, "manager", List.of("MANAGER"), java.util.Set.of(), java.util.Set.of(2L), false
        );
        when(jdbcTemplate.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(10L),
            eq(1L),
            eq("member:manage")
        )).thenReturn(1);

        service.requireManagementPermission(principal, "member:manage");
    }

    @Test
    void requiresPermissionAndShopScopeFromTheSameActiveGrant() {
        TenantPrincipal principal = new TenantPrincipal(
            10L, 1L, 2L, "manager", List.of("MANAGER"), java.util.Set.of(), java.util.Set.of(2L, 3L), false
        );
        when(jdbcTemplate.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(10L),
            eq(1L),
            eq(2L),
            eq("appointment:manage")
        )).thenReturn(1);

        assertThat(service.requireShopPermission(principal, 2L, "appointment:manage"))
            .isEqualTo(2L);
    }

    @Test
    void selfScopedRoleCanUseItsGrantedPermissionOnlyInsideAssignedShop() {
        TenantPrincipal principal = new TenantPrincipal(
            11L, 1L, 2L, "jishi01", List.of("BEAUTICIAN"), java.util.Set.of(), java.util.Set.of(2L), false
        );
        when(jdbcTemplate.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(11L),
            eq(1L),
            eq(2L),
            eq("service_record:manage")
        )).thenReturn(1);

        assertThat(service.requireShopPermission(principal, 2L, "service_record:manage"))
            .isEqualTo(2L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
            sql.capture(),
            eq(Integer.class),
            eq(11L),
            eq(1L),
            eq(2L),
            eq("service_record:manage")
        );
        assertThat(sql.getValue()).contains("'SELF'");
        assertThat(sql.getValue()).contains("ar.shop_id = s.id");
    }

    @Test
    void rejectsPermissionFromAnotherShopGrant() {
        TenantPrincipal principal = new TenantPrincipal(
            10L, 1L, 2L, "manager", List.of("MANAGER"), java.util.Set.of(), java.util.Set.of(2L, 3L), false
        );
        when(jdbcTemplate.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(10L),
            eq(1L),
            eq(3L),
            eq("appointment:manage")
        )).thenReturn(0);

        assertThatThrownBy(() -> service.requireShopPermission(principal, 3L, "appointment:manage"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining("该门店");
    }

    @Test
    void listsOnlyShopsCoveredByThePermissionGrant() {
        TenantPrincipal principal = new TenantPrincipal(
            10L, 1L, 2L, "manager", List.of("MANAGER"), java.util.Set.of(), java.util.Set.of(2L, 3L), false
        );
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(10L),
            eq(1L),
            eq("appointment:view")
        )).thenReturn(List.of(Map.of("id", 2L)));

        assertThat(service.accessibleShopIds(principal, "appointment:view"))
            .containsExactly(2L);
    }

    @Test
    void exposesOnlyPermissionsFromActiveServerSideGrants() {
        TenantPrincipal principal = new TenantPrincipal(
            10L, 1L, 2L, "manager", List.of("MANAGER"), java.util.Set.of(), java.util.Set.of(2L), false
        );
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(String.class),
            eq(10L),
            eq(1L)
        )).thenReturn(List.of("analytics:view", "order:view"));

        assertThat(service.permissionCodes(principal))
            .containsExactly("analytics:view", "order:view");
    }

    @Test
    void selfScopedRoleExposesItsPermissionCodesWithoutGainingManagementScope() {
        TenantPrincipal principal = new TenantPrincipal(
            11L, 1L, 2L, "jishi01", List.of("BEAUTICIAN"), java.util.Set.of(), java.util.Set.of(2L), false
        );
        when(jdbcTemplate.queryForList(
            anyString(),
            eq(String.class),
            eq(11L),
            eq(1L)
        )).thenReturn(List.of("appointment:view:self"));

        assertThat(service.permissionCodes(principal)).containsExactly("appointment:view:self");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(
            sql.capture(),
            eq(String.class),
            eq(11L),
            eq(1L)
        );
        assertThat(sql.getValue()).contains("'SELF'");
    }
}
