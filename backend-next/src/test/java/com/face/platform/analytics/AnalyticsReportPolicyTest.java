package com.face.platform.analytics;

import com.face.platform.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsReportPolicyTest {

    @Test
    void validatesSalesOverviewCsvRequestAgainstExistingAnalyticsRules() {
        AnalyticsReportPolicy.ValidatedRequest request = AnalyticsReportPolicy.validate(
            2L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 28),
            "product",
            "csv"
        );

        assertThat(request.shopId()).isEqualTo(2L);
        assertThat(request.fromDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(request.toDate()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(request.itemType()).isEqualTo("PRODUCT");
        assertThat(request.format()).isEqualTo("CSV");
        assertThat(request.reportType()).isEqualTo("SALES_OVERVIEW");
    }

    @Test
    void rejectsUnsupportedFormatWithoutFallingBackToFakeSuccess() {
        assertThatThrownBy(() -> AnalyticsReportPolicy.validate(
            null,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 28),
            "ALL",
            "XLSX"
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
