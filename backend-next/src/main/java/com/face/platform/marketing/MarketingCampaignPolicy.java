package com.face.platform.marketing;

import java.util.Map;
import java.util.Set;

public final class MarketingCampaignPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT", Set.of("PENDING_APPROVAL", "CANCELLED"),
        "PENDING_APPROVAL", Set.of("APPROVED", "REJECTED", "CANCELLED"),
        "APPROVED", Set.of("RUNNING", "CANCELLED"),
        "RUNNING", Set.of("COMPLETED")
    );

    private MarketingCampaignPolicy() {
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canApprove(long approverId, long creatorId, long submitterId) {
        return approverId != creatorId && approverId != submitterId;
    }

    public static boolean channelAvailable(String channel) {
        return "IN_APP".equals(channel);
    }
}
