package com.face.platform.reconciliation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4PaymentReconciliationMigrationContractTest {

    @Test
    void migrationAddsCallbackReconciliationConstraintsAndPermissions() throws Exception {
        String sql = Files.readString(
            Path.of(
                "src/main/resources/db/migration/"
                    + "V2026073002__m4_payment_adapter_reconciliation.sql"
            ),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `payment_callback_event`");
        assertThat(sql).contains("CREATE TABLE `reconciliation_batch`");
        assertThat(sql).contains("CREATE TABLE `reconciliation_item`");
        assertThat(sql).contains("CREATE TABLE `reconciliation_resolution`");
        assertThat(sql).contains("uk_payment_callback_event");
        assertThat(sql).contains("uk_reconciliation_scope_date");
        assertThat(sql).contains("'reconciliation:view'");
        assertThat(sql).contains("'reconciliation:manage'");
        assertThat(sql).doesNotContain("DROP TABLE");
    }
}
