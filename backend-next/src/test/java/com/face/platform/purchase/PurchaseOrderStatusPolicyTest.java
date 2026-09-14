package com.face.platform.purchase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseOrderStatusPolicyTest {

    @Test
    void onlyDocumentedForwardTransitionsAreAllowed() {
        assertTrue(PurchaseOrderStatusPolicy.canTransition("DRAFT", "SUBMITTED"));
        assertTrue(PurchaseOrderStatusPolicy.canTransition("SUBMITTED", "APPROVED"));
        assertTrue(PurchaseOrderStatusPolicy.canTransition("APPROVED", "PARTIALLY_RECEIVED"));
        assertTrue(PurchaseOrderStatusPolicy.canTransition("APPROVED", "RECEIVED"));
        assertTrue(PurchaseOrderStatusPolicy.canTransition("PARTIALLY_RECEIVED", "RECEIVED"));
        assertTrue(PurchaseOrderStatusPolicy.canTransition("APPROVED", "CLOSED"));

        assertFalse(PurchaseOrderStatusPolicy.canTransition("DRAFT", "RECEIVED"));
        assertFalse(PurchaseOrderStatusPolicy.canTransition("RECEIVED", "APPROVED"));
        assertFalse(PurchaseOrderStatusPolicy.canTransition("CLOSED", "SUBMITTED"));
    }
}

