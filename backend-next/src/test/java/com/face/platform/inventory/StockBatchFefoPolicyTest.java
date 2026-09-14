package com.face.platform.inventory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockBatchFefoPolicyTest {

    @Test
    void sortsByExpiryFirstAndPutsUndatedBatchesLastWithStableTieBreakers() {
        Instant created = Instant.parse("2026-07-30T00:00:00Z");
        var undated = new StockBatchFefoPolicy.BatchCandidate(1L, null, created.minusSeconds(60));
        var later = new StockBatchFefoPolicy.BatchCandidate(
            2L, LocalDate.of(2027, 12, 1), created
        );
        var sameExpiryLaterId = new StockBatchFefoPolicy.BatchCandidate(
            4L, LocalDate.of(2027, 6, 1), created
        );
        var sameExpiryEarlierId = new StockBatchFefoPolicy.BatchCandidate(
            3L, LocalDate.of(2027, 6, 1), created
        );

        List<StockBatchFefoPolicy.BatchCandidate> batches = new ArrayList<>(
            List.of(undated, later, sameExpiryLaterId, sameExpiryEarlierId)
        );
        batches.sort(StockBatchFefoPolicy.comparator());

        assertThat(batches)
            .extracting(StockBatchFefoPolicy.BatchCandidate::id)
            .containsExactly(3L, 4L, 2L, 1L);
        assertThat(StockBatchFefoPolicy.sqlOrderBy())
            .containsIgnoringCase("expiry_date IS NULL")
            .containsIgnoringCase("expiry_date")
            .containsIgnoringCase("created_at")
            .containsIgnoringCase("id");
    }
}
