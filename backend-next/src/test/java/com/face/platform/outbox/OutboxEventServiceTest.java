package com.face.platform.outbox;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxEventServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OutboxEventService service =
        new OutboxEventService(jdbcTemplate, new ObjectMapper());

    @Test
    void storesOnlyIdentifierBasedPayloadForServiceCompletion() {
        service.append(
            principal(),
            2L,
            "SERVICE_RECORD",
            "100",
            "ServiceRecordCompleted",
            Map.of("serviceRecordId", 100L, "appointmentId", 200L, "memberId", 300L)
        );

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
            anyString(),
            anyString(),
            eq(1L),
            eq(2L),
            eq("SERVICE_RECORD"),
            eq("100"),
            eq("ServiceRecordCompleted"),
            payload.capture()
        );
        assertThat(payload.getValue())
            .contains("\"serviceRecordId\":100")
            .doesNotContain("phone")
            .doesNotContain("observations")
            .doesNotContain("credential");
    }

    @Test
    void rejectsSensitivePayloadKeysBeforeWriting() {
        assertThatThrownBy(() -> service.append(
            principal(),
            2L,
            "PAYMENT",
            "5",
            "PaymentSucceeded",
            Map.of("paymentCredential", "secret")
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("敏感");
        verifyNoInteractions(jdbcTemplate);
    }

    private TenantPrincipal principal() {
        return new TenantPrincipal(
            10L,
            1L,
            2L,
            "manager",
            List.of("MANAGER"),
            Set.of(),
            Set.of(2L),
            false
        );
    }
}
