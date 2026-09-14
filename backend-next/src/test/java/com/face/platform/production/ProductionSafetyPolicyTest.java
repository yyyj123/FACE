package com.face.platform.production;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSafetyPolicyTest {

    private final ProductionSafetyPolicy policy = new ProductionSafetyPolicy(
        Clock.fixed(Instant.parse("2026-08-10T05:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void acceptsProductionWithAllExternalChannelsDisabled(@TempDir Path root) {
        assertDoesNotThrow(() -> policy.validate(configuration(root, "disabled", false, "disabled")));
    }

    @Test
    void rejectsDemoSmsMockPaymentAndSandbox(@TempDir Path root) {
        var demoSms = configuration(root, "disabled", false, "demo");
        var demoPayment = new ProductionSafetyPolicy.Configuration(
            new String[]{"prod"}, true, "", "disabled", "http://object-store:9000", "face-production",
            "disabled", "disabled", root.resolve("adapters"), root.resolve("evidence"), false, false
        );
        var sandbox = new ProductionSafetyPolicy.Configuration(
            new String[]{"prod"}, false, "sandbox-secret", "disabled", "http://object-store:9000", "face-production",
            "disabled", "disabled", root.resolve("adapters"), root.resolve("evidence"), false, false
        );

        assertThrows(IllegalStateException.class, () -> policy.validate(demoSms));
        assertThrows(IllegalStateException.class, () -> policy.validate(demoPayment));
        assertThrows(IllegalStateException.class, () -> policy.validate(sandbox));
    }

    @Test
    void enabledPaymentRequiresInstalledAdapterAndCompleteEvidence(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("adapters"));
        Files.createDirectories(root.resolve("evidence"));
        var enabled = configuration(root, "WECHAT", true, "disabled");

        assertThrows(IllegalStateException.class, () -> policy.validate(enabled));

        Path jar = root.resolve("adapters/payment-WECHAT.jar");
        Files.writeString(jar, "synthetic adapter fixture");
        String digest = ProductionSafetyPolicy.sha256(jar);
        Files.writeString(root.resolve("evidence/payment.properties"), """
            channel=WECHAT
            adapter.sha256=%s
            checked-at=2026-08-09T05:00:00Z
            sandbox=PASS
            small-payment=PASS
            refund=PASS
            reconciliation=PASS
            """.formatted(digest));

        assertDoesNotThrow(() -> policy.validate(enabled));
    }

    private ProductionSafetyPolicy.Configuration configuration(
        Path root,
        String payment,
        boolean paymentConfigured,
        String smsMode
    ) {
        return new ProductionSafetyPolicy.Configuration(
            new String[]{"prod"}, false, "", smsMode, "http://object-store:9000", "face-production",
            payment, "disabled", root.resolve("adapters"), root.resolve("evidence"),
            paymentConfigured, false
        );
    }
}
