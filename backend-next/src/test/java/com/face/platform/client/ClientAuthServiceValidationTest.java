package com.face.platform.client;

import com.face.platform.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ClientAuthServiceValidationTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ClientAuthService service = new ClientAuthService(jdbcTemplate);

    @Test
    void invalidPhoneIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.register(Map.of(
            "username", "member001",
            "password", "Face@123",
            "name", "测试会员",
            "phone", "dd",
            "shopId", 1
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(400)
        );

        verifyNoInteractions(jdbcTemplate);
    }
}
