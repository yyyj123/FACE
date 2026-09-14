package com.face.platform.idempotency;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandIdempotencyServiceTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CommandIdempotencyService service = new CommandIdempotencyService(jdbcTemplate);
    private final TenantPrincipal principal = new TenantPrincipal(
        11L,
        1L,
        2L,
        "manager01",
        List.of("MANAGER"),
        Set.of(),
        Set.of(2L),
        false
    );

    @Test
    void firstCommandIsRecordedThenCompletedInTheSameTransaction() {
        AtomicInteger executions = new AtomicInteger();
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq("m2-resource-001")))
            .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), eq(1L), eq("m2-resource-001")))
            .thenReturn(1);

        service.run(
            principal,
            "m2-resource-001",
            "RESOURCE_CREATE",
            HASH_A,
            executions::incrementAndGet
        );

        assertThat(executions).hasValue(1);
        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq("m2-resource-001"),
            eq("RESOURCE_CREATE"),
            eq(HASH_A)
        );
        verify(jdbcTemplate).update(
            anyString(),
            eq(1L),
            eq("m2-resource-001")
        );
    }

    @Test
    void completedReplayWithSameHashReturnsWithoutExecutingAgain() {
        AtomicInteger executions = new AtomicInteger();
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq("m2-resource-001")))
            .thenReturn(List.of(Map.of(
                "operation_code", "RESOURCE_CREATE",
                "request_hash", HASH_A,
                "status", "COMPLETED"
            )));

        service.run(
            principal,
            "m2-resource-001",
            "RESOURCE_CREATE",
            HASH_A,
            executions::incrementAndGet
        );

        assertThat(executions).hasValue(0);
    }

    @Test
    void replayWithDifferentPayloadIsRejected() {
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq("m2-resource-001")))
            .thenReturn(List.of(Map.of(
                "operation_code", "RESOURCE_CREATE",
                "request_hash", HASH_A,
                "status", "COMPLETED"
            )));

        assertThatThrownBy(() -> service.run(
            principal,
            "m2-resource-001",
            "RESOURCE_CREATE",
            HASH_B,
            () -> {
            }
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.status().value()).isEqualTo(409)
        );
    }
}
