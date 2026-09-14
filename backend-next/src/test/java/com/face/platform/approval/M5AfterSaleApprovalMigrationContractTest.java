package com.face.platform.approval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5AfterSaleApprovalMigrationContractTest {

    @Test
    void migrationAddsAppendOnlyAfterSaleApprovalAndAdjustmentContracts() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026073006__m5_aftersale_approval.sql"),
            StandardCharsets.UTF_8
        );
        assertThat(sql).contains("CREATE TABLE `after_sale_case`");
        assertThat(sql).contains("CREATE TABLE `after_sale_case_log`");
        assertThat(sql).contains("CREATE TABLE `approval_instance`");
        assertThat(sql).contains("CREATE TABLE `approval_step`");
        assertThat(sql).contains("uk_approval_active_business");
        assertThat(sql).contains("approval_instance_id");
        assertThat(sql).contains("'commission:entry:adjust'");
        assertThat(sql).contains("'aftersale:manage'");
        assertThat(sql).contains("'approval:decide'");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `refund_transaction`");
        assertThat(sql).doesNotContain("UPDATE `sales_order`");
    }
}
