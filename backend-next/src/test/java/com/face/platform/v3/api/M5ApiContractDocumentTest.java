package com.face.platform.v3.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M5ApiContractDocumentTest {

    @Test
    void contractDefinesCommissionSettlementAfterSaleApprovalAndNotificationHardRules()
        throws Exception {
        String contract = Files.readString(
            Path.of("../docs/architecture/api-contract-m5.md"),
            StandardCharsets.UTF_8
        );
        String lifecycle = Files.readString(
            Path.of("../docs/architecture/m5-commission-settlement-aftersales-lifecycle.md"),
            StandardCharsets.UTF_8
        );
        String impact = Files.readString(
            Path.of("../docs/baseline/M5-01-commission-settlement-aftersales-gap-and-impact.md"),
            StandardCharsets.UTF_8
        );

        assertThat(contract).contains("/commission/rules/{ruleId}/publish");
        assertThat(contract).contains("/commission/entries/{entryId}/reversals");
        assertThat(contract).contains("/commission/settlements/{batchId}/close");
        assertThat(contract).contains("/after-sales/cases/{caseId}/actions");
        assertThat(contract).contains("/approvals/{approvalId}/decisions");
        assertThat(contract).contains("/notifications/{notificationId}/read");
        assertThat(contract).contains("同键异载荷");
        assertThat(contract).contains("申请人不能审批本人申请");

        assertThat(lifecycle).contains("已入账提成不可覆盖或删除");
        assertThat(lifecycle).contains("已关闭结算批次不可修改或重开");
        assertThat(lifecycle).contains("RefundCompleted");
        assertThat(lifecycle).contains("只追加新流水");

        assertThat(impact).contains("commission_rule_version");
        assertThat(impact).contains("commission_settlement_batch");
        assertThat(impact).contains("after_sale_case");
        assertThat(impact).contains("approval_instance");
        assertThat(impact).contains("Flyway 向前扩展");
        assertThat(impact).contains("禁止直接 DROP");
    }
}
