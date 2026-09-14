package com.face.platform.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M6TrainingOpenPlatformMigrationContractTest {

    @Test
    void migrationAddsVersionedTrainingAndSecretSafeIntegrationLedgers() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080103__m6_training_open_platform.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `training_course`");
        assertThat(sql).contains("CREATE TABLE `training_record`");
        assertThat(sql).contains("CREATE TABLE `training_record_history`");
        assertThat(sql).contains("CREATE TABLE `integration_client`");
        assertThat(sql).contains("CREATE TABLE `integration_client_event`");
        assertThat(sql).contains("CREATE TABLE `integration_request_log`");
        assertThat(sql).contains("secret_hash");
        assertThat(sql).doesNotContain("secret_plaintext");
        assertThat(sql).contains("uk_training_course_revision");
        assertThat(sql).contains("uk_training_record_assign_idempotency");
        assertThat(sql).contains("uk_training_history_idempotency");
        assertThat(sql).contains("uk_integration_client_code");
        assertThat(sql).contains("uk_integration_event_idempotency");
        assertThat(sql).contains("uk_integration_request_nonce");
        assertThat(sql).contains("ck_training_record_status");
        assertThat(sql).contains("ck_integration_client_status");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `member`");
        assertThat(sql).doesNotContain("UPDATE `payment_transaction`");
    }
}
