package com.face.platform.v3.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M4ApiContractDocumentTest {

    @Test
    void contractDefinesAssetsFundsRoutesAndHardRules() throws Exception {
        String contract = Files.readString(
            Path.of("../docs/architecture/api-contract-m4.md"),
            StandardCharsets.UTF_8
        );

        assertThat(contract).contains("/package-instances/{id}/write-offs");
        assertThat(contract).contains("/package-ledger/{ledgerId}/reversals");
        assertThat(contract).contains("/member-account-ledger/{ledgerId}/reversals");
        assertThat(contract).contains("/payments/{id}/refunds");
        assertThat(contract).contains("/purchase-orders/{id}/receipts");
        assertThat(contract).contains("/reconciliation-batches");
        assertThat(contract).contains("同键异载荷");
        assertThat(contract).contains("不改原流水");
        assertThat(contract).contains("不能直接修改订单、支付、退款、账户、套餐或库存流水");
    }
}

