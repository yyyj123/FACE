package com.face.platform.purchase;

import java.util.Map;
import java.util.Set;

public final class PurchaseOrderStatusPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT", Set.of("SUBMITTED"),
        "SUBMITTED", Set.of("APPROVED", "CLOSED"),
        "APPROVED", Set.of("PARTIALLY_RECEIVED", "RECEIVED", "CLOSED"),
        "PARTIALLY_RECEIVED", Set.of("RECEIVED", "CLOSED"),
        "RECEIVED", Set.of("CLOSED"),
        "CLOSED", Set.of()
    );

    private PurchaseOrderStatusPolicy() {
    }

    public static boolean canTransition(String from, String to) {
        return from != null
            && to != null
            && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}

