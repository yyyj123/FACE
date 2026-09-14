package com.face.platform.v3.content;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Sc2ContentApiContractTest {

    @Test
    void exposesPublicHomeAndAdministratorLifecycleEndpoints() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/content/V3ContentController.java"),
            StandardCharsets.UTF_8
        );
        assertThat(source).contains(
            "@GetMapping(\"/api/v3/open/content/home\")",
            "@GetMapping(\"/api/v3/content\")",
            "@PostMapping(\"/api/v3/content\")",
            "@PutMapping(\"/api/v3/content/{contentId}\")",
            "@PostMapping(\"/api/v3/content/{contentId}/status\")"
        );
        assertThat(source).contains("V3ApiResponse");
    }
}
