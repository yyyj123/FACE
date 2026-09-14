package com.face.platform.booking;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HourlyBookingSlotMigrationContractTest {

    @Test
    void migrationAlignsExistingAndFutureServicesToHourlyStarts() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
            "/db/migration/V2026081901__align_booking_slot_interval_hourly.sql"
        )) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("SET `SLOT_INTERVAL_MINUTES` = 60");
            assertThat(sql).contains("`BOOKING_TERMS_VERSION` = `BOOKING_TERMS_VERSION` + 1");
            assertThat(sql).contains("DEFAULT 60");
        }
    }

    @Test
    void showcaseSeedCannotReintroduceHalfHourStartsAfterMigration() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/demo/showcase-seed.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("'DEMO-SVC-01'");
            assertThat(sql).contains("BOOKING_TERMS_VERSION = BOOKING_TERMS_VERSION + IF(SLOT_INTERVAL_MINUTES <> 60, 1, 0)");
            assertThat(sql).contains("SLOT_INTERVAL_MINUTES = 60");
            assertThat(sql).doesNotContain("SLOT_INTERVAL_MINUTES = 30");
        }
    }
}
