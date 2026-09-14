package com.face.platform.identity;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sc2IdentityContentMigrationContractTest {

    @Test
    void migrationReusesAccountMemberAndBannerWithoutDestructiveHistoryChanges() throws Exception {
        String resource = "db/migration/V2026080301__sc2_identity_content_single_shop.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("SC2 Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("ALTER TABLE `ACCOUNT`");
            assertThat(sql).contains("ADD COLUMN `PHONE`");
            assertThat(sql).contains("CREATE TABLE `SMS_VERIFICATION_CHALLENGE`");
            assertThat(sql).contains("ALTER TABLE `BANNER`");
            assertThat(sql).contains("ADD COLUMN `CONTENT_TYPE`");
            assertThat(sql).contains("'DRAFT'", "'SCHEDULED'", "'PUBLISHED'", "'OFFLINE'");
            assertThat(sql).contains("'ADMIN'", "'SUPER_ADMIN'");
            assertThat(sql).contains("'CONTENT:VIEW'", "'CONTENT:MANAGE'", "'CONTENT:PUBLISH'");
            assertThat(sql).doesNotContain("CREATE TABLE `MEMBER`");
            assertThat(sql).doesNotContain("CREATE TABLE `CONTENT_ENTRY`");
            assertThat(sql).doesNotContain("DROP TABLE");
        }
    }

    @Test
    void appendOnlyRepairBackfillsPublishedBannerTimestamp() throws Exception {
        String resource = "db/migration/V2026080302__sc2_banner_publish_timestamp_backfill.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("SC2 append-only banner timestamp repair").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("UPDATE `BANNER`");
            assertThat(sql).contains("`STATUS` = 'PUBLISHED'");
            assertThat(sql).contains("`PUBLISHED_AT` IS NULL");
            assertThat(sql).contains("COALESCE(`START_AT`, `CREATED_AT`)");
            assertThat(sql).doesNotContain("ALTER TABLE", "DROP TABLE");
        }
    }
}
