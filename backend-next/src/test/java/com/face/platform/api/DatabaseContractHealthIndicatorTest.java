package com.face.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseContractHealthIndicatorTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void reportsUpOnlyWhenConnectionMigrationTablesAndPermissionIndexAreReady() {
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(
            Map.of("databaseName", "face_salon", "databaseVersion", "8.0.41", "sessionTimeZone", "+08:00"),
            Map.of("version", "2026072603", "success", true)
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .thenReturn(11, 1);

        Health health = new DatabaseContractHealthIndicator(jdbcTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
            .containsEntry("database", "face_salon")
            .containsEntry("requiredTables", "11/11")
            .containsEntry("permissionIndex", "ready");
    }

    @Test
    void reportsUpWhenPermissionIndexHasMultipleColumns() {
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(
            Map.of("databaseName", "face_salon", "databaseVersion", "8.0.41", "sessionTimeZone", "+08:00"),
            Map.of("version", "2026072603", "success", true)
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .thenReturn(11, 8);

        Health health = new DatabaseContractHealthIndicator(jdbcTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("permissionIndex", "ready");
    }

    @Test
    void reportsDownWhenRequiredTablesAreMissing() {
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(
            Map.of("databaseName", "face_salon", "databaseVersion", "8.0.41", "sessionTimeZone", "+08:00")
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(10);

        Health health = new DatabaseContractHealthIndicator(jdbcTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("requiredTables", "10/11");
    }
}
