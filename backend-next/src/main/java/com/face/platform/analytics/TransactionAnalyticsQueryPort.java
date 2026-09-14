package com.face.platform.analytics;

import com.face.platform.security.TenantPrincipal;

import java.time.LocalDate;
import java.util.Map;

public interface TransactionAnalyticsQueryPort {

    Map<String, Object> overview(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType
    );

    Map<String, Object> lines(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType,
        int page,
        int pageSize
    );
}
