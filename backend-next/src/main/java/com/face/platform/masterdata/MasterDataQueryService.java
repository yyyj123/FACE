package com.face.platform.masterdata;

import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class MasterDataQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public MasterDataQueryService(JdbcTemplate jdbcTemplate, TenantAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<Map<String, Object>> services(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "service:view");
        return jdbcTemplate.queryForList(
            """
            SELECT si.id,
                   si.service_code AS serviceCode,
                   si.name,
                   si.cover_url AS coverUrl,
                   si.category_id AS categoryId,
                   sc.name AS categoryName,
                   si.duration_minutes AS durationMinutes,
                   si.cleanup_minutes AS cleanupMinutes,
                   si.list_price AS listPrice,
                   si.member_price AS memberPrice,
                   si.status,
                   si.version,
                   si.updated_at AS updatedAt
            FROM service_item si
            LEFT JOIN service_category sc
              ON sc.id = si.category_id
             AND sc.tenant_id = si.tenant_id
            WHERE si.tenant_id = ?
              AND si.shop_id = ?
            ORDER BY sc.sort_order, si.name, si.id
            """,
            principal.tenantId(),
            shopId
        );
    }

    public List<Map<String, Object>> staff(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "staff:view");
        LocalDate today = LocalDate.now();
        return jdbcTemplate.queryForList(
            """
            SELECT st.id,
                   st.staff_no AS staffNo,
                   st.name,
                   st.job_role AS jobRole,
                   st.level_name AS levelName,
                   st.phone,
                   st.status,
                   GROUP_CONCAT(
                       CASE WHEN ssv.enabled = 1 THEN ssv.service_id END
                       ORDER BY ssv.service_id
                   ) AS serviceIds
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id
             AND ssa.tenant_id = st.tenant_id
             AND ssa.shop_id = ?
             AND ssa.status = 'ACTIVE'
             AND ssa.effective_from <= ?
             AND (ssa.effective_to IS NULL OR ssa.effective_to >= ?)
            LEFT JOIN staff_skill_version ssv
              ON ssv.staff_id = st.id
             AND ssv.tenant_id = st.tenant_id
             AND ssv.effective_to IS NULL
            WHERE st.tenant_id = ?
            GROUP BY st.id, st.staff_no, st.name, st.job_role, st.level_name, st.phone, st.status
            ORDER BY st.name, st.id
            """,
            shopId,
            today,
            today,
            principal.tenantId()
        );
    }
}
