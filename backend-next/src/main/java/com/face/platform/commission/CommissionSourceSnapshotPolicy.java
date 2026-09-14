package com.face.platform.commission;

import com.face.platform.idempotency.RequestHash;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommissionSourceSnapshotPolicy {

    private CommissionSourceSnapshotPolicy() {
    }

    public static String hash(
        String sourceType,
        long sourceId,
        long staffId,
        BigDecimal baseAmount,
        Instant occurredAt,
        Map<String, Object> facts
    ) {
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("来源类型不能为空");
        }
        if (sourceId <= 0 || staffId <= 0) {
            throw new IllegalArgumentException("来源或员工标识不正确");
        }
        if (baseAmount == null || baseAmount.signum() < 0) {
            throw new IllegalArgumentException("提成基数不能为负数或空值");
        }
        if (occurredAt == null) throw new IllegalArgumentException("业务发生时间不能为空");
        return RequestHash.of(
            sourceType.trim().toUpperCase(Locale.ROOT),
            sourceId,
            staffId,
            baseAmount.setScale(2).toPlainString(),
            occurredAt,
            canonical(facts == null ? Map.of() : facts)
        );
    }

    private static String canonical(Object value) {
        if (value == null) return "<null>";
        if (value instanceof Map<?, ?> map) {
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            StringBuilder result = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : entries) {
                result.append(canonical(String.valueOf(entry.getKey())))
                    .append('=')
                    .append(canonical(entry.getValue()))
                    .append(';');
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder result = new StringBuilder("[");
            for (Object item : iterable) result.append(canonical(item)).append(';');
            return result.append(']').toString();
        }
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return value.toString();
    }
}
