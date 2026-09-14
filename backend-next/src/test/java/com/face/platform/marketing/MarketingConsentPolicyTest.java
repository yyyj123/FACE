package com.face.platform.marketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingConsentPolicyTest {

    @Test
    void missingConsentIsDeniedAndOnlyExplicitGrantIsEligible() {
        assertThat(MarketingConsentPolicy.isEligible(null)).isFalse();
        assertThat(MarketingConsentPolicy.isEligible("REVOKED")).isFalse();
        assertThat(MarketingConsentPolicy.isEligible("GRANTED")).isTrue();
    }

    @Test
    void consentUsesOptimisticVersionAndKnownChannelsOnly() {
        assertThat(MarketingConsentPolicy.versionMatches(3, 3)).isTrue();
        assertThat(MarketingConsentPolicy.versionMatches(3, 2)).isFalse();
        assertThat(MarketingConsentPolicy.validChannel("IN_APP")).isTrue();
        assertThat(MarketingConsentPolicy.validChannel("SMS")).isTrue();
        assertThat(MarketingConsentPolicy.validChannel("EMAIL")).isTrue();
        assertThat(MarketingConsentPolicy.validChannel("WECHAT")).isTrue();
        assertThat(MarketingConsentPolicy.validChannel("PUSH")).isFalse();
    }
}
