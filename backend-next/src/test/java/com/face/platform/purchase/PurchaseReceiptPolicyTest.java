package com.face.platform.purchase;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PurchaseReceiptPolicyTest {

    @Test
    void receiptMustBePositiveAndCannotExceedOutstandingQuantity() {
        assertThat(
            PurchaseReceiptPolicy.requireReceivable(
                new BigDecimal("10.000"),
                new BigDecimal("4.000"),
                new BigDecimal("6.000")
            )
        ).isEqualByComparingTo("6.000");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> PurchaseReceiptPolicy.requireReceivable(
                new BigDecimal("10.000"),
                new BigDecimal("4.000"),
                new BigDecimal("6.001")
            ))
            .withMessageContaining("超过");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> PurchaseReceiptPolicy.requireReceivable(
                new BigDecimal("10.000"),
                new BigDecimal("4.000"),
                BigDecimal.ZERO
            ))
            .withMessageContaining("大于0");
    }

    @Test
    void expiryCannotBeBeforeProducedDate() {
        PurchaseReceiptPolicy.requireValidDates(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2027, 7, 1)
        );

        assertThatIllegalArgumentException()
            .isThrownBy(() -> PurchaseReceiptPolicy.requireValidDates(
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 1)
            ))
            .withMessageContaining("有效期");
    }

    @Test
    void receiptStatusReflectsWhetherEveryLineIsFullyReceived() {
        assertThat(PurchaseReceiptPolicy.statusAfterReceipt(false))
            .isEqualTo("PARTIALLY_RECEIVED");
        assertThat(PurchaseReceiptPolicy.statusAfterReceipt(true))
            .isEqualTo("RECEIVED");
    }
}
