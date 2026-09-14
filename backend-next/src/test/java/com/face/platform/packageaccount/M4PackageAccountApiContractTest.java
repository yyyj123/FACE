package com.face.platform.packageaccount;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4PackageAccountApiContractTest {

    @Test
    void controllerExposesVersionedIdempotentPackageAndAccountActions() throws Exception {
        String java = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/packageaccount/V3PackageAccountController.java"),
            StandardCharsets.UTF_8
        );

        assertThat(java).contains("@RequestMapping(\"/api/v3\")");
        assertThat(java).contains("@GetMapping(\"/package-products\")");
        assertThat(java).contains("@PostMapping(\"/package-instances/{instanceId}/write-offs\")");
        assertThat(java).contains("@PostMapping(\"/package-ledger/{ledgerId}/reversals\")");
        assertThat(java).contains("@GetMapping(\"/members/{memberId}/accounts\")");
        assertThat(java).contains("@PostMapping(\"/member-accounts/{accountId}/credits\")");
        assertThat(java).contains("@PostMapping(\"/member-accounts/{accountId}/debits\")");
        assertThat(java).contains("@PostMapping(\"/member-account-ledger/{ledgerId}/reversals\")");
        assertThat(java).contains("Idempotency-Key");
        assertThat(java).contains("version");
    }
}

