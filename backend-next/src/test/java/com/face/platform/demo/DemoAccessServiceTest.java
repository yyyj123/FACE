package com.face.platform.demo;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAccessServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void createsSignedTwelveHourTokenAndRejectsTamperingOrExpiry() {
        DemoAccessService service = serviceAt(NOW);
        String token = service.createToken();

        assertTrue(service.tokenValid(token));
        assertFalse(service.tokenValid(token + "tampered"));
        assertFalse(serviceAt(NOW.plus(DemoAccessService.SESSION_TTL)).tokenValid(token));
    }

    @Test
    void comparesConfiguredPasswordAndRejectsWeakConfiguration() {
        DemoAccessService service = serviceAt(NOW);
        assertTrue(service.passwordMatches("DemoAccess#2026"));
        assertFalse(service.passwordMatches("DemoAccess#2025"));
        assertThrows(IllegalStateException.class, () -> new DemoAccessService(
            "short", "01234567890123456789012345678901", Clock.systemUTC()
        ));
        assertThrows(IllegalStateException.class, () -> new DemoAccessService(
            "DemoAccess#2026", "short", Clock.systemUTC()
        ));
    }

    private DemoAccessService serviceAt(Instant instant) {
        return new DemoAccessService(
            "DemoAccess#2026",
            "01234567890123456789012345678901",
            Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
