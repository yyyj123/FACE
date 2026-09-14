package com.face.platform.reconciliation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationLifecyclePolicyTest {

    @Test
    void matchedBatchCanCloseWithoutResolution() {
        assertThat(ReconciliationLifecyclePolicy.canTransition("PENDING", "RUNNING")).isTrue();
        assertThat(ReconciliationLifecyclePolicy.canTransition("RUNNING", "MATCHED")).isTrue();
        assertThat(ReconciliationLifecyclePolicy.canTransition("MATCHED", "CLOSED")).isTrue();
    }

    @Test
    void differentBatchMustBeResolvedBeforeClose() {
        assertThat(ReconciliationLifecyclePolicy.canTransition("RUNNING", "DIFFERENT")).isTrue();
        assertThat(ReconciliationLifecyclePolicy.canTransition("DIFFERENT", "CLOSED")).isFalse();
        assertThat(ReconciliationLifecyclePolicy.canTransition("DIFFERENT", "RESOLVED")).isTrue();
        assertThat(ReconciliationLifecyclePolicy.canTransition("RESOLVED", "CLOSED")).isTrue();
    }
}

