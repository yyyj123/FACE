package com.face.platform.approval;

import java.util.Set;

public final class ApprovalPolicy {

    private static final Set<String> TERMINAL = Set.of(
        "APPROVED", "REJECTED", "CANCELLED", "EXPIRED"
    );

    private ApprovalPolicy() {
    }

    public static boolean canTransition(String from, String to) {
        return "PENDING".equals(from) && TERMINAL.contains(to);
    }

    public static boolean canDecide(long deciderId, long requesterId) {
        return deciderId != requesterId;
    }
}
