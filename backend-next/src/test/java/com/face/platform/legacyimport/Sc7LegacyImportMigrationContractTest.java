package com.face.platform.legacyimport;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sc7LegacyImportMigrationContractTest {

    @Test
    void migrationLocksIdempotencyPermissionsAndImmutableLegacyLedgers() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080313__sc7_legacy_import.sql"),
            StandardCharsets.UTF_8
        );
        assertTrue(sql.contains("uk_legacy_import_file"));
        assertTrue(sql.contains("source_row_number"));
        assertTrue(sql.contains("uk_legacy_member_source"));
        assertTrue(sql.contains("uk_package_legacy_card"));
        assertTrue(sql.contains("'LEGACY_IMPORT'"));
        assertTrue(sql.contains("'import:execute'"));
        assertTrue(sql.contains("r.`role_code` = 'SUPER_ADMIN'"));
        assertFalse(sql.contains("2026080103"));
    }
}
