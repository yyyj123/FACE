package com.face.platform.payment;

import java.math.BigDecimal;
import java.time.Instant;

public interface PaymentChannelAdapter {

    String channelCode();

    boolean configured();

    PaymentInitiation initiate(String paymentNo, BigDecimal amount, String currencyCode);

    boolean verifyCallback(long timestamp, String signature, String rawBody, Instant now);

    RefundResult refund(
        String paymentNo,
        String externalTransactionNo,
        String refundNo,
        BigDecimal amount
    );

    record PaymentInitiation(String channelRequestNo, String channelStatus) {
    }

    record RefundResult(String externalRefundNo, String channelStatus, boolean successful) {
    }
}

