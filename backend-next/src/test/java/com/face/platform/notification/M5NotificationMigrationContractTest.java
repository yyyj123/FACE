package com.face.platform.notification;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5NotificationMigrationContractTest {

    @Test
    void migrationAddsIdempotentSelfScopedNotificationProjection() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026073007__m5_notification.sql"),
            StandardCharsets.UTF_8
        );
        assertThat(sql).contains("CREATE TABLE `notification_message`");
        assertThat(sql).contains("CREATE TABLE `notification_projection_checkpoint`");
        assertThat(sql).contains("uk_notification_event_recipient_channel");
        assertThat(sql).contains("idx_notification_recipient_status");
        assertThat(sql).contains("idx_notification_projection_retry");
        assertThat(sql).contains("ck_notification_status");
        assertThat(sql).contains("ck_notification_external_status");
        assertThat(sql).contains("'notification:view:self'");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `outbox_event`");
    }
}
