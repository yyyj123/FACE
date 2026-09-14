package com.face.platform.resource;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceBookingConcurrencyContractTest {

    @Test
    void overlapCheckUsesLockingCurrentReadAfterResourceLock() throws Exception {
        Path source = Path.of(
            "src/main/java/com/face/platform/resource/ResourceBookingService.java"
        );
        String java = Files.readString(source, StandardCharsets.UTF_8)
            .toUpperCase()
            .replaceAll("\\s+", " ");

        assertThat(java).contains(
            "SELECT ID FROM RESOURCE_BOOKING "
                + "WHERE TENANT_ID = ? AND RESOURCE_ID = ? AND STATUS = 'RESERVED' "
                + "AND START_AT < ? AND END_AT > ? AND APPOINTMENT_ID <> ? FOR UPDATE"
        );
        assertThat(java).doesNotContain(
            "SELECT COUNT(*) FROM RESOURCE_BOOKING "
                + "WHERE TENANT_ID = ? AND RESOURCE_ID = ? AND STATUS = 'RESERVED'"
        );
    }
}
