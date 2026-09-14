package com.face.platform.v3.auth;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3AuthSessionRepositoryTest {

    @Test
    void mapsDatabaseComputedLoginLockWithoutJdbcDatetimeConversion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("manager"))).thenReturn(List.of(
            Map.of(
                "id", 10L,
                "tenant_id", 1L,
                "home_shop_id", 2L,
                "username", "manager",
                "password_hash", "synthetic-hash",
                "status", "ACTIVE",
                "login_locked", 1
            )
        ));

        var account = new V3AuthSessionRepository(jdbcTemplate)
            .findAccountForLogin("manager")
            .orElseThrow();

        assertThat(account.loginLocked()).isTrue();
    }
}
