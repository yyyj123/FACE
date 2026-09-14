package com.face.platform.servicecare;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class M3MigrationContractTest {

    @Test
    void migrationAddsConfirmationCorrectionsOrderIdempotencyAndPermissionsWithoutDestructiveChanges()
        throws Exception {
        String resource = "db/migration/V2026072804__m3_vertical_service_flow.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("M3 Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("CREATE TABLE `CUSTOMER_CONFIRMATION`");
            assertThat(sql).contains("CREATE TABLE `SERVICE_RECORD_CORRECTION`");
            assertThat(sql).contains("UK_CUSTOMER_CONFIRMATION_RECORD_TYPE");
            assertThat(sql).contains("IDX_CUSTOMER_CONFIRMATION_MEMBER_STATUS");
            assertThat(sql).contains("UK_SERVICE_CORRECTION_IDEMPOTENCY");
            assertThat(sql).contains("ADD COLUMN `CREATE_IDEMPOTENCY_KEY`");
            assertThat(sql).contains("ADD COLUMN `CREATE_REQUEST_HASH`");
            assertThat(sql).contains("UK_SALES_ORDER_CREATE_IDEMPOTENCY");
            assertThat(sql).contains("ADD COLUMN `COMPLETION_REQUEST_HASH`");
            assertThat(sql).contains("ADD COLUMN `REQUEST_HASH`");
            assertThat(sql).contains("'SERVICE_RECORD:CORRECT'");
            assertThat(sql).contains("'CUSTOMER_CONFIRMATION:VIEW'");
            assertThat(sql).doesNotContain("DROP TABLE");
            assertThat(sql).doesNotContain("DROP COLUMN");
            assertThat(sql).doesNotContain("DELETE FROM");
        }
    }
}
