package com.face.platform.v3.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V3AdminAccountApiContractTest {

    @Test
    void superAdministratorCanListCreateDeactivateAndResetVisibleAdminAccounts() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/identity/V3AdminAccountController.java"),
            StandardCharsets.UTF_8
        );
        assertThat(source).contains(
            "@RequestMapping(\"/api/v3/admin-accounts\")",
            "@GetMapping",
            "@PostMapping",
            "@PostMapping(\"/{accountId}/deactivate\")",
            "@PostMapping(\"/{accountId}/reset-password\")"
        );
    }
}
