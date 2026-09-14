package com.face.platform.approval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5AfterSaleApprovalApiContractTest {

    @Test
    void controllersExposeAfterSaleApprovalAndAdjustmentRoutes() throws Exception {
        String afterSale = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/aftersale/V3AfterSaleController.java"),
            StandardCharsets.UTF_8
        );
        String approval = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/approval/V3ApprovalController.java"),
            StandardCharsets.UTF_8
        );
        String adjustment = Files.readString(
            Path.of("src/main/java/com/face/platform/v3/commission/V3CommissionAdjustmentController.java"),
            StandardCharsets.UTF_8
        );

        assertThat(afterSale).contains("@GetMapping(\"/cases\")");
        assertThat(afterSale).contains("@PostMapping(\"/cases\")");
        assertThat(afterSale).contains("@GetMapping(\"/cases/{caseId}\")");
        assertThat(afterSale).contains("@PostMapping(\"/cases/{caseId}/actions\")");
        assertThat(afterSale).contains("@PostMapping(\"/cases/{caseId}/refunds\")");
        assertThat(afterSale).contains("@PostMapping(\"/cases/{caseId}/reopen\")");
        assertThat(approval).contains("@GetMapping");
        assertThat(approval).contains("@GetMapping(\"/{approvalId}\")");
        assertThat(approval).contains("@PostMapping(\"/{approvalId}/decisions\")");
        assertThat(approval).contains("@PostMapping(\"/{approvalId}/cancel\")");
        assertThat(adjustment)
            .contains("@PostMapping(\"/entries/{entryId}/adjustments\")");
        assertThat(afterSale + approval + adjustment).contains("Idempotency-Key");
        assertThat(afterSale + approval).contains("version");
    }
}
