package com.face.platform.analytics;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsReportSnapshotServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService accessService = mock(TenantAccessService.class);
    private final TransactionAnalyticsQueryPort analytics = mock(TransactionAnalyticsQueryPort.class);
    private final AnalyticsReportSnapshotService service = new AnalyticsReportSnapshotService(
        jdbcTemplate,
        accessService,
        analytics,
        new ObjectMapper()
    );

    @Test
    void createsReadySnapshotUsingExportPermissionAndReturnsNoContentBody() {
        TenantPrincipal principal = principal();
        when(accessService.accessibleShopIds(principal, "analytics:export"))
            .thenReturn(List.of(2L));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of());
        when(analytics.overview(
            principal,
            2L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 28),
            "ALL"
        )).thenReturn(overview());
        doAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(1);
            holder.getKeyList().add(Map.of("GENERATED_KEY", 99L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Map<String, Object> result = service.create(
            principal,
            "report-key-1",
            new AnalyticsReportSnapshotService.CreateRequest(
                2L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 28),
                "ALL",
                "CSV"
            )
        );

        verify(accessService).accessibleShopIds(principal, "analytics:export");
        verify(analytics).overview(
            principal,
            2L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 28),
            "ALL"
        );
        assertThat(result)
            .containsEntry("id", 99L)
            .containsEntry("status", "READY")
            .containsEntry("metricVersion", "M6-04-v1")
            .doesNotContainKey("content");
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestHashReturnsConflict() {
        TenantPrincipal principal = principal();
        when(accessService.accessibleShopIds(principal, "analytics:export"))
            .thenReturn(List.of(2L));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of(
                "id", 88L,
                "request_hash", "different-request-hash",
                "status", "READY"
            )));

        assertThatThrownBy(() -> service.create(
            principal,
            "report-key-1",
            new AnalyticsReportSnapshotService.CreateRequest(
                2L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 28),
                "ALL",
                "CSV"
            )
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void downloadsSnapshotWhenMysqlReturnsDatetimeAsLocalDateTime() {
        TenantPrincipal principal = principal();
        byte[] content = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'};
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 99L),
                Map.entry("shop_ids_json", "[2]"),
                Map.entry("content", content),
                Map.entry("content_sha256", "a".repeat(64)),
                Map.entry("expires_at", LocalDateTime.now().plusDays(1))
            )));
        when(accessService.accessibleShopIds(principal, "analytics:export"))
            .thenReturn(List.of(2L));

        AnalyticsReportSnapshotService.Download download = service.download(principal, 99L);

        assertThat(download.content()).isEqualTo(content);
        assertThat(download.reportId()).isEqualTo(99L);
    }

    @Test
    void hidesSnapshotMetadataAfterItsShopExportPermissionIsRevoked() {
        TenantPrincipal principal = principal();
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of(
                "id", 99L,
                "shop_ids_json", "[2]",
                "status", "READY"
            )));
        when(accessService.accessibleShopIds(principal, "analytics:export"))
            .thenReturn(List.of(3L));

        assertThatThrownBy(() -> service.detail(principal, 99L))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private TenantPrincipal principal() {
        return new TenantPrincipal(
            10L,
            1L,
            2L,
            "owner",
            List.of("OWNER"),
            Set.of(),
            Set.of(2L),
            false
        );
    }

    private Map<String, Object> overview() {
        return Map.ofEntries(
            Map.entry("fromDate", LocalDate.of(2026, 7, 1)),
            Map.entry("toDate", LocalDate.of(2026, 7, 28)),
            Map.entry("itemType", "ALL"),
            Map.entry("shopIds", List.of(2L)),
            Map.entry("asOf", Instant.parse("2026-08-01T08:00:00Z")),
            Map.entry("dataQuality", Map.of(
                "metricVersion", "M6-04-v1",
                "refundAllocation", "ORDER_LEVEL_ONLY"
            )),
            Map.entry("summary", Map.of(
                "orderCount", 3,
                "consumingCustomerCount", 2,
                "collectedAmount", new BigDecimal("288.00"),
                "refundedAmount", BigDecimal.ZERO,
                "netCollectedAmount", new BigDecimal("288.00")
            )),
            Map.entry("comparison", Map.of(
                "previousFromDate", LocalDate.of(2026, 6, 3),
                "previousToDate", LocalDate.of(2026, 6, 30),
                "previousSummary", Map.of("netCollectedAmount", BigDecimal.ZERO)
            )),
            Map.entry("shopBreakdown", List.of()),
            Map.entry("ranking", List.of()),
            Map.entry("trend", List.of())
        );
    }
}
