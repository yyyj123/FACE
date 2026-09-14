package com.face.platform.servicecare;

import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceRecordCorrectionServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantAccessService tenantAccessService = mock(TenantAccessService.class);
    private final ServiceRecordCorrectionService service = new ServiceRecordCorrectionService(
        jdbcTemplate,
        tenantAccessService,
        new ObjectMapper()
    );

    @Test
    void completedCareIsCorrectedByAppendingANewFactWithoutUpdatingOriginalRows() {
        TenantPrincipal principal = principal();
        when(tenantAccessService.requireShopPermission(
            principal,
            2L,
            "service_record:correct"
        )).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), eq(100L), eq(1L), eq(2L)))
            .thenReturn(List.of(Map.of("status", "COMPLETED", "version", 1)));
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq("correct-100")))
            .thenReturn(List.of());
        when(jdbcTemplate.update(
            anyString(),
            any(Object[].class)
        )).thenReturn(1);

        service.append(
            principal,
            100L,
            new CareCorrectionRequest(
                2L,
                1,
                "补充居家护理建议",
                Map.of("homeCareAdvice", "晚间加强保湿"),
                "correct-100"
            )
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), any(Object[].class));
        assertThat(String.join("\n", sql.getAllValues()).toUpperCase())
            .contains("INSERT INTO SERVICE_RECORD_CORRECTION")
            .doesNotContain("UPDATE CARE_RECORD")
            .doesNotContain("UPDATE SERVICE_RECORD");
    }

    private TenantPrincipal principal() {
        return new TenantPrincipal(
            10L,
            1L,
            2L,
            "manager",
            List.of("MANAGER"),
            Set.of(),
            Set.of(2L),
            false
        );
    }
}
