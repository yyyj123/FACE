package com.face.platform.operations;

import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v3/operations")
public class OperationsWorkbenchController {

    private final OperationsWorkbenchService service;

    public OperationsWorkbenchController(OperationsWorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/workbench")
    public ApiResponse<Map<String, Object>> workbench(
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.workbench(principal(request), shopId));
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) throw new IllegalStateException("租户上下文未建立");
        return principal;
    }
}
