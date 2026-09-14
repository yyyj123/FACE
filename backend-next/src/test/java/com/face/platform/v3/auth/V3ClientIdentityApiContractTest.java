package com.face.platform.v3.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V3ClientIdentityApiContractTest {

    @Test
    void clientIdentityExposesPasswordSmsRegistrationAndResetFlows() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/identity/V3ClientIdentityController.java"),
            StandardCharsets.UTF_8
        );

        assertThat(source).contains(
            "@PostMapping(\"/sms/request\")",
            "@PostMapping(\"/password-login\")",
            "@PostMapping(\"/sms-login\")",
            "@PostMapping(\"/register\")",
            "@PostMapping(\"/password-reset\")"
        );
        assertThat(source).contains("V3ApiResponse");
    }

    @Test
    void identityResponseCarriesThePersistedAccountId() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/identity/IdentityApplicationService.java"),
            StandardCharsets.UTF_8
        );

        assertThat(source).contains("id AS accountId");
        assertThat(source).contains("data.put(\"account_id\", accountId)");
    }

    @Test
    void demoAdapterIsProfileIsolatedAndProductionAdapterNeverReturnsFixedCode() throws Exception {
        String demo = Files.readString(
            Path.of("src/main/java/com/face/platform/identity/DemoSmsDeliveryAdapter.java"),
            StandardCharsets.UTF_8
        );
        String disabled = Files.readString(
            Path.of("src/main/java/com/face/platform/identity/DisabledSmsDeliveryAdapter.java"),
            StandardCharsets.UTF_8
        );

        assertThat(demo).contains("@Profile(\"demo\")", "888888");
        assertThat(disabled).contains("@Profile(\"!demo\")");
        assertThat(disabled).doesNotContain("888888");
    }
}
