package com.face.platform.identity;

import java.util.Locale;
import java.util.Set;

public final class SmsVerificationPolicy {

    public static final int MAX_ATTEMPTS = 5;
    private static final Set<String> PURPOSES = Set.of("REGISTER_LOGIN", "PASSWORD_RESET");

    private SmsVerificationPolicy() {
    }

    public static String normalizePhone(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("请输入正确的11位手机号");
        }
        return normalized;
    }

    public static String normalizePurpose(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!PURPOSES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的验证码用途");
        }
        return normalized;
    }

    public static String demoCode(boolean demoProfile) {
        if (!demoProfile) {
            throw new IllegalStateException("fixed code is available only in the demo profile");
        }
        return "888888";
    }
}
