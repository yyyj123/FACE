package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5CommissionRuleApiContractTest {

    @Test
    void v3RulesRequirePermissionVersionIdempotencyAuditAndImmutablePublishing()
        throws Exception {
        String controller = Files.readString(
            Path.of(
                "src/main/java/com/face/platform/v3/commission/"
                    + "V3CommissionRuleController.java"
            ),
            StandardCharsets.UTF_8
        );
        String service = Files.readString(
            Path.of(
                "src/main/java/com/face/platform/commission/"
                    + "CommissionRuleApplicationService.java"
            ),
            StandardCharsets.UTF_8
        );

        assertThat(controller).contains("@RequestMapping(\"/api/v3/commission\")");
        assertThat(controller).contains("@PostMapping(\"/rules\")");
        assertThat(controller).contains("@PatchMapping(\"/rules/{ruleId}\")");
        assertThat(controller).contains("@PostMapping(\"/rules/{ruleId}/publish\")");
        assertThat(controller).contains("@PostMapping(\"/rules/{ruleId}/retire\")");
        assertThat(controller).contains("@PostMapping(\"/simulations\")");
        assertThat(controller).contains("Idempotency-Key");
        assertThat(controller).contains("RequestHash.of");

        assertThat(service).contains("\"commission:rule:view\"");
        assertThat(service).contains("\"commission:rule:manage\"");
        assertThat(service).contains("FOR UPDATE");
        assertThat(service).contains("version = version + 1");
        assertThat(service).contains("RULE_SCOPE_CONFLICT");
        assertThat(service).contains("COMMISSION_RULE_PUBLISH");
        assertThat(service).doesNotContain("UPDATE sales_order");
        assertThat(service).doesNotContain("UPDATE service_record");
        assertThat(service).doesNotContain("UPDATE commission_rule_version SET rate_value");
    }
}
