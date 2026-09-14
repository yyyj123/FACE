package com.face.platform.analytics;

import com.face.platform.api.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsQueryPolicyTest {

    @Test
    void acceptsInclusiveNinetyThreeDayWindow() {
        AnalyticsQueryPolicy.DateRange range = AnalyticsQueryPolicy.dateRange(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 7, 2)
        );

        assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(range.toDate()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void rejectsWindowLongerThanNinetyThreeDays() {
        assertThatThrownBy(() -> AnalyticsQueryPolicy.dateRange(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 7, 3)
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("93");
    }

    @Test
    void normalizesSupportedItemTypesAndRejectsUnknownValues() {
        assertThat(AnalyticsQueryPolicy.itemType(" service ")).isEqualTo("SERVICE");
        assertThat(AnalyticsQueryPolicy.itemType(null)).isEqualTo("ALL");
        assertThatThrownBy(() -> AnalyticsQueryPolicy.itemType("PACKAGE"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("服务项目或零售商品");
    }

    @Test
    void calculatesAdjacentPreviousPeriodWithTheSameInclusiveLength() {
        AnalyticsQueryPolicy.DateRange previous = AnalyticsQueryPolicy.previousRange(
            new AnalyticsQueryPolicy.DateRange(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 28)
            )
        );

        assertThat(previous.fromDate()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(previous.toDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void calculatesPreviousDayForSingleDayPeriod() {
        AnalyticsQueryPolicy.DateRange previous = AnalyticsQueryPolicy.previousRange(
            new AnalyticsQueryPolicy.DateRange(
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 7, 28)
            )
        );

        assertThat(previous.fromDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(previous.toDate()).isEqualTo(LocalDate.of(2026, 7, 27));
    }
}
