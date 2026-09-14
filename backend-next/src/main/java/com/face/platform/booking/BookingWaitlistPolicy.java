package com.face.platform.booking;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public final class BookingWaitlistPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "WAITING", Set.of("MATCHED", "CANCELLED", "EXPIRED", "INVALID"),
        "MATCHED", Set.of("WAITING_CONFIRMATION", "CANCELLED", "EXPIRED", "INVALID"),
        "WAITING_CONFIRMATION", Set.of("CONFIRMED", "CANCELLED", "EXPIRED", "INVALID")
    );

    public boolean canTransition(String from, String to) {
        return from != null && to != null
            && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean createsAppointment(String state) {
        return "CONFIRMED".equals(state);
    }
}
