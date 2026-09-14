package com.face.platform.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Sc2DefaultShopBoundaryContractTest {

    @Test
    void publicControllerDoesNotDefaultToShopOneAndAppointmentUsesServerContext() throws Exception {
        String controller = Files.readString(
            Path.of("src/main/java/com/face/platform/client/ClientController.java"),
            StandardCharsets.UTF_8
        );
        String portal = Files.readString(
            Path.of("src/main/java/com/face/platform/client/ClientPortalService.java"),
            StandardCharsets.UTF_8
        );
        String frontend = Files.readString(
            Path.of("../front-next/src/api/client.ts"),
            StandardCharsets.UTF_8
        );

        assertThat(controller).doesNotContain("defaultValue = \"1\"");
        assertThat(controller).contains("shopContextService.requirePublicShop");
        assertThat(portal).contains("shopContextService.requireTenantShop");
        assertThat(frontend).doesNotContain("shopId: 1");
    }
}
