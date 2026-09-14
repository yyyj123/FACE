package com.face.platform.shop;

import com.face.platform.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopContextServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void singleShopAlwaysUsesConfiguredActiveShop() {
        ShopContextService service = new ShopContextService(jdbcTemplate, "SINGLE_SHOP", 7L);
        when(jdbcTemplate.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of(
            "id", 7L,
            "tenantId", 3L,
            "name", "FACE 中心店",
            "status", "ACTIVE"
        )));

        ShopContextService.ShopContext context = service.requirePublicShop(null);

        assertThat(context.shopId()).isEqualTo(7L);
        assertThat(context.tenantId()).isEqualTo(3L);
        assertThat(context.businessMode()).isEqualTo("SINGLE_SHOP");
    }

    @Test
    void invalidConfiguredShopIsRejectedInsteadOfFallingBack() {
        ShopContextService service = new ShopContextService(jdbcTemplate, "SINGLE_SHOP", 99L);
        when(jdbcTemplate.queryForList(anyString(), eq(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requirePublicShop(null))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(exception.getMessage()).contains("默认门店");
            });
    }

    @Test
    void mismatchedTenantIsRejectedForMemberCommands() {
        ShopContextService service = new ShopContextService(jdbcTemplate, "SINGLE_SHOP", 7L);
        when(jdbcTemplate.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of(
            "id", 7L,
            "tenantId", 3L,
            "name", "FACE 中心店",
            "status", "ACTIVE"
        )));

        assertThatThrownBy(() -> service.requireTenantShop(4L, 7L))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }
}
