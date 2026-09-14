package com.face.platform.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5LeastPrivilegeVerificationContractTest {

    @Test
    void m2AcceptanceFixtureMustRemoveTemporaryManagerRoleBeforeLaterPhaseChecks() throws Exception {
        String script = Files.readString(
            Path.of("../scripts/verify-m1-v3-security.ps1"),
            StandardCharsets.UTF_8
        );

        assertThat(script).contains("M2_TEMP_MANAGER_ROLE_CLEANUP=PASS");
        assertThat(script).contains("DELETE ar");
        assertThat(script).contains("a.username = 'jishi01'");
        assertThat(script).contains("r.role_code = 'MANAGER'");
    }
}
