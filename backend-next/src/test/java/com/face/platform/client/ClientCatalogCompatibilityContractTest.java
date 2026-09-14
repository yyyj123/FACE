package com.face.platform.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClientCatalogCompatibilityContractTest {

    @Test
    void publicCatalogDoesNotDependOnLegacyStoreupTable() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/client/ClientCatalogService.java"),
            StandardCharsets.UTF_8
        ).toUpperCase().replaceAll("\\s+", " ");

        assertThat(source).doesNotContain("FROM STOREUP");
        assertThat(source).contains("0 AS STOREUPNUM");
    }
}
