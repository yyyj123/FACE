package com.face.platform.booking;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPolicyTest {

    private final BookingPolicy policy = new BookingPolicy();

    @Test
    void acceptsExactlySevenDaysInsideThirtyDayHorizon() {
        LocalDate today = LocalDate.of(2026, 8, 3);

        assertThat(policy.queryDates(today, today)).containsExactly(
            LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8),
            LocalDate.of(2026, 8, 9)
        );
        assertThat(policy.queryDates(today, today.plusDays(24))).hasSize(7);
    }

    @Test
    void rejectsPastOrPageBeyondThirtyDayHorizon() {
        LocalDate today = LocalDate.of(2026, 8, 3);

        assertThatThrownBy(() -> policy.queryDates(today, today.minusDays(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.queryDates(today, today.plusDays(25)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesOnlyServerAlignedSlotsThatFitServiceAndBuffers() {
        BookingPolicy.ServiceRule rule = rule(20, 60, 10, 15, 0, true);
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 9, 0);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 8, 4, 12, 0);

        List<BookingPolicy.SlotWindow> slots = policy.generateSlots(rule, windowStart, windowEnd);

        assertThat(slots).extracting(BookingPolicy.SlotWindow::serviceStart).containsExactly(
            LocalDateTime.of(2026, 8, 4, 9, 20),
            LocalDateTime.of(2026, 8, 4, 9, 40),
            LocalDateTime.of(2026, 8, 4, 10, 0),
            LocalDateTime.of(2026, 8, 4, 10, 20),
            LocalDateTime.of(2026, 8, 4, 10, 40)
        );
        assertThat(slots.getFirst().occupiedStart()).isEqualTo(LocalDateTime.of(2026, 8, 4, 9, 10));
        assertThat(slots.getFirst().occupiedEnd()).isEqualTo(LocalDateTime.of(2026, 8, 4, 10, 35));
    }

    @Test
    void enforcesSameDayAndMinimumAdvanceBeforeOfferingSlot() {
        BookingPolicy.ServiceRule noSameDay = rule(30, 60, 0, 0, 120, false);
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 9, 0);

        assertThat(policy.isSelectable(noSameDay, now, LocalDateTime.of(2026, 8, 3, 13, 0), false)).isFalse();
        assertThat(policy.isSelectable(noSameDay, now, LocalDateTime.of(2026, 8, 4, 10, 0), false)).isTrue();

        BookingPolicy.ServiceRule sameDay = rule(30, 60, 0, 0, 120, true);
        assertThat(policy.isSelectable(sameDay, now, LocalDateTime.of(2026, 8, 3, 10, 59), false)).isFalse();
        assertThat(policy.isSelectable(sameDay, now, LocalDateTime.of(2026, 8, 3, 11, 0), false)).isTrue();
        assertThat(policy.isSelectable(sameDay, now, LocalDateTime.of(2026, 8, 3, 9, 30), true)).isTrue();
    }

    @Test
    void detectsAnyOverlapAcrossFullOccupiedIntervals() {
        BookingPolicy.SlotWindow candidate = new BookingPolicy.SlotWindow(
            LocalDateTime.of(2026, 8, 4, 10, 0),
            LocalDateTime.of(2026, 8, 4, 9, 50),
            LocalDateTime.of(2026, 8, 4, 11, 15)
        );

        assertThat(policy.overlaps(candidate, LocalDateTime.of(2026, 8, 4, 9, 0), LocalDateTime.of(2026, 8, 4, 9, 50))).isFalse();
        assertThat(policy.overlaps(candidate, LocalDateTime.of(2026, 8, 4, 11, 15), LocalDateTime.of(2026, 8, 4, 12, 0))).isFalse();
        assertThat(policy.overlaps(candidate, LocalDateTime.of(2026, 8, 4, 11, 14), LocalDateTime.of(2026, 8, 4, 12, 0))).isTrue();
    }

    private BookingPolicy.ServiceRule rule(
        int interval, int duration, int before, int after, int advance, boolean sameDay
    ) {
        return new BookingPolicy.ServiceRule(
            1L, duration, interval, before, after, advance, sameDay,
            120, 180, 2, "FORFEIT_DEPOSIT", null, "terms-v1"
        );
    }
}
