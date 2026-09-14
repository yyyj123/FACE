package com.face.platform.booking;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sc3SchedulingMigrationContractTest {

    @Test
    void migrationExtendsExistingSchedulingFactsAndAddsLocksAndWaitlist() throws Exception {
        String resource = "db/migration/V2026080303__sc3_scheduling_locks_waitlist.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("SC3 Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("ALTER TABLE `SERVICE_ITEM`");
            assertThat(sql).contains("`SLOT_INTERVAL_MINUTES`");
            assertThat(sql).contains("`BUFFER_BEFORE_MINUTES`");
            assertThat(sql).contains("`MINIMUM_ADVANCE_MINUTES`");
            assertThat(sql).contains("ALTER TABLE `APPOINTMENT`");
            assertThat(sql).contains("`OCCUPIED_START_AT`");
            assertThat(sql).contains("`RULE_SNAPSHOT_JSON`");
            assertThat(sql).contains("CREATE TABLE `STAFF_SCHEDULE_RULE`");
            assertThat(sql).contains("CREATE TABLE `BOOKING_TIME_LOCK`");
            assertThat(sql).contains("CREATE TABLE `BOOKING_WAITLIST`");
            assertThat(sql).contains("CREATE TABLE `BOOKING_WAITLIST_STATUS_HISTORY`");
            assertThat(sql).contains("'HELD'", "'CONVERTED'", "'RELEASED'", "'EXPIRED'");
            assertThat(sql).contains("'WAITING'", "'WAITING_CONFIRMATION'", "'CONFIRMED'", "'INVALID'");
            assertThat(sql).doesNotContain("CREATE TABLE `APPOINTMENT`");
            assertThat(sql).doesNotContain("CREATE TABLE `SERVICE_ITEM`");
            assertThat(sql).doesNotContain("DROP TABLE");
        }
    }
}
