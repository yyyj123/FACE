package com.face.platform.production;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

final class ProductionSafetyPolicy {

    private static final Duration MAX_EVIDENCE_AGE = Duration.ofDays(90);
    private final Clock clock;

    ProductionSafetyPolicy(Clock clock) {
        this.clock = clock;
    }

    void validate(Configuration configuration) {
        var profiles = Arrays.stream(configuration.activeProfiles())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
        require(profiles.contains("prod"), "Production deployment must activate the prod profile");
        require(!profiles.contains("demo"), "Production deployment cannot activate the demo profile");
        require(!configuration.demoMockEnabled(), "Production deployment cannot enable DEMO_MOCK payment");
        require(configuration.sandboxSecret() == null || configuration.sandboxSecret().isBlank(),
            "Production deployment cannot configure the SANDBOX payment secret");
        require(!"demo".equalsIgnoreCase(configuration.smsMode()),
            "Production deployment cannot use demo SMS or the fixed 888888 code");
        require(configuration.objectStorageEndpoint() != null && !configuration.objectStorageEndpoint().isBlank(),
            "Production object storage endpoint is required");
        require(configuration.objectStorageBucket() != null && !configuration.objectStorageBucket().isBlank(),
            "Production object storage bucket is required");

        validatePayment(configuration);
        validateSms(configuration);
        validateLogistics(configuration);
    }

    private void validatePayment(Configuration configuration) {
        String channel = normalize(configuration.paymentChannel());
        if ("DISABLED".equals(channel)) return;
        require(configuration.paymentAdapterConfigured(),
            "Configured production payment channel is not provided by an installed adapter");
        validateEvidence("payment", channel, configuration.adapterDirectory(), configuration.evidenceDirectory(),
            false, "sandbox", "small-payment", "refund", "reconciliation");
    }

    private void validateSms(Configuration configuration) {
        String channel = normalize(configuration.smsMode());
        if ("DISABLED".equals(channel)) return;
        require(configuration.smsAdapterConfigured(),
            "Configured production SMS channel is not provided by an installed adapter");
        validateEvidence("sms", channel, configuration.adapterDirectory(), configuration.evidenceDirectory(),
            "HTTP".equals(channel), "sandbox", "delivery-receipt");
    }

    private void validateLogistics(Configuration configuration) {
        String channel = normalize(configuration.logisticsChannel());
        if ("DISABLED".equals(channel)) return;
        validateEvidence("logistics", channel, configuration.adapterDirectory(), configuration.evidenceDirectory(),
            false, "sandbox", "create-shipment", "tracking", "cancel-shipment");
    }

    private void validateEvidence(
        String kind,
        String channel,
        Path adapterDirectory,
        Path evidenceDirectory,
        boolean builtIn,
        String... requiredResults
    ) {
        Path evidencePath = evidenceDirectory.resolve(kind + ".properties").normalize();
        require(evidencePath.startsWith(evidenceDirectory.normalize()) && Files.isRegularFile(evidencePath),
            "Required channel-evidence file is missing: " + evidencePath);
        Properties evidence = new Properties();
        try (InputStream stream = Files.newInputStream(evidencePath)) {
            evidence.load(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read channel-evidence: " + evidencePath, exception);
        }
        require(channel.equals(normalize(evidence.getProperty("channel"))),
            "Channel evidence does not match configured channel");
        Instant checkedAt;
        try {
            checkedAt = Instant.parse(evidence.getProperty("checked-at", ""));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Channel evidence checked-at is invalid", exception);
        }
        Instant now = clock.instant();
        require(!checkedAt.isAfter(now.plus(Duration.ofMinutes(5))), "Channel evidence is dated in the future");
        require(checkedAt.isAfter(now.minus(MAX_EVIDENCE_AGE)), "Channel evidence is older than 90 days");
        for (String result : requiredResults) {
            require("PASS".equalsIgnoreCase(evidence.getProperty(result)),
                "Channel evidence is not PASS: " + result);
        }

        if (builtIn) {
            require("BUILTIN".equalsIgnoreCase(evidence.getProperty("adapter.sha256")),
                "Built-in adapter evidence must declare adapter.sha256=BUILTIN");
            return;
        }
        Path jar = adapterDirectory.resolve(kind + "-" + channel + ".jar").normalize();
        require(jar.startsWith(adapterDirectory.normalize()) && Files.isRegularFile(jar),
            "Installed adapter package is missing: " + jar);
        require(sha256(jar).equalsIgnoreCase(evidence.getProperty("adapter.sha256", "")),
            "Installed adapter SHA-256 does not match channel evidence");
    }

    static String sha256(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = stream.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash adapter package: " + path, exception);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "DISABLED" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record Configuration(
        String[] activeProfiles,
        boolean demoMockEnabled,
        String sandboxSecret,
        String smsMode,
        String objectStorageEndpoint,
        String objectStorageBucket,
        String paymentChannel,
        String logisticsChannel,
        Path adapterDirectory,
        Path evidenceDirectory,
        boolean paymentAdapterConfigured,
        boolean smsAdapterConfigured
    ) {}
}
