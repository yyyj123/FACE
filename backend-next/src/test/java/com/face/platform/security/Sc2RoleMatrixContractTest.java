package com.face.platform.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sc2RoleMatrixContractTest {

    @Test
    void migrationAddsTwoVisibleAdminRolesAndPreservesLegacyRoles() throws Exception {
        String resource = "db/migration/V2026080301__sc2_identity_content_single_shop.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("SC2 Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains(
                "SELECT 'SUPER_ADMIN' AS `ROLE_CODE`",
                "'超级管理员' AS `ROLE_NAME`",
                "'TENANT' AS `SCOPE_TYPE`"
            );
            assertThat(sql).contains("UNION ALL SELECT 'ADMIN', '普通管理员', 'SHOP'");
            assertThat(sql).contains("WHERE LEGACY_ROLE.`ROLE_CODE` = 'OWNER'");
            assertThat(sql).contains("IN ('MANAGER', 'REGIONAL_MANAGER', 'FRONT_DESK')");
            assertThat(sql).doesNotContain("DELETE FROM `ROLE_DEFINITION`");
            assertThat(sql).doesNotContain("UPDATE `ACCOUNT` SET `ROLE_CODE` =");
        }
    }
}
