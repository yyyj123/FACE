package com.face.platform.settlement;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5CommissionSettlementMigrationContractTest {

    @Test
    void migrationAddsImmutableSettlementBatchAndActiveEntryUniqueness() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026073005__m5_commission_settlement.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `commission_settlement_batch`");
        assertThat(sql).contains("CREATE TABLE `commission_settlement_item`");
        assertThat(sql).contains("active_entry_id");
        assertThat(sql).contains("uk_commission_settlement_active_entry");
        assertThat(sql).contains("ck_commission_settlement_period");
        assertThat(sql).contains("'commission:settlement:view'");
        assertThat(sql).contains("'commission:settlement:manage'");
        assertThat(sql).contains("'commission:settlement:approve'");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `sales_order`");
        assertThat(sql).doesNotContain("UPDATE `payment_transaction`");
    }
}
