package com.face.platform.marketing;

import java.util.Set;

public final class MarketingConsentPolicy {

    private static final Set<String> CHANNELS = Set.of("IN_APP", "SMS", "EMAIL", "WECHAT");

    private MarketingConsentPolicy() {
    }

    public static boolean isEligible(String status) {
        return "GRANTED".equals(status);
    }

    public static boolean versionMatches(int current, int expected) {
        return current == expected;
    }

    public static boolean validChannel(String channel) {
        return CHANNELS.contains(channel);
    }
}
