package com.face.platform.masterdata;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class M2MigrationContractTest {

    @Test
    void migrationAddsVersionedSkillsResourcesBookingsScheduleGuardsAndPermissions() throws Exception {
        String resource = "db/migration/V2026072803__m2_master_data_and_resources.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("M2 Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS STAFF_SKILL_VERSION");
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS SERVICE_RESOURCE");
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS RESOURCE_BOOKING");
            assertThat(sql).contains("UNIQUE KEY UK_STAFF_SKILL_CURRENT");
            assertThat(sql).contains("KEY IDX_RESOURCE_BOOKING_OVERLAP");
            assertThat(sql).contains("COLUMN_NAME = 'STATUS'");
            assertThat(sql).contains("COLUMN_NAME = 'VERSION'");
            assertThat(sql).contains("TABLE_NAME = 'SERVICE_ITEM'");
            assertThat(sql).contains("'RESOURCE:VIEW'");
            assertThat(sql).contains("'RESOURCE:MANAGE'");
            assertThat(sql).contains("INSERT IGNORE INTO STAFF_SKILL_VERSION");
            assertThat(sql).doesNotContain("DROP TABLE");
            assertThat(sql).doesNotContain("DELETE FROM STAFF_SERVICE");
        }
    }
}
