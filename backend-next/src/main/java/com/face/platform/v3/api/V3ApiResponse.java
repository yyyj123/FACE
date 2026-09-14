package com.face.platform.v3.api;

import java.time.Clock;
import java.time.Instant;

public record V3ApiResponse<T>(
    String code,
    String message,
    T data,
    String request_id,
    String timestamp
) {
    public static <T> V3ApiResponse<T> success(T data, String requestId, Clock clock) {
        return new V3ApiResponse<>("SUCCESS", "ok", data, requestId, Instant.now(clock).toString());
    }

    public static <T> V3ApiResponse<T> success(T data, String requestId) {
        return success(data, requestId, Clock.systemUTC());
    }

    public static V3ApiResponse<Void> error(
        String code,
        String message,
        String requestId,
        Clock clock
    ) {
        return new V3ApiResponse<>(code, message, null, requestId, Instant.now(clock).toString());
    }

    public static V3ApiResponse<Void> error(String code, String message, String requestId) {
        return error(code, message, requestId, Clock.systemUTC());
    }
}
