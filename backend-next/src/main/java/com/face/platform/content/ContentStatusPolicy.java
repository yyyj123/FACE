package com.face.platform.content;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ContentStatusPolicy {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT", Set.of("SCHEDULED", "PUBLISHED", "OFFLINE"),
        "SCHEDULED", Set.of("DRAFT", "PUBLISHED", "OFFLINE"),
        "PUBLISHED", Set.of("OFFLINE"),
        "OFFLINE", Set.of("DRAFT", "SCHEDULED", "PUBLISHED")
    );

    private ContentStatusPolicy() {
    }

    public static String transition(
        String rawCurrent,
        String rawTarget,
        Instant scheduledAt,
        Instant now
    ) {
        String current = normalize(rawCurrent);
        String target = normalize(rawTarget);
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalArgumentException("不支持从 " + current + " 变更为 " + target);
        }
        if ("SCHEDULED".equals(target) && (scheduledAt == null || !scheduledAt.isAfter(now))) {
            throw new IllegalArgumentException("定时发布时间必须晚于当前时间");
        }
        return target;
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TRANSITIONS.containsKey(normalized)) {
            throw new IllegalArgumentException("不支持的内容状态");
        }
        return normalized;
    }
}
