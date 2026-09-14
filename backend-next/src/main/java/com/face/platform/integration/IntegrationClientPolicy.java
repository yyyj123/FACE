package com.face.platform.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class IntegrationClientPolicy {

    public static final Set<String> AVAILABLE_SCOPES = Set.of("catalog:read");
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(5);

    private IntegrationClientPolicy() {
    }

    public static boolean validScopes(List<String> scopes) {
        return scopes != null
            && !scopes.isEmpty()
            && scopes.stream().allMatch(AVAILABLE_SCOPES::contains)
            && scopes.stream().distinct().count() == scopes.size();
    }

    public static boolean shopAllowed(long boundShopId, long requestedShopId) {
        return boundShopId == requestedShopId;
    }

    public static boolean timestampAllowed(Instant now, Instant supplied) {
        if (now == null || supplied == null) return false;
        return Duration.between(now, supplied).abs().compareTo(REQUEST_WINDOW) <= 0;
    }

    public static boolean validNonce(String nonce) {
        return nonce != null
            && nonce.length() >= 12
            && nonce.length() <= 80
            && nonce.matches("[A-Za-z0-9._:-]+");
    }
}
