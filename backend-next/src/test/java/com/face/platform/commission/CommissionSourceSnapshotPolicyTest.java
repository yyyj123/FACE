package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionSourceSnapshotPolicyTest {

    @Test
    void snapshotHashIsOrderIndependentAndChangesWithFinancialFacts() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("serviceName", "合成护理项目");
        first.put("durationMinutes", 60);
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("durationMinutes", 60);
        reordered.put("serviceName", "合成护理项目");

        String firstHash = CommissionSourceSnapshotPolicy.hash(
            "SERVICE",
            101,
            201,
            new BigDecimal("168.00"),
            Instant.parse("2026-07-30T08:00:00Z"),
            first
        );
        String reorderedHash = CommissionSourceSnapshotPolicy.hash(
            "SERVICE",
            101,
            201,
            new BigDecimal("168.00"),
            Instant.parse("2026-07-30T08:00:00Z"),
            reordered
        );
        String changedAmountHash = CommissionSourceSnapshotPolicy.hash(
            "SERVICE",
            101,
            201,
            new BigDecimal("169.00"),
            Instant.parse("2026-07-30T08:00:00Z"),
            first
        );

        assertThat(firstHash).hasSize(64).isEqualTo(reorderedHash);
        assertThat(changedAmountHash).isNotEqualTo(firstHash);
    }
}
