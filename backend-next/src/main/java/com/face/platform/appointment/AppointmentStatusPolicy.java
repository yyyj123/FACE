package com.face.platform.appointment;

import com.face.platform.api.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public final class AppointmentStatusPolicy {

    private static final Map<String, List<String>> TRANSITIONS = Map.of(
        "PENDING", List.of("CONFIRMED", "CANCELLED"),
        "CONFIRMED", List.of("CHECKED_IN", "CANCELLED", "NO_SHOW"),
        "CHECKED_IN", List.of("IN_SERVICE", "CANCELLED"),
        "IN_SERVICE", List.of("COMPLETED")
    );

    private AppointmentStatusPolicy() {
    }

    public static List<String> allowedNext(String current) {
        return TRANSITIONS.getOrDefault(current, List.of());
    }

    public static void requireTransition(String current, String next) {
        if (!allowedNext(current).contains(next)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "预约状态已变化，不能从 " + current + " 变更为 " + next
            );
        }
    }
}

