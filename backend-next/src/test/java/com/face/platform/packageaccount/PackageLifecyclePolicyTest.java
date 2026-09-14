package com.face.platform.packageaccount;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackageLifecyclePolicyTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 28);

    @Test
    void activePackageWithinValidityCanBeWrittenOff() {
        assertEquals(
            new BigDecimal("2.0000"),
            PackageLifecyclePolicy.balanceAfterWriteOff(
                "ACTIVE",
                new BigDecimal("3.0000"),
                new BigDecimal("1.0000"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 31),
                BUSINESS_DATE
            )
        );
    }

    @Test
    void writeOffRejectsFrozenExpiredFutureAndInsufficientPackages() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PackageLifecyclePolicy.balanceAfterWriteOff(
                "FROZEN", new BigDecimal("3"), BigDecimal.ONE,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), BUSINESS_DATE
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PackageLifecyclePolicy.balanceAfterWriteOff(
                "ACTIVE", new BigDecimal("3"), BigDecimal.ONE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), BUSINESS_DATE
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PackageLifecyclePolicy.balanceAfterWriteOff(
                "ACTIVE", new BigDecimal("3"), BigDecimal.ONE,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 27), BUSINESS_DATE
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PackageLifecyclePolicy.balanceAfterWriteOff(
                "ACTIVE", new BigDecimal("0.5"), BigDecimal.ONE,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), BUSINESS_DATE
            )
        );
    }

    @Test
    void writeOffRejectsNonPositiveQuantityAndExhaustsAtZero() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PackageLifecyclePolicy.balanceAfterWriteOff(
                "ACTIVE", BigDecimal.ONE, BigDecimal.ZERO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), BUSINESS_DATE
            )
        );
        assertEquals("EXHAUSTED", PackageLifecyclePolicy.statusAfterBalance(BigDecimal.ZERO));
        assertEquals("ACTIVE", PackageLifecyclePolicy.statusAfterBalance(new BigDecimal("0.0001")));
    }
}

