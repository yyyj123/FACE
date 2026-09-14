package com.face.platform.settlement;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public final class CommissionSettlementPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT", Set.of("CALCULATED", "VOIDED"),
        "CALCULATED", Set.of("CONFIRMED", "VOIDED"),
        "CONFIRMED", Set.of("PAID", "VOIDED"),
        "PAID", Set.of("CLOSED"),
        "CLOSED", Set.of(),
        "VOIDED", Set.of()
    );

    private CommissionSettlementPolicy() {
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canApprove(long approverId, long creatorId, Long calculatorId) {
        return approverId != creatorId
            && (calculatorId == null || approverId != calculatorId.longValue());
    }

    public static boolean validPeriod(LocalDate start, LocalDate end) {
        return start != null && end != null && !start.isAfter(end);
    }
}
