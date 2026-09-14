package com.face.platform.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStatusPolicyTest {

    @Test
    void onlyUnpaidAndPartiallyPaidOrdersAcceptPayment() {
        assertTrue(TransactionStatusPolicy.canAcceptPayment("UNPAID"));
        assertTrue(TransactionStatusPolicy.canAcceptPayment("PARTIALLY_PAID"));
        assertFalse(TransactionStatusPolicy.canAcceptPayment("PAID"));
        assertFalse(TransactionStatusPolicy.canAcceptPayment("REFUNDED"));
    }

    @Test
    void paymentStatusReflectsOutstandingAmount() {
        assertEquals(
            "PARTIALLY_PAID",
            TransactionStatusPolicy.paymentStatus(new BigDecimal("500.00"), new BigDecimal("200.00"))
        );
        assertEquals(
            "PAID",
            TransactionStatusPolicy.paymentStatus(new BigDecimal("500.00"), new BigDecimal("500.00"))
        );
    }

    @Test
    void refundStatusDistinguishesPartialAndFullRefunds() {
        assertEquals(
            "PARTIALLY_REFUNDED",
            TransactionStatusPolicy.refundStatus(new BigDecimal("500.00"), new BigDecimal("120.00"))
        );
        assertEquals(
            "REFUNDED",
            TransactionStatusPolicy.refundStatus(new BigDecimal("500.00"), new BigDecimal("500.00"))
        );
    }
}
