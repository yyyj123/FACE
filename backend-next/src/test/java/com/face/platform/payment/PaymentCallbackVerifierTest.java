package com.face.platform.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCallbackVerifierTest {

    private static final String SECRET = "m4-synthetic-secret";
    private static final String BODY =
        "{\"event_id\":\"evt-001\",\"payment_no\":\"PAY-001\",\"status\":\"SUCCESS\"}";

    @Test
    void validSignatureWithinClockWindowIsAccepted() {
        long timestamp = Instant.parse("2026-07-30T02:00:00Z").getEpochSecond();
        String signature = PaymentCallbackVerifier.sign(SECRET, timestamp, BODY);

        assertThat(PaymentCallbackVerifier.verify(
            SECRET,
            timestamp,
            signature,
            BODY,
            Instant.parse("2026-07-30T02:02:00Z")
        )).isTrue();
    }

    @Test
    void invalidSignatureOrExpiredTimestampIsRejected() {
        long timestamp = Instant.parse("2026-07-30T02:00:00Z").getEpochSecond();
        String signature = PaymentCallbackVerifier.sign(SECRET, timestamp, BODY);

        assertThat(PaymentCallbackVerifier.verify(
            SECRET,
            timestamp,
            "0".repeat(64),
            BODY,
            Instant.parse("2026-07-30T02:01:00Z")
        )).isFalse();
        assertThat(PaymentCallbackVerifier.verify(
            SECRET,
            timestamp,
            signature,
            BODY,
            Instant.parse("2026-07-30T02:06:00Z")
        )).isFalse();
    }
}

