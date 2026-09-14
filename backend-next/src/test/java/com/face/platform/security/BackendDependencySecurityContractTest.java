package com.face.platform.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendDependencySecurityContractTest {

    @Test
    void pinsJacksonBomToTheFirstPatchedReleaseForGhsa5gvwP9qmJgwh() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(
            pom.contains("<jackson-bom.version>3.1.5</jackson-bom.version>"),
            "GHSA-5gvw-p9qm-jgwh requires tools.jackson.core:jackson-databind 3.1.5 or later"
        );
    }
}
