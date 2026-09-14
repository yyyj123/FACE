package com.face.platform.reconciliation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconciliationPolicyTest {

    @Test
    void matchingCountsAndAmountsProduceMatchedStatus() {
        assertEquals(
            "MATCHED",
            ReconciliationPolicy.resultStatus(
                12, new BigDecimal("1280.00"), new BigDecimal("80.00"),
                12, new BigDecimal("1280.00"), new BigDecimal("80.00")
            )
        );
    }

    @Test
    void anyCountPaymentOrRefundDifferenceProducesDifferentStatus() {
        assertEquals(
            "DIFFERENT",
            ReconciliationPolicy.resultStatus(
                12, new BigDecimal("1280.00"), new BigDecimal("80.00"),
                11, new BigDecimal("1280.00"), new BigDecimal("80.00")
            )
        );
        assertEquals(
            "DIFFERENT",
            ReconciliationPolicy.resultStatus(
                12, new BigDecimal("1280.00"), new BigDecimal("80.00"),
                12, new BigDecimal("1279.99"), new BigDecimal("80.00")
            )
        );
        assertEquals(
            "DIFFERENT",
            ReconciliationPolicy.resultStatus(
                12, new BigDecimal("1280.00"), new BigDecimal("80.00"),
                12, new BigDecimal("1280.00"), new BigDecimal("79.99")
            )
        );
    }
}
