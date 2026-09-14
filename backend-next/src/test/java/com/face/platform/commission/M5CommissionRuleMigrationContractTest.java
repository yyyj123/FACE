package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5CommissionRuleMigrationContractTest {

    @Test
    void migrationAddsVersionedRulesScopesSnapshotsAndPermissionsWithoutRewritingHistory()
        throws Exception {
        String sql = Files.readString(
            Path.of(
                "src/main/resources/db/migration/"
                    + "V2026073003__m5_commission_rule_versioning.sql"
            ),
            StandardCharsets.UTF_8
        );

        assertThat(sql).contains("CREATE TABLE `commission_rule_version`");
        assertThat(sql).contains("CREATE TABLE `commission_rule_scope`");
        assertThat(sql).contains("CREATE TABLE `commission_source_snapshot`");
        assertThat(sql).contains("uk_commission_rule_version");
        assertThat(sql).contains("uk_commission_rule_scope");
        assertThat(sql).contains("uk_commission_source_snapshot");
        assertThat(sql).contains("ck_commission_rule_status");
        assertThat(sql).contains("snapshot_hash");
        assertThat(sql).contains("'commission:rule:view'");
        assertThat(sql).contains("'commission:rule:manage'");
        assertThat(sql).doesNotContain("UPDATE `sales_order`");
        assertThat(sql).doesNotContain("UPDATE `service_record`");
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("DROP TABLE");
    }
}
