package com.face.platform.audit;

import com.face.platform.security.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataAccessAuditService {

    private final JdbcTemplate jdbcTemplate;

    public DataAccessAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordView(
        TenantPrincipal principal,
        Long shopId,
        String resourceType,
        String resourceId,
        String requestId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO data_access_log (
                tenant_id,
                shop_id,
                account_id,
                resource_type,
                resource_id,
                action,
                result,
                request_id,
                ip_address
            ) VALUES (?, ?, ?, ?, ?, 'VIEW', 'ALLOWED', ?, NULL)
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            resourceType,
            resourceId,
            requestId
        );
    }
}
