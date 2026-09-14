package com.face.platform.v3.platform;

import com.face.platform.api.DatabaseContractHealthIndicator;
import com.face.platform.audit.DataAccessAuditService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3PlatformController {

    private final TenantAccessService tenantAccessService;
    private final DataAccessAuditService auditService;
    private final DatabaseContractHealthIndicator databaseHealth;
    private final ShopContextService shopContextService;

    public V3PlatformController(
        TenantAccessService tenantAccessService,
        DataAccessAuditService auditService,
        DatabaseContractHealthIndicator databaseHealth,
        ShopContextService shopContextService
    ) {
        this.tenantAccessService = tenantAccessService;
        this.auditService = auditService;
        this.databaseHealth = databaseHealth;
        this.shopContextService = shopContextService;
    }

    @GetMapping("/me/context")
    public V3ApiResponse<Map<String, Object>> context(HttpServletRequest request) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("account_id", principal.accountId());
        data.put("tenant_id", principal.tenantId());
        data.put("home_shop_id", principal.homeShopId());
        data.put("username", principal.username());
        data.put("roles", principal.roles());
        data.put("region_ids", principal.regionIds());
        data.put("shop_ids", principal.shopIds());
        data.put("tenant_wide", principal.tenantWide());
        data.put("permissions", tenantAccessService.permissionCodes(principal));
        auditService.recordView(
            principal,
            principal.homeShopId(),
            "TENANT_CONTEXT",
            String.valueOf(principal.accountId()),
            requestId
        );
        return V3ApiResponse.success(data, requestId);
    }

    @GetMapping("/shops")
    public V3ApiResponse<List<Map<String, Object>>> shops(HttpServletRequest request) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        List<Map<String, Object>> shops = tenantAccessService.listAccessibleShops(principal);
        if (shopContextService.singleShop()) {
            long defaultShopId = shopContextService.requireTenantShop(principal.tenantId(), null).shopId();
            shops = shops.stream()
                .filter(shop -> ((Number) shop.get("id")).longValue() == defaultShopId)
                .toList();
        }
        auditService.recordView(principal, null, "SHOP_SCOPE", null, requestId);
        return V3ApiResponse.success(shops, requestId);
    }

    @GetMapping("/health/readiness")
    public ResponseEntity<V3ApiResponse<Map<String, Object>>> readiness(
        HttpServletRequest request
    ) {
        String requestId = V3RequestSupport.requestId(request);
        Health health = databaseHealth.health();
        if (!Status.UP.equals(health.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new V3ApiResponse<>(
                    "DEPENDENCY_UNAVAILABLE",
                    "数据库或迁移契约未就绪",
                    Map.of("status", "DOWN", "database", "DOWN"),
                    requestId,
                    java.time.Instant.now().toString()
                )
            );
        }
        return ResponseEntity.ok(V3ApiResponse.success(
            Map.of("status", "UP", "database", "UP"),
            requestId
        ));
    }
}
