package com.face.platform.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M6PublicMediaOptimizationMigrationContractTest {

    @Test
    void migrationOnlySwitchesKnownServiceImagesToOptimizedWebpAssets() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080201__optimize_public_service_media.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("UPDATE `service_item`");
        assertThat(sql).contains("upload/service-catalog/%.png");
        assertThat(sql).contains(".webp");
        assertThat(sql).doesNotContain("DROP ");
        assertThat(sql).doesNotContain("DELETE ");
        assertThat(sql).doesNotContain("ALTER TABLE");
    }
}
