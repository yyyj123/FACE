package com.face.platform.training;

import java.util.Map;
import java.util.Set;

public final class TrainingPolicy {

    private static final Map<String, Set<String>> COURSE_TRANSITIONS = Map.of(
        "DRAFT", Set.of("ACTIVE"),
        "ACTIVE", Set.of("RETIRED"),
        "RETIRED", Set.of()
    );

    private static final Map<String, Set<String>> RECORD_TRANSITIONS = Map.of(
        "ASSIGNED", Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS", Set.of("SUBMITTED", "CANCELLED"),
        "SUBMITTED", Set.of("PASSED", "FAILED"),
        "PASSED", Set.of("EXPIRED"),
        "FAILED", Set.of(),
        "EXPIRED", Set.of(),
        "CANCELLED", Set.of()
    );

    private TrainingPolicy() {
    }

    public static boolean canTransitionCourse(String from, String to) {
        return COURSE_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canTransitionRecord(String from, String to) {
        return RECORD_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean passes(int score, int passScore) {
        return score >= 0 && score <= 100 && passScore >= 0 && passScore <= 100 && score >= passScore;
    }

    public static boolean canVerify(long verifierAccountId, Long traineeAccountId) {
        return traineeAccountId == null || verifierAccountId != traineeAccountId.longValue();
    }
}
