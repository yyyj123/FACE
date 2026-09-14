package com.face.platform.reconciliation;

import java.util.Map;
import java.util.Set;

public final class ReconciliationLifecyclePolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "PENDING", Set.of("RUNNING"),
        "RUNNING", Set.of("MATCHED", "DIFFERENT"),
        "MATCHED", Set.of("CLOSED"),
        "DIFFERENT", Set.of("RESOLVED"),
        "RESOLVED", Set.of("CLOSED"),
        "CLOSED", Set.of()
    );

    private ReconciliationLifecyclePolicy() {
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}

