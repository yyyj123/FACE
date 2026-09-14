package com.face.platform.v3.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SessionTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenPair issue() {
        String accessToken = randomToken();
        String refreshToken = randomToken();
        return new TokenPair(
            accessToken,
            refreshToken,
            hash(accessToken),
            hash(refreshToken)
        );
    }

    public String hash(String token) {
        return sha256(token);
    }

    public static String sha256(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("令牌不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TokenPair(
        String accessToken,
        String refreshToken,
        String accessTokenHash,
        String refreshTokenHash
    ) {
    }
}
