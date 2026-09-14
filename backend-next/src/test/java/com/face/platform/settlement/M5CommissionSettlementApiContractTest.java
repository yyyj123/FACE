package com.face.platform.settlement;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5CommissionSettlementApiContractTest {

    @Test
    void controllerExposesSettlementLifecycleWithVersionAndIdempotency() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/commission/V3CommissionSettlementController.java"),
            StandardCharsets.UTF_8
        );

        assertThat(source).contains("@GetMapping(\"/settlements\")");
        assertThat(source).contains("@PostMapping(\"/settlements\")");
        assertThat(source).contains("@GetMapping(\"/settlements/{batchId}\")");
        assertThat(source).contains("@PostMapping(\"/settlements/{batchId}/calculate\")");
        assertThat(source).contains("@PostMapping(\"/settlements/{batchId}/confirm\")");
        assertThat(source).contains("@PostMapping(\"/settlements/{batchId}/mark-paid\")");
        assertThat(source).contains("@PostMapping(\"/settlements/{batchId}/close\")");
        assertThat(source).contains("@PostMapping(\"/settlements/{batchId}/void\")");
        assertThat(source).contains("@GetMapping(\"/technician/commission-summary\")");
        assertThat(source).contains("Idempotency-Key");
        assertThat(source).contains("version");
    }
}
