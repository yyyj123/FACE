package com.face.platform.masterdata;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class StaffPublicProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final ShopContextService shopContextService;

    public StaffPublicProfileService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        ShopContextService shopContextService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.shopContextService = shopContextService;
    }

    public List<Map<String, Object>> publicProfiles(Long serviceId) {
        var shop = shopContextService.requirePublicShop(null);
        String skillFilter = serviceId == null
            ? ""
            : " AND EXISTS (SELECT 1 FROM staff_service filter_skill WHERE filter_skill.staff_id = st.id AND filter_skill.service_id = ? AND filter_skill.enabled = 1)";
        if (serviceId == null) {
            return query(shop.tenantId(), shop.shopId(), skillFilter);
        }
        return query(shop.tenantId(), shop.shopId(), skillFilter, serviceId);
    }

    private List<Map<String, Object>> query(
        long tenantId,
        long shopId,
        String skillFilter,
        Object... skillArgs
    ) {
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(shopId);
        args.add(tenantId);
        args.addAll(List.of(skillArgs));
        return jdbcTemplate.queryForList(
            """
            SELECT st.id, st.name, st.job_role AS jobRole, st.level_name AS levelName,
                   st.avatar_url AS avatarUrl, st.bio,
                   GROUP_CONCAT(DISTINCT si.name ORDER BY si.name SEPARATOR '、') AS specialties
            FROM staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
             AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
             AND ssa.effective_from <= CURRENT_DATE
             AND (ssa.effective_to IS NULL OR ssa.effective_to >= CURRENT_DATE)
            LEFT JOIN staff_service ss ON ss.staff_id = st.id AND ss.enabled = 1
            LEFT JOIN service_item si
              ON si.id = ss.service_id AND si.tenant_id = st.tenant_id AND si.status = 'ACTIVE'
            WHERE st.tenant_id = ? AND st.status = 'ACTIVE'
            %s
            GROUP BY st.id, st.name, st.job_role, st.level_name, st.avatar_url, st.bio
            ORDER BY st.name, st.id
            """.formatted(skillFilter),
            args.toArray()
        );
    }

    @Transactional
    public Map<String, Object> update(
        TenantPrincipal principal,
        long staffId,
        String jobRole,
        String levelName,
        String avatarUrl,
        String bio
    ) {
        var shop = shopContextService.requireTenantShop(principal.tenantId(), null);
        tenantAccessService.requireShopPermission(principal, shop.shopId(), "staff:manage");
        String safeJobRole = required(jobRole, "岗位名称不能为空", 30);
        int changed = jdbcTemplate.update(
            """
            UPDATE staff st
            JOIN staff_shop_assignment ssa
              ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
            SET st.job_role = ?, st.level_name = ?, st.avatar_url = ?, st.bio = ?
            WHERE st.id = ? AND st.tenant_id = ? AND ssa.shop_id = ?
              AND ssa.status = 'ACTIVE'
            """,
            safeJobRole,
            optional(levelName, 50),
            optional(avatarUrl, 500),
            optional(bio, 1000),
            staffId,
            principal.tenantId(),
            shop.shopId()
        );
        if (changed != 1) throw new ApiException(HttpStatus.NOT_FOUND, "员工公开资料不存在");
        jdbcTemplate.update(
            """
            INSERT INTO audit_log
                (tenant_id, shop_id, account_id, action, entity_type, entity_id)
            VALUES (?, ?, ?, 'STAFF_PUBLIC_PROFILE_UPDATE', 'STAFF', ?)
            """,
            principal.tenantId(), shop.shopId(), principal.accountId(), staffId
        );
        return Map.of("id", staffId, "updated", true);
    }

    private String required(String value, String message, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "公开资料字段过长");
        return normalized;
    }
}
