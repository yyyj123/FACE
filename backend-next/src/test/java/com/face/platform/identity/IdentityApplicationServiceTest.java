package com.face.platform.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationServiceTest {

    @Test
    void identityUsesMemberOwnerPortAndNeverCreatesDuplicateMemberDirectly() throws Exception {
        Path path = Path.of("src/main/java/com/face/platform/identity/IdentityApplicationService.java");
        assertThat(path).exists();
        String source = Files.readString(path, StandardCharsets.UTF_8);

        assertThat(source).contains("MemberService", "attachOnlineIdentity");
        assertThat(source).contains("smsVerificationService.consume");
        assertThat(source).contains("uk_account_tenant_phone");
        assertThat(source.toUpperCase()).doesNotContain("INSERT INTO MEMBER (");
    }
}
