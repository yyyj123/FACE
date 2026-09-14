package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5CommissionEntryMigrationContractTest {

    @Test
    void migrationAddsAppendOnlyCommissionEntriesHistoryAndProjectionCheckpoint() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026073004__m5_commission_entry_lifecycle.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `commission_entry`");
        assertThat(sql).contains("CREATE TABLE `commission_entry_history`");
        assertThat(sql).contains("CREATE TABLE `commission_event_projection`");
        assertThat(sql).contains("uk_commission_entry_source_key");
        assertThat(sql).contains("uk_commission_entry_history_idempotency");
        assertThat(sql).contains("uk_commission_projection_outbox");
        assertThat(sql).contains("ck_commission_entry_direction");
        assertThat(sql).contains("'commission:freeze'");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `sales_order`");
        assertThat(sql).doesNotContain("UPDATE `refund_transaction`");
    }
}

