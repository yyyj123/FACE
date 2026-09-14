package com.face.platform.masterdata;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StaffPublicProfileServiceTest {

    @Test
    void publicProfileOwnerDoesNotCreateStaffLoginAccounts() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/masterdata/StaffPublicProfileService.java"),
            StandardCharsets.UTF_8
        );
        assertThat(source).contains("avatar_url", "bio", "job_role", "level_name", "staff_service");
        assertThat(source.toUpperCase()).doesNotContain("INSERT INTO ACCOUNT");
    }
}
