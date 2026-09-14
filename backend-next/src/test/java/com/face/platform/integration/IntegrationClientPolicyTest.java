package com.face.platform.integration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationClientPolicyTest {

    @Test
    void onlyApprovedLowSensitivityScopeAndBoundShopAreAllowed() {
        assertThat(IntegrationClientPolicy.validScopes(List.of("catalog:read"))).isTrue();
        assertThat(IntegrationClientPolicy.validScopes(List.of("member:read"))).isFalse();
        assertThat(IntegrationClientPolicy.validScopes(List.of("catalog:read", "payment:read"))).isFalse();
        assertThat(IntegrationClientPolicy.shopAllowed(7L, 7L)).isTrue();
        assertThat(IntegrationClientPolicy.shopAllowed(7L, 8L)).isFalse();
    }

    @Test
    void requestTimestampMustBeWithinFiveMinutesAndNonceMustBeSafe() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        assertThat(IntegrationClientPolicy.timestampAllowed(now, now.minusSeconds(299))).isTrue();
        assertThat(IntegrationClientPolicy.timestampAllowed(now, now.plusSeconds(300))).isTrue();
        assertThat(IntegrationClientPolicy.timestampAllowed(now, now.minusSeconds(301))).isFalse();
        assertThat(IntegrationClientPolicy.validNonce("nonce_20260801-001")).isTrue();
        assertThat(IntegrationClientPolicy.validNonce("含敏感文本")).isFalse();
        assertThat(IntegrationClientPolicy.validNonce("short")).isFalse();
    }
}
