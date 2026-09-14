package com.face.platform.aftersale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AfterSalePolicyTest {

    @Test
    void enforcesDocumentedLifecycleAndControlledReopen() {
        assertThat(AfterSalePolicy.canTransition("OPEN", "TRIAGED")).isTrue();
        assertThat(AfterSalePolicy.canTransition("TRIAGED", "PROCESSING")).isTrue();
        assertThat(AfterSalePolicy.canTransition("PROCESSING", "WAITING_CUSTOMER")).isTrue();
        assertThat(AfterSalePolicy.canTransition("WAITING_CUSTOMER", "PROCESSING")).isTrue();
        assertThat(AfterSalePolicy.canTransition("PROCESSING", "RESOLVED")).isTrue();
        assertThat(AfterSalePolicy.canTransition("RESOLVED", "CLOSED")).isTrue();
        assertThat(AfterSalePolicy.canTransition("RESOLVED", "REOPENED")).isTrue();
        assertThat(AfterSalePolicy.canTransition("REOPENED", "PROCESSING")).isTrue();
        assertThat(AfterSalePolicy.canTransition("OPEN", "REJECTED")).isTrue();
        assertThat(AfterSalePolicy.canTransition("CLOSED", "PROCESSING")).isFalse();
        assertThat(AfterSalePolicy.canTransition("REJECTED", "OPEN")).isFalse();
    }
}
