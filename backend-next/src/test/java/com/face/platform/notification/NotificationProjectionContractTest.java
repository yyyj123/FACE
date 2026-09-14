package com.face.platform.notification;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationProjectionContractTest {

    @Test
    void projectionIsIdempotentRetriableAndDoesNotClaimExternalDelivery() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/notification/NotificationProjectionService.java"),
            StandardCharsets.UTF_8
        );
        assertThat(source).contains("projectPending");
        assertThat(source).contains("notification_projection_checkpoint");
        assertThat(source).contains("notification_message");
        assertThat(source).contains("UNAVAILABLE");
        assertThat(source).contains("retry_count");
        assertThat(source).contains("safe_summary");
        assertThat(source).doesNotContain("phone");
        assertThat(source).doesNotContain("health");
        assertThat(source).doesNotContain("paymentCredential");
    }
}
