package com.face.platform.aftersale;

import java.util.Map;
import java.util.Set;

public final class AfterSalePolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "OPEN", Set.of("TRIAGED", "REJECTED"),
        "TRIAGED", Set.of("PROCESSING", "REJECTED"),
        "PROCESSING", Set.of("WAITING_CUSTOMER", "RESOLVED", "REJECTED"),
        "WAITING_CUSTOMER", Set.of("PROCESSING"),
        "RESOLVED", Set.of("CLOSED", "REOPENED"),
        "REOPENED", Set.of("PROCESSING"),
        "CLOSED", Set.of(),
        "REJECTED", Set.of()
    );

    private AfterSalePolicy() {
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
