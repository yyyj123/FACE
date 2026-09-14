package com.face.platform.purchase;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class M4PurchaseBatchMigrationContractTest {

    @Test
    void migrationAddsPurchaseReceiptBatchLedgerAndOpeningBalanceWithoutDestructiveChanges()
        throws Exception {
        String resource = "db/migration/V2026073001__m4_purchase_batch_inventory.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("M4 purchase/batch Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("CREATE TABLE `PURCHASE_ORDER`");
            assertThat(sql).contains("CREATE TABLE `PURCHASE_ORDER_ITEM`");
            assertThat(sql).contains("CREATE TABLE `PURCHASE_RECEIPT`");
            assertThat(sql).contains("CREATE TABLE `PURCHASE_RECEIPT_ITEM`");
            assertThat(sql).contains("CREATE TABLE `STOCK_BATCH`");
            assertThat(sql).contains("CREATE TABLE `STOCK_BATCH_MOVEMENT`");
            assertThat(sql).contains("UK_PURCHASE_RECEIPT_IDEMPOTENCY");
            assertThat(sql).contains("IDX_STOCK_BATCH_FEFO");
            assertThat(sql).contains("UK_STOCK_BATCH_MOVEMENT_BUSINESS");
            assertThat(sql).contains("MIGRATION_OPENING");
            assertThat(sql).contains("'PURCHASE:VIEW'");
            assertThat(sql).contains("'PURCHASE:MANAGE'");
            assertThat(sql).contains("'PURCHASE:APPROVE'");
            assertThat(sql).contains("'PURCHASE:RECEIVE'");
            assertThat(sql).doesNotContain("DROP TABLE");
            assertThat(sql).doesNotContain("DELETE FROM");
            assertThat(sql).doesNotContain("UPDATE `STOCK_BALANCE`");
        }
    }
}
