package com.face.platform.transaction;

import java.math.BigDecimal;
import java.util.List;

final class TransactionStatusPolicy {

    private TransactionStatusPolicy() {
    }

    static boolean canAcceptPayment(String status) {
        return List.of("UNPAID", "PARTIALLY_PAID").contains(status);
    }

    static String paymentStatus(BigDecimal payable, BigDecimal paidAfter) {
        return paidAfter.compareTo(payable) >= 0 ? "PAID" : "PARTIALLY_PAID";
    }

    static String refundStatus(BigDecimal paid, BigDecimal refundedAfter) {
        return refundedAfter.compareTo(paid) >= 0 ? "REFUNDED" : "PARTIALLY_REFUNDED";
    }
}
