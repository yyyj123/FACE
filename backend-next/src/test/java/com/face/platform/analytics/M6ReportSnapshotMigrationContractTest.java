package com.face.platform.analytics;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M6ReportSnapshotMigrationContractTest {

    @Test
    void migrationAddsImmutableIdempotentSelfScopedReportSnapshots() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080101__m6_report_snapshot.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `report_snapshot`");
        assertThat(sql).contains("uk_report_snapshot_idempotency");
        assertThat(sql).contains("idx_report_snapshot_requester");
        assertThat(sql).contains("idx_report_snapshot_expiry");
        assertThat(sql).contains("ck_report_snapshot_status");
        assertThat(sql).contains("ck_report_snapshot_ready_content");
        assertThat(sql).contains("`content_sha256`");
        assertThat(sql).contains("`request_hash`");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("UPDATE `sales_order`");
        assertThat(sql).doesNotContain("UPDATE `sales_order_item`");
    }
}
