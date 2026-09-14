package com.face.platform.analytics;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminOperationsOverviewPermissionMigrationContractTest {

    @Test
    void grantsReadOnlyAnalyticsToVisibleAdminRolesWithoutGrantingExport() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V2026081801__admin_operations_overview_permission.sql"
        ));

        assertThat(sql).contains("analytics:view");
        assertThat(sql).contains("'ADMIN', 'SUPER_ADMIN'");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).doesNotContain("analytics:export");
    }
}
