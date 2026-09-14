package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5CommissionEntryApiContractTest {

    @Test
    void controllerExposesReadFreezeAndUnfreezeWithoutPublicRawReversal() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/commission/V3CommissionEntryController.java"),
            StandardCharsets.UTF_8
        );

        assertThat(source).contains("@GetMapping(\"/entries\")");
        assertThat(source).contains("@GetMapping(\"/entries/{entryId}\")");
        assertThat(source).contains("@PostMapping(\"/entries/{entryId}/freeze\")");
        assertThat(source).contains("@PostMapping(\"/entries/{entryId}/unfreeze\")");
        assertThat(source).contains("Idempotency-Key");
        assertThat(source).contains("version");
        assertThat(source).doesNotContain("@PostMapping(\"/entries/{entryId}/reversals\")");
        assertThat(source).doesNotContain("UPDATE commission_entry SET amount");
    }
}

