package com.face.platform.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundExecutionPolicyTest {

    @Test
    void refundableAmountSubtractsSuccessfulAndReservedRefunds() {
        assertEquals(
            new BigDecimal("65.00"),
            RefundExecutionPolicy.refundableAmount(
                new BigDecimal("100.00"),
                new BigDecimal("25.00"),
                new BigDecimal("10.00")
            )
        );
        assertTrue(
            RefundExecutionPolicy.canReserve(
                new BigDecimal("100.00"),
                new BigDecimal("25.00"),
                new BigDecimal("10.00"),
                new BigDecimal("65.00")
            )
        );
    }

    @Test
    void refundRejectsOverReservationAndInconsistentSnapshots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RefundExecutionPolicy.requireReservable(
                new BigDecimal("100.00"),
                new BigDecimal("25.00"),
                new BigDecimal("10.00"),
                new BigDecimal("65.01")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RefundExecutionPolicy.refundableAmount(
                new BigDecimal("100.00"),
                new BigDecimal("90.00"),
                new BigDecimal("20.00")
            )
        );
    }

    @Test
    void onlyDocumentedRefundTransitionsAreAllowed() {
        assertTrue(RefundExecutionPolicy.canTransition("PENDING", "APPROVED"));
        assertTrue(RefundExecutionPolicy.canTransition("PENDING", "REJECTED"));
        assertTrue(RefundExecutionPolicy.canTransition("APPROVED", "PROCESSING"));
        assertTrue(RefundExecutionPolicy.canTransition("PROCESSING", "SUCCESS"));
        assertTrue(RefundExecutionPolicy.canTransition("PROCESSING", "FAILED"));
        assertTrue(RefundExecutionPolicy.canTransition("FAILED", "PROCESSING"));
        assertFalse(RefundExecutionPolicy.canTransition("PENDING", "SUCCESS"));
        assertFalse(RefundExecutionPolicy.canTransition("APPROVED", "SUCCESS"));
        assertFalse(RefundExecutionPolicy.canTransition("SUCCESS", "PROCESSING"));
    }
}
