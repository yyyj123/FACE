package com.face.platform.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class SandboxPaymentChannelAdapter implements PaymentChannelAdapter {

    private final String secret;

    public SandboxPaymentChannelAdapter(
        @Value("${face.payment.sandbox-secret:}") String secret
    ) {
        this.secret = secret == null ? "" : secret.trim();
    }

    @Override
    public String channelCode() {
        return "SANDBOX";
    }

    @Override
    public boolean configured() {
        return !secret.isBlank();
    }

    @Override
    public PaymentInitiation initiate(
        String paymentNo,
        BigDecimal amount,
        String currencyCode
    ) {
        requireConfigured();
        return new PaymentInitiation(
            "SBX-REQ-" + UUID.randomUUID().toString().replace("-", ""),
            "AWAITING_CALLBACK"
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
            "SBX-RF-" + UUID.randomUUID().toString().replace("-", ""),
            "SANDBOX_CONFIRMED",
            true
        );
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException("SANDBOX 支付通道未配置");
    }
}

