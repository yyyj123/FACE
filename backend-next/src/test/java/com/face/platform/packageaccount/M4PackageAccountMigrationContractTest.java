package com.face.platform.packageaccount;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4PackageAccountMigrationContractTest {

    @Test
    void migrationAddsPackageLedgerConstraintsWithoutFabricatingMemberEntitlements() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026072901__m4_package_account_lifecycle.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `package_product`");
        assertThat(sql).contains("CREATE TABLE `package_product_item`");
        assertThat(sql).contains("CREATE TABLE `package_instance`");
        assertThat(sql).contains("CREATE TABLE `package_ledger`");
        assertThat(sql).contains("uk_package_ledger_idempotency");
        assertThat(sql).contains("uk_package_ledger_reversal");
        assertThat(sql).contains("ck_package_instance_balance");
        assertThat(sql).contains("reversal_of_ledger_id");
        assertThat(sql).contains("request_hash");
        assertThat(sql).contains("'package:writeoff'");
        assertThat(sql).contains("'account:manage'");
        assertThat(sql).doesNotContain("INSERT INTO `package_instance`");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("DROP TABLE");
    }
}

