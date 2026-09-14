package com.face.platform.points;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MallCartApiContractTest {

    @Test
    void clientCartExposesMemberScopedDeleteWithoutChangingCheckoutPricing() throws Exception {
        String controller = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/store/V3MallController.java"),
            StandardCharsets.UTF_8
        );
        String service = Files.readString(
            Path.of("src/main/java/com/face/platform/store/MallApplicationService.java"),
            StandardCharsets.UTF_8
        );

        assertThat(controller).contains("@DeleteMapping(\"/client/mall/cart/{itemId}\")");
        assertThat(controller).contains("@PutMapping(\"/client/mall/cart/{itemId}\")");
        assertThat(controller).contains("service.removeCartItem(principal(request), itemId)");
        assertThat(controller).contains("service.updateCartItemQuantity(principal(request), itemId, body.quantity())");
        assertThat(service).contains("public Map<String, Object> removeCartItem(TenantPrincipal principal, long itemId)");
        assertThat(service).contains("public Map<String, Object> updateCartItemQuantity(TenantPrincipal principal, long itemId, int quantity)");
        assertThat(service).contains("DELETE FROM mall_cart_item WHERE id = ? AND tenant_id = ? AND member_id = ?");
        assertThat(service).contains("UPDATE mall_cart_item SET quantity = ?, selected = 1, version = version + 1 WHERE id = ? AND tenant_id = ? AND member_id = ?");
        assertThat(service).contains("validateModeRow(row, mode)");
    }
}
