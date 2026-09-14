package com.face.platform.marketing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M6MarketingMigrationContractTest {

    @Test
    void migrationAddsAuditableConsentCampaignAudienceAndDeliveryLedgers() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080102__m6_marketing_governance.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `marketing_campaign`");
        assertThat(sql).contains("CREATE TABLE `marketing_campaign_status_history`");
        assertThat(sql).contains("CREATE TABLE `member_marketing_consent`");
        assertThat(sql).contains("CREATE TABLE `member_marketing_consent_history`");
        assertThat(sql).contains("CREATE TABLE `marketing_campaign_audience`");
        assertThat(sql).contains("CREATE TABLE `marketing_delivery_attempt`");
        assertThat(sql).contains("uk_marketing_campaign_create_idempotency");
        assertThat(sql).contains("uk_marketing_status_idempotency");
        assertThat(sql).contains("uk_member_marketing_consent");
        assertThat(sql).contains("uk_member_marketing_consent_history_idempotency");
        assertThat(sql).contains("uk_marketing_audience_member");
        assertThat(sql).contains("uk_marketing_delivery_member_channel");
        assertThat(sql).contains("ck_marketing_campaign_status");
        assertThat(sql).contains("ck_marketing_campaign_channel");
        assertThat(sql).contains("ck_member_marketing_consent_status");
        assertThat(sql).contains("'MARKETING'");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `member` SET");
        assertThat(sql).doesNotContain("UPDATE `sales_order`");
        assertThat(sql).doesNotContain("UPDATE `payment_transaction`");
    }
}
