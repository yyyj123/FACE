package com.face.platform.booking;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public final class BookingPolicy {

    public static final int QUERY_DAYS = 7;
    public static final int MAX_ADVANCE_DAYS = 30;
    private static final Set<Integer> ALLOWED_INTERVALS = Set.of(15, 20, 30, 60);

    public List<LocalDate> queryDates(LocalDate today, LocalDate fromDate) {
        if (today == null || fromDate == null) {
            throw new IllegalArgumentException("日期不能为空");
        }
        long firstDayOffset = ChronoUnit.DAYS.between(today, fromDate);
        if (firstDayOffset < 0 || firstDayOffset + QUERY_DAYS - 1 > MAX_ADVANCE_DAYS) {
            throw new IllegalArgumentException("可预约日期必须位于未来 30 天内，且每次查询连续 7 天");
        }
        return java.util.stream.IntStream.range(0, QUERY_DAYS)
            .mapToObj(fromDate::plusDays)
            .toList();
    }

    public List<SlotWindow> generateSlots(
        ServiceRule rule,
        LocalDateTime workStart,
        LocalDateTime workEnd
    ) {
        requireValidRule(rule);
        if (workStart == null || workEnd == null || !workStart.isBefore(workEnd)) {
            return List.of();
        }

        LocalDateTime firstServiceStart = alignUp(
            workStart.plusMinutes(rule.bufferBeforeMinutes()),
            rule.slotIntervalMinutes()
        );
        List<SlotWindow> result = new ArrayList<>();
        for (LocalDateTime start = firstServiceStart; ; start = start.plusMinutes(rule.slotIntervalMinutes())) {
            LocalDateTime occupiedStart = start.minusMinutes(rule.bufferBeforeMinutes());
            LocalDateTime occupiedEnd = start
                .plusMinutes(rule.durationMinutes())
                .plusMinutes(rule.bufferAfterMinutes());
            if (occupiedEnd.isAfter(workEnd)) {
                break;
            }
            result.add(new SlotWindow(start, occupiedStart, occupiedEnd));
        }
        return List.copyOf(result);
    }

    public boolean isSelectable(
        ServiceRule rule,
        LocalDateTime now,
        LocalDateTime serviceStart,
        boolean minimumAdvanceBypassed
    ) {
        requireValidRule(rule);
        if (now == null || serviceStart == null || !serviceStart.isAfter(now)) {
            return false;
        }
        if (!rule.sameDayBookingAllowed() && serviceStart.toLocalDate().equals(now.toLocalDate())) {
            return false;
        }
        return minimumAdvanceBypassed
            || !serviceStart.isBefore(now.plusMinutes(rule.minimumAdvanceMinutes()));
    }

    public boolean overlaps(SlotWindow candidate, LocalDateTime occupiedStart, LocalDateTime occupiedEnd) {
        if (candidate == null || occupiedStart == null || occupiedEnd == null) {
            return false;
        }
        return candidate.occupiedStart().isBefore(occupiedEnd)
            && candidate.occupiedEnd().isAfter(occupiedStart);
    }

    private LocalDateTime alignUp(LocalDateTime value, int intervalMinutes) {
        LocalDateTime dayStart = value.toLocalDate().atStartOfDay();
        long minutes = ChronoUnit.MINUTES.between(dayStart, value);
        long aligned = ((minutes + intervalMinutes - 1) / intervalMinutes) * intervalMinutes;
        return dayStart.plusMinutes(aligned);
    }

    private void requireValidRule(ServiceRule rule) {
        if (rule == null || rule.durationMinutes() <= 0
            || !ALLOWED_INTERVALS.contains(rule.slotIntervalMinutes())
            || rule.bufferBeforeMinutes() < 0 || rule.bufferAfterMinutes() < 0
            || rule.minimumAdvanceMinutes() < 0) {
            throw new IllegalArgumentException("预约规则不完整或间隔不受支持");
        }
    }

    public record ServiceRule(
        long serviceId,
        int durationMinutes,
        int slotIntervalMinutes,
        int bufferBeforeMinutes,
        int bufferAfterMinutes,
        int minimumAdvanceMinutes,
        boolean sameDayBookingAllowed,
        int freeCancelMinutes,
        int rescheduleCutoffMinutes,
        int maxReschedules,
        String lateCancelPolicy,
        java.math.BigDecimal lateCancelValue,
        String termsVersion
    ) {
    }

    public record SlotWindow(
        LocalDateTime serviceStart,
        LocalDateTime occupiedStart,
        LocalDateTime occupiedEnd
    ) {
        public LocalDateTime serviceEnd(ServiceRule rule) {
            return serviceStart.plusMinutes(rule.durationMinutes());
        }
    }
}
