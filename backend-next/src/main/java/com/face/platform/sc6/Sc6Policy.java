package com.face.platform.sc6;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

public final class Sc6Policy {

    public static final Duration CONFIRMATION_WINDOW = Duration.ofHours(24);
    public static final Duration SERVICE_AFTERSALE_WINDOW = Duration.ofDays(7);
    public static final Duration CUSTOMER_RESPONSE_WINDOW = Duration.ofHours(48);
    public static final BigDecimal ADMIN_ASSET_LIMIT = new BigDecimal("500.00");

    private Sc6Policy() {
    }

    public static BigDecimal average(int staff, int effect, int environment) {
        validateRating(staff);
        validateRating(effect);
        validateRating(environment);
        return BigDecimal.valueOf((long) staff + effect + environment)
            .divide(BigDecimal.valueOf(3), 1, RoundingMode.HALF_UP);
    }

    public static boolean triggersContact(
        int staff, int effect, int environment, boolean wantsContact
    ) {
        return wantsContact || staff < 3 || effect < 3 || environment < 3
            || average(staff, effect, environment).compareTo(BigDecimal.valueOf(3)) < 0;
    }

    public static boolean withinServiceAfterSaleWindow(
        LocalDateTime completedAt, LocalDateTime now
    ) {
        return completedAt != null && now != null
            && !now.isBefore(completedAt)
            && !now.isAfter(completedAt.plus(SERVICE_AFTERSALE_WINDOW));
    }

    public static boolean requiresSuperAdmin(BigDecimal assetAmount) {
        return assetAmount != null && assetAmount.compareTo(ADMIN_ASSET_LIMIT) > 0;
    }

    public static boolean mayReopen(int reopenCount, LocalDateTime entryDeadline, LocalDateTime now) {
        return reopenCount == 0 && entryDeadline != null && now != null
            && !now.isAfter(entryDeadline);
    }

    private static void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评价分数必须在 1 到 5 之间");
        }
    }
}
