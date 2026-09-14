package com.face.platform.packageaccount;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberAccountPolicyTest {

    @Test
    void activeMoneyAccountCanDebitAndCreditExactDecimalAmounts() {
        assertEquals(
            new BigDecimal("72.00"),
            MemberAccountPolicy.balanceAfterDebit(
                "BALANCE", "ACTIVE", new BigDecimal("100.00"), new BigDecimal("28.00")
            )
        );
        assertEquals(
            new BigDecimal("128.00"),
            MemberAccountPolicy.balanceAfterCredit(
                "GIFT_BALANCE", "ACTIVE", new BigDecimal("100.00"), new BigDecimal("28.00")
            )
        );
    }

    @Test
    void accountRejectsFrozenInsufficientAndInvalidScaleAmounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MemberAccountPolicy.balanceAfterDebit(
                "BALANCE", "FROZEN", new BigDecimal("100.00"), new BigDecimal("28.00")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MemberAccountPolicy.balanceAfterDebit(
                "BALANCE", "ACTIVE", new BigDecimal("20.00"), new BigDecimal("28.00")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MemberAccountPolicy.balanceAfterCredit(
                "BALANCE", "ACTIVE", new BigDecimal("20.00"), new BigDecimal("1.001")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MemberAccountPolicy.balanceAfterCredit(
                "POINTS", "ACTIVE", new BigDecimal("20"), new BigDecimal("1.5")
            )
        );
    }

    @Test
    void accountRejectsNonPositiveAmounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MemberAccountPolicy.balanceAfterCredit(
                "BALANCE", "ACTIVE", new BigDecimal("20.00"), BigDecimal.ZERO
            )
        );
    }
}

