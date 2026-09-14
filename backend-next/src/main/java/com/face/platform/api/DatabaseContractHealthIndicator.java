package com.face.platform.api;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("databaseContract")
public class DatabaseContractHealthIndicator implements HealthIndicator {

    private static final int REQUIRED_TABLE_COUNT = 11;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseContractHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Map<String, Object> connection = jdbcTemplate.queryForMap(
                """
                SELECT DATABASE() AS databaseName,
                       VERSION() AS databaseVersion,
                       @@session.time_zone AS sessionTimeZone
                """
            );
            int tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                    'tenant',
                    'account_shop_role',
                    'appointment',
                    'service_record',
                    'stock_balance',
                    'data_access_log',
                    'outbox_event',
                    'auth_session',
                    'staff_skill_version',
                    'service_resource',
                    'resource_booking'
                  )
                """,
                Integer.class
            );

            if (tableCount != REQUIRED_TABLE_COUNT) {
                return base(Health.down(), connection)
                    .withDetail("requiredTables", tableCount + "/" + REQUIRED_TABLE_COUNT)
                    .withDetail("reason", "database contract tables are incomplete")
                    .build();
            }

            Map<String, Object> migration = jdbcTemplate.queryForMap(
                """
                SELECT version, success
                FROM flyway_schema_history
                ORDER BY installed_rank DESC
                LIMIT 1
                """
            );
            int permissionIndexCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'account_shop_role'
                  AND index_name = 'idx_account_shop_role_permission_lookup'
                """,
                Integer.class
            );
            boolean migrationSuccessful = isTrue(migration.get("success"));

            boolean permissionIndexReady = permissionIndexCount > 0;
            Health.Builder builder = migrationSuccessful && permissionIndexReady
                ? Health.up()
                : Health.down();
            builder = base(builder, connection)
                .withDetail("requiredTables", REQUIRED_TABLE_COUNT + "/" + REQUIRED_TABLE_COUNT)
                .withDetail("latestFlywayVersion", String.valueOf(migration.get("version")))
                .withDetail("latestFlywaySuccess", migrationSuccessful)
                .withDetail("permissionIndex", permissionIndexReady ? "ready" : "missing");
            if (!migrationSuccessful || !permissionIndexReady) {
                builder.withDetail("reason", "database migration contract is not ready");
            }
            return builder.build();
        } catch (DataAccessException | NullPointerException exception) {
            return Health.down()
                .withDetail("reason", "database contract query failed")
                .build();
        }
    }

    private Health.Builder base(Health.Builder builder, Map<String, Object> connection) {
        return builder
            .withDetail("database", String.valueOf(connection.get("databaseName")))
            .withDetail("databaseVersion", String.valueOf(connection.get("databaseVersion")))
            .withDetail("sessionTimeZone", String.valueOf(connection.get("sessionTimeZone")));
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return "1".equals(String.valueOf(value))
            || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
