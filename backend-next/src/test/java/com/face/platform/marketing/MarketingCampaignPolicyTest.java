package com.face.platform.marketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingCampaignPolicyTest {

    @Test
    void campaignOnlyMovesForwardAndTerminalStatesAreImmutable() {
        assertThat(MarketingCampaignPolicy.canTransition("DRAFT", "PENDING_APPROVAL")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("PENDING_APPROVAL", "APPROVED")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("PENDING_APPROVAL", "REJECTED")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("APPROVED", "RUNNING")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("RUNNING", "COMPLETED")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("COMPLETED", "RUNNING")).isFalse();
        assertThat(MarketingCampaignPolicy.canTransition("CANCELLED", "DRAFT")).isFalse();
        assertThat(MarketingCampaignPolicy.canTransition("REJECTED", "PENDING_APPROVAL")).isFalse();
    }

    @Test
    void onlyPreExecutionStatesCanBeCancelled() {
        assertThat(MarketingCampaignPolicy.canTransition("DRAFT", "CANCELLED")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("PENDING_APPROVAL", "CANCELLED")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("APPROVED", "CANCELLED")).isTrue();
        assertThat(MarketingCampaignPolicy.canTransition("RUNNING", "CANCELLED")).isFalse();
    }

    @Test
    void creatorAndSubmitterCannotApproveTheirOwnCampaign() {
        assertThat(MarketingCampaignPolicy.canApprove(11L, 11L, 12L)).isFalse();
        assertThat(MarketingCampaignPolicy.canApprove(12L, 11L, 12L)).isFalse();
        assertThat(MarketingCampaignPolicy.canApprove(13L, 11L, 12L)).isTrue();
    }

    @Test
    void onlyInAppChannelIsExecutableUntilARealProviderExists() {
        assertThat(MarketingCampaignPolicy.channelAvailable("IN_APP")).isTrue();
        assertThat(MarketingCampaignPolicy.channelAvailable("SMS")).isFalse();
        assertThat(MarketingCampaignPolicy.channelAvailable("EMAIL")).isFalse();
        assertThat(MarketingCampaignPolicy.channelAvailable("WECHAT")).isFalse();
    }
}
