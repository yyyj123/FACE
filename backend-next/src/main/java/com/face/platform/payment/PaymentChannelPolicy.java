package com.face.platform.payment;

import java.util.Locale;
import java.util.Set;

public final class PaymentChannelPolicy {

    private static final Set<String> LOCAL = Set.of("CASH", "BALANCE");
    private static final Set<String> EXTERNAL =
        Set.of("CARD", "WECHAT", "ALIPAY", "SANDBOX", "DEMO_MOCK", "AGGREGATOR");

    private PaymentChannelPolicy() {
    }

    public static String executionMode(String paymentMethod) {
        String normalized = normalize(paymentMethod);
        if ("ZERO_AMOUNT".equals(normalized)) return "ZERO_AMOUNT";
        if (LOCAL.contains(normalized)) return "LOCAL_LEDGER";
        if (EXTERNAL.contains(normalized)) return "EXTERNAL_ADAPTER";
        throw new IllegalArgumentException("不支持的支付方式");
    }

    public static String callbackTarget(String currentStatus, String callbackStatus) {
        String current = normalize(currentStatus);
        String target = normalize(callbackStatus);
        if (!Set.of("SUCCESS", "FAILED").contains(target)) {
            throw new IllegalArgumentException("不支持的支付回调状态");
        }
        if ("PENDING".equals(current) || current.equals(target)) return target;
        throw new IllegalArgumentException("支付终态不能被不同结果覆盖");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("支付状态或方式不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
