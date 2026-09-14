package com.face.platform.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@Profile("demo")
public class DemoAccessService {

    static final Duration SESSION_TTL = Duration.ofHours(12);

    private final byte[] passwordDigest;
    private final byte[] cookieSecret;
    private final Clock clock;

    @Autowired
    public DemoAccessService(
        @Value("${face.demo.access-password}") String accessPassword,
        @Value("${face.demo.cookie-secret}") String cookieSecret
    ) {
        this(accessPassword, cookieSecret, Clock.systemUTC());
    }

    DemoAccessService(String accessPassword, String cookieSecret, Clock clock) {
        if (accessPassword == null || accessPassword.length() < 12) {
            throw new IllegalStateException("FACE_DEMO_ACCESS_PASSWORD must contain at least 12 characters");
        }
        if (cookieSecret == null || cookieSecret.length() < 32) {
            throw new IllegalStateException("FACE_DEMO_COOKIE_SECRET must contain at least 32 characters");
        }
        this.passwordDigest = sha256(accessPassword);
        this.cookieSecret = cookieSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    boolean passwordMatches(String candidate) {
        byte[] candidateDigest = sha256(candidate == null ? "" : candidate);
        return MessageDigest.isEqual(passwordDigest, candidateDigest);
    }

    String createToken() {
        String payload = Long.toString(clock.instant().plus(SESSION_TTL).getEpochSecond());
        return payload + "." + sign(payload);
    }

    boolean tokenValid(String token) {
        if (token == null || token.isBlank()) return false;
        int separator = token.indexOf('.');
        if (separator < 1 || separator == token.length() - 1) return false;
        String payload = token.substring(0, separator);
        String signature = token.substring(separator + 1);
        try {
            long expiresAt = Long.parseLong(payload);
            if (!Instant.ofEpochSecond(expiresAt).isAfter(clock.instant())) return false;
            return MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cookieSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign demo access cookie", exception);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
