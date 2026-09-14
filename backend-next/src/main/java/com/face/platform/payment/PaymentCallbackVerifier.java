package com.face.platform.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class PaymentCallbackVerifier {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private PaymentCallbackVerifier() {
    }

    public static String sign(String secret, long timestamp, String body) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("支付回调密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(
                (timestamp + "." + (body == null ? "" : body)).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("支付回调签名计算失败", exception);
        }
    }

    public static boolean verify(
        String secret,
        long timestamp,
        String signature,
        String body,
        Instant now
    ) {
        if (signature == null || signature.length() != 64 || now == null) return false;
        Instant sentAt;
        try {
            sentAt = Instant.ofEpochSecond(timestamp);
        } catch (RuntimeException exception) {
            return false;
        }
        if (Duration.between(sentAt, now).abs().compareTo(MAX_CLOCK_SKEW) > 0) return false;
        try {
            byte[] expected = HexFormat.of().parseHex(sign(secret, timestamp, body));
            byte[] actual = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

