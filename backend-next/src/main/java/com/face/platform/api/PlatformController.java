package com.face.platform.api;

import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v2")
public class PlatformController {

    private final TenantAccessService tenantAccessService;
    private final JdbcTemplate jdbcTemplate;
    private final Optional<BuildProperties> buildProperties;

    public PlatformController(
        TenantAccessService tenantAccessService,
        JdbcTemplate jdbcTemplate,
        Optional<BuildProperties> buildProperties
    ) {
        this.tenantAccessService = tenantAccessService;
        this.jdbcTemplate = jdbcTemplate;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Integer tenants = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant", Integer.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "FACE Chain Platform");
        data.put("status", "UP");
        data.put("java", Runtime.version().feature());
        data.put("tenantCount", tenants == null ? 0 : tenants);
        buildProperties.ifPresent(properties -> data.put("version", properties.getVersion()));
        return ApiResponse.ok(data);
    }

    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> context(HttpServletRequest request) {
        TenantPrincipal principal = principal(request);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("accountId", principal.accountId());
        context.put("tenantId", principal.tenantId());
        context.put("homeShopId", principal.homeShopId());
        context.put("username", principal.username());
        context.put("roles", principal.roles());
        context.put("regionIds", principal.regionIds());
        context.put("shopIds", principal.shopIds());
        context.put("tenantWide", principal.tenantWide());
        context.put("permissions", tenantAccessService.permissionCodes(principal));
        return ApiResponse.ok(context);
    }

    @GetMapping("/shops")
    public ApiResponse<List<Map<String, Object>>> shops(HttpServletRequest request) {
        return ApiResponse.ok(tenantAccessService.listAccessibleShops(principal(request)));
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return principal;
    }
}
