package com.face.platform.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;

public final class StockBatchFefoPolicy {

    private static final String SQL_ORDER_BY =
        "expiry_date IS NULL, expiry_date, created_at, id";

    private StockBatchFefoPolicy() {
    }

    public static Comparator<BatchCandidate> comparator() {
        return Comparator
            .comparing(
                BatchCandidate::expiryDate,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparing(BatchCandidate::createdAt)
            .thenComparingLong(BatchCandidate::id);
    }

    public static String sqlOrderBy() {
        return SQL_ORDER_BY;
    }

    public record BatchCandidate(long id, LocalDate expiryDate, Instant createdAt) {
    }
}
