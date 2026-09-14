package com.face.platform.v3.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V3ApiResponseTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-07-28T06:30:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void successEnvelopeContainsStableContractAndTraceFields() {
        V3ApiResponse<Map<String, Object>> response = V3ApiResponse.success(
            Map.of("ready", true),
            "req-20260728-001",
            CLOCK
        );

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("ok");
        assertThat(response.data()).containsEntry("ready", true);
        assertThat(response.request_id()).isEqualTo("req-20260728-001");
        assertThat(response.timestamp()).isEqualTo("2026-07-28T06:30:00Z");
    }

    @Test
    void errorEnvelopeKeepsStableBusinessCode() {
        V3ApiResponse<Void> response = V3ApiResponse.error(
            "PERMISSION_DENIED",
            "当前账号无权访问",
            "req-20260728-002",
            CLOCK
        );

        assertThat(response.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(response.data()).isNull();
        assertThat(response.request_id()).isEqualTo("req-20260728-002");
    }
}
