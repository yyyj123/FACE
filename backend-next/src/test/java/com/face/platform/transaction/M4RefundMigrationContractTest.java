package com.face.platform.transaction;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4RefundMigrationContractTest {

    @Test
    void migrationSeparatesRequestApprovalAndExecutionWithoutRewritingHistory() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026072902__m4_refund_lifecycle.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("ADD COLUMN `request_hash`");
        assertThat(sql).contains("ADD COLUMN `execution_idempotency_key`");
        assertThat(sql).contains("ADD COLUMN `execution_request_hash`");
        assertThat(sql).contains("ADD COLUMN `executed_by`");
        assertThat(sql).contains("'PROCESSING'");
        assertThat(sql).contains("uk_refund_execution_idempotency");
        assertThat(sql).contains("'refund:request'");
        assertThat(sql).contains("'refund:execute'");
        assertThat(sql).doesNotContain("UPDATE `payment_transaction` SET `amount`");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("DROP TABLE");
    }
}
