package com.face.platform.masterdata;

import com.face.platform.integration.IntegrationCatalogQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IntegrationCatalogQueryAdapter implements IntegrationCatalogQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public IntegrationCatalogQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> activeServices(long tenantId, long shopId) {
        return jdbcTemplate.queryForList(
            """
            SELECT si.service_code AS serviceCode, si.name,
                   sc.name AS categoryName, si.duration_minutes AS durationMinutes,
                   CAST(si.list_price AS CHAR) AS listPrice,
                   CAST(si.member_price AS CHAR) AS memberPrice,
                   si.updated_at AS updatedAt
            FROM service_item si
            JOIN service_category sc
              ON sc.id = si.category_id AND sc.tenant_id = si.tenant_id
            WHERE si.tenant_id = ? AND si.shop_id = ?
              AND si.status = 'ACTIVE' AND sc.status = 'ACTIVE'
            ORDER BY sc.sort_order, si.name, si.id
            """,
            tenantId, shopId
        );
    }
}
