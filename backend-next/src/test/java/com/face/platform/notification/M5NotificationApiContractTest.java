package com.face.platform.notification;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5NotificationApiContractTest {

    @Test
    void controllerExposesOnlySelfScopedReadContracts() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/notification/V3NotificationController.java"),
            StandardCharsets.UTF_8
        );
        assertThat(source).contains("@RequestMapping(\"/api/v3/notifications\")");
        assertThat(source).contains("@GetMapping");
        assertThat(source).contains("@PostMapping(\"/{notificationId}/read\")");
        assertThat(source).contains("@PostMapping(\"/read-all\")");
        assertThat(source).contains("version");
        assertThat(source).contains("Idempotency-Key");
        assertThat(source).doesNotContain("recipient_account_id");
        assertThat(source).doesNotContain("recipientAccountId");
    }
}
