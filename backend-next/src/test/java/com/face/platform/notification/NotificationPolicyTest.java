package com.face.platform.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPolicyTest {

    @Test
    void onlyUnreadMessagesCanMoveToReadAndReadNeverMovesBack() {
        assertThat(NotificationPolicy.canTransition("UNREAD", "READ")).isTrue();
        assertThat(NotificationPolicy.canTransition("READ", "READ")).isTrue();
        assertThat(NotificationPolicy.canTransition("READ", "UNREAD")).isFalse();
        assertThat(NotificationPolicy.canTransition("UNREAD", "UNREAD")).isFalse();
    }

    @Test
    void optimisticVersionMustMatch() {
        assertThat(NotificationPolicy.versionMatches(4, 4)).isTrue();
        assertThat(NotificationPolicy.versionMatches(4, 3)).isFalse();
    }

    @Test
    void bulkReadUsesAnInclusiveServerWatermark() {
        Instant watermark = Instant.parse("2026-07-30T08:00:00Z");
        assertThat(NotificationPolicy.withinWatermark(
            Instant.parse("2026-07-30T07:59:59Z"), watermark
        )).isTrue();
        assertThat(NotificationPolicy.withinWatermark(watermark, watermark)).isTrue();
        assertThat(NotificationPolicy.withinWatermark(
            Instant.parse("2026-07-30T08:00:01Z"), watermark
        )).isFalse();
    }
}
