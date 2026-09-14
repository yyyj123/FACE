package com.face.platform.training;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingPolicyTest {

    @Test
    void courseAndRecordStatesOnlyMoveThroughAllowedTransitions() {
        assertThat(TrainingPolicy.canTransitionCourse("DRAFT", "ACTIVE")).isTrue();
        assertThat(TrainingPolicy.canTransitionCourse("ACTIVE", "RETIRED")).isTrue();
        assertThat(TrainingPolicy.canTransitionCourse("RETIRED", "ACTIVE")).isFalse();

        assertThat(TrainingPolicy.canTransitionRecord("ASSIGNED", "IN_PROGRESS")).isTrue();
        assertThat(TrainingPolicy.canTransitionRecord("IN_PROGRESS", "SUBMITTED")).isTrue();
        assertThat(TrainingPolicy.canTransitionRecord("SUBMITTED", "PASSED")).isTrue();
        assertThat(TrainingPolicy.canTransitionRecord("SUBMITTED", "FAILED")).isTrue();
        assertThat(TrainingPolicy.canTransitionRecord("PASSED", "IN_PROGRESS")).isFalse();
        assertThat(TrainingPolicy.canTransitionRecord("FAILED", "SUBMITTED")).isFalse();
    }

    @Test
    void passRequiresThresholdAndVerifierMustDifferFromTraineeAccount() {
        assertThat(TrainingPolicy.passes(80, 80)).isTrue();
        assertThat(TrainingPolicy.passes(79, 80)).isFalse();
        assertThat(TrainingPolicy.canVerify(21L, 21L)).isFalse();
        assertThat(TrainingPolicy.canVerify(22L, 21L)).isTrue();
    }
}
