package com.face.platform.notification;

import java.time.Instant;

public final class NotificationPolicy {

    private NotificationPolicy() {
    }

    public static boolean canTransition(String current, String target) {
        if ("UNREAD".equals(current) && "READ".equals(target)) return true;
        return "READ".equals(current) && "READ".equals(target);
    }

    public static boolean versionMatches(int actual, int expected) {
        return actual == expected;
    }

    public static boolean withinWatermark(Instant createdAt, Instant watermark) {
        return !createdAt.isAfter(watermark);
    }
}
