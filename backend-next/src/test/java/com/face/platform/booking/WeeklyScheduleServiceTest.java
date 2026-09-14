package com.face.platform.booking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeeklyScheduleServiceTest {

    @Test
    void derivesPublicAvailabilityWithoutTreatingDisplayHoursAsBookingRules() {
        assertEquals("AVAILABLE", WeeklyScheduleService.availabilityStatus(
            "WORKING", 480, 480, 0, 0
        ));
        assertEquals("PARTIALLY_AVAILABLE", WeeklyScheduleService.availabilityStatus(
            "WORKING", 480, 330, 60, 90
        ));
        assertEquals("FULL", WeeklyScheduleService.availabilityStatus(
            "WORKING", 480, 0, 0, 480
        ));
        assertEquals("UNAVAILABLE", WeeklyScheduleService.availabilityStatus(
            "LEAVE", 0, 0, 0, 0
        ));
    }

    @Test
    void oneHourDisplaySlotCanShowPartialOccupancyForNinetyMinuteBooking() {
        assertEquals("FULL", WeeklyScheduleService.slotStatus(60, 60));
        assertEquals("PARTIALLY_AVAILABLE", WeeklyScheduleService.slotStatus(60, 30));
        assertEquals("AVAILABLE", WeeklyScheduleService.slotStatus(60, 0));
        assertEquals("UNAVAILABLE", WeeklyScheduleService.slotStatus(0, 0));
    }
}
