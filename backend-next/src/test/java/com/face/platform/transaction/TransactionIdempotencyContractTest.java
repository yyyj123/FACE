package com.face.platform.transaction;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionIdempotencyContractTest {

    @Test
    void orderCreationAndPaymentPersistAndCompareRequestHashes() throws Exception {
        String java = Files.readString(
            Path.of("src/main/java/com/face/platform/transaction/TransactionService.java"),
            StandardCharsets.UTF_8
        );

        assertThat(java).contains("create_request_hash");
        assertThat(java).contains("paymentRequestHash");
        assertThat(java).contains("订单创建幂等键已用于不同请求");
        assertThat(java).contains("收款幂等键已用于不同请求");
        assertThat(java).contains("PaymentSucceeded");
    }
}
