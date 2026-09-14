package com.face.platform.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "face.payment.demo-mock.enabled", havingValue = "true")
public class DemoMockPaymentChannelAdapter implements PaymentChannelAdapter {

    private final String secret;

    public DemoMockPaymentChannelAdapter(
        @Value("${face.payment.demo-mock.secret:}") String secret
    ) {
        this.secret = secret == null ? "" : secret.trim();
    }

    @Override
    public String channelCode() {
        return "DEMO_MOCK";
    }

    @Override
    public boolean configured() {
        return !secret.isBlank();
    }

    @Override
    public PaymentInitiation initiate(String paymentNo, BigDecimal amount, String currencyCode) {
        requireConfigured();
        return new PaymentInitiation(
            "DEMO-REQ-" + UUID.randomUUID().toString().replace("-", ""),
            "AWAITING_DEMO_CALLBACK"
        );
    }

    @Override
    public boolean verifyCallback(
        long timestamp,
        String signature,
        String rawBody,
        Instant now
    ) {
        return configured()
            && PaymentCallbackVerifier.verify(secret, timestamp, signature, rawBody, now);
    }

    @Override
    public RefundResult refund(
        String paymentNo,
        String externalTransactionNo,
        String refundNo,
        BigDecimal amount
    ) {
        requireConfigured();
        return new RefundResult(
            "DEMO-RF-" + UUID.randomUUID().toString().replace("-", ""),
            "DEMO_CONFIRMED",
            true
        );
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException("DEMO_MOCK 支付通道未配置");
    }
}
