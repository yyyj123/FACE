package com.face.platform.store;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MallPolicyTest {

    @Test
    void threeModesHaveIndependentPricesButOneInventoryGuard() {
        assertThat(MallPolicy.price("CASH", true, true, true,
            new BigDecimal("20"), 200L, new BigDecimal("8"), 80L, 2))
            .isEqualTo(new MallPolicy.Price(new BigDecimal("40"), 0));
        assertThat(MallPolicy.price("POINTS", true, true, true,
            new BigDecimal("20"), 200L, new BigDecimal("8"), 80L, 2).pointsAmount())
            .isEqualTo(400);
        assertThat(MallPolicy.price("COMBINATION", true, true, true,
            new BigDecimal("20"), 200L, new BigDecimal("8"), 80L, 2))
            .isEqualTo(new MallPolicy.Price(new BigDecimal("16"), 160));
    }

    @Test
    void splitKeySeparatesModesAndDedicatedShipping() {
        assertThat(MallPolicy.splitKey("CASH", "DELIVERY", null, "F1", false, 7))
            .isEqualTo("CASH|DELIVERY|-|F1|SHARED");
        assertThat(MallPolicy.splitKey("CASH", "DELIVERY", null, "F1", true, 7))
            .endsWith("SKU:7");
    }
}
