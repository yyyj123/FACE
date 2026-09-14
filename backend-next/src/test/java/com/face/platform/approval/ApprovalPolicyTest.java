package com.face.platform.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalPolicyTest {

    @Test
    void pendingApprovalHasImmutableTerminalDecisions() {
        assertThat(ApprovalPolicy.canTransition("PENDING", "APPROVED")).isTrue();
        assertThat(ApprovalPolicy.canTransition("PENDING", "REJECTED")).isTrue();
        assertThat(ApprovalPolicy.canTransition("PENDING", "CANCELLED")).isTrue();
        assertThat(ApprovalPolicy.canTransition("PENDING", "EXPIRED")).isTrue();
        assertThat(ApprovalPolicy.canTransition("APPROVED", "REJECTED")).isFalse();
        assertThat(ApprovalPolicy.canTransition("CANCELLED", "PENDING")).isFalse();
    }

    @Test
    void requesterCannotDecideTheirOwnApproval() {
        assertThat(ApprovalPolicy.canDecide(11L, 11L)).isFalse();
        assertThat(ApprovalPolicy.canDecide(12L, 11L)).isTrue();
    }
}
