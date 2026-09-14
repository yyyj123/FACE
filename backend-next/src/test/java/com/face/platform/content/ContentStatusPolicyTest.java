package com.face.platform.content;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentStatusPolicyTest {

    @Test
    void validatesScheduledTimeAndLifecycleTransitions() {
        Instant now = Instant.parse("2026-08-03T04:00:00Z");
        assertThat(ContentStatusPolicy.transition("DRAFT", "SCHEDULED", now.plusSeconds(60), now))
            .isEqualTo("SCHEDULED");
        assertThat(ContentStatusPolicy.transition("SCHEDULED", "PUBLISHED", null, now))
            .isEqualTo("PUBLISHED");
        assertThat(ContentStatusPolicy.transition("PUBLISHED", "OFFLINE", null, now))
            .isEqualTo("OFFLINE");
        assertThatThrownBy(() -> ContentStatusPolicy.transition(
            "PUBLISHED", "DRAFT", null, now
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContentStatusPolicy.transition(
            "DRAFT", "SCHEDULED", now.minusSeconds(1), now
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
