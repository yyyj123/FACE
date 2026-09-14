package com.face.platform.packageaccount;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberAccountApplicationServiceValidationTest {

    @Test
    void rejectsNegativeManualAmountBeforeAnyAccountMutation() {
        MemberAccountApplicationService service =
            new MemberAccountApplicationService(null, null, null, null);
        TenantPrincipal principal = new TenantPrincipal(
            1L, 1L, 1L, "tester", List.of("OWNER"), Set.of(), Set.of(1L), false
        );

        assertThatThrownBy(() -> service.postManualEntry(
            principal,
            1L,
            1L,
            true,
            "MANUAL_CREDIT",
            new BigDecimal("-1.00"),
            null,
            null,
            "negative-amount",
            "a".repeat(64),
            null,
            0
        ))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getMessage()).contains("必须大于零");
            });
    }
}
