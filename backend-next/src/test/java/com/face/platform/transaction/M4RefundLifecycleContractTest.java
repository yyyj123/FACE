package com.face.platform.transaction;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4RefundLifecycleContractTest {

    @Test
    void v3RefundFlowKeepsApprovalExecutionAndAssetReversalExplicit() throws Exception {
        String service = Files.readString(
            Path.of("src/main/java/com/face/platform/transaction/RefundApplicationService.java"),
            StandardCharsets.UTF_8
        );
        String controller = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/transaction/V3RefundController.java"),
            StandardCharsets.UTF_8
        );
        String legacyController = Files.readString(
            Path.of("src/main/java/com/face/platform/transaction/TransactionController.java"),
            StandardCharsets.UTF_8
        );

        assertThat(service).contains("\"refund:request\"");
        assertThat(service).contains("\"refund:approve\"");
        assertThat(service).contains("\"refund:execute\"");
        assertThat(service).contains("RefundExecutionPolicy.canTransition");
        assertThat(service).contains("principal.accountId() == number(refund.get(\"createdBy\"))");
        assertThat(service).contains("memberAccountService.creditBalanceForRefund");
        assertThat(service).contains("packageAccountService.cancelUnusedForRefund");
        assertThat(service).contains("\"RefundCompleted\"");
        assertThat(service).doesNotContain("UPDATE member_account");
        assertThat(service).doesNotContain("UPDATE package_ledger");

        assertThat(controller).contains("@PostMapping(\"/payments/{paymentId}/refunds\")");
        assertThat(controller).contains("@PostMapping(\"/refunds/{refundId}/decision\")");
        assertThat(controller).contains("@PostMapping(\"/refunds/{refundId}/execute\")");
        assertThat(controller).contains("@GetMapping(\"/refunds/{refundId}\")");
        assertThat(controller).contains("@GetMapping(\"/members/{memberId}/refunds\")");
        assertThat(controller).contains("requiredIdempotencyKey");
        assertThat(service).contains("memberRefunds(");
        assertThat(service).contains("accessService.requireMemberId(principal)");

        assertThat(legacyController).contains("RefundApplicationService refundService");
        assertThat(legacyController).contains("@PostMapping(\"/refunds/{refundId}/execute\")");
        assertThat(legacyController).contains("refundService.request(");
        assertThat(legacyController).contains("refundService.decide(");
        assertThat(legacyController).contains("refundService.execute(");
        assertThat(legacyController).doesNotContain("transactionService.createRefund(");
        assertThat(legacyController).doesNotContain("transactionService.decideRefund(");
    }
}
