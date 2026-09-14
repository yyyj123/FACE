package com.face.platform.v3.integration;

import com.face.platform.integration.IntegrationClientApplicationService;
import com.face.platform.integration.IntegrationClientFilter;
import com.face.platform.integration.IntegrationPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/open/v1/catalog")
public class V3OpenCatalogController {

    private final IntegrationClientApplicationService service;

    public V3OpenCatalogController(IntegrationClientApplicationService service) {
        this.service = service;
    }

    @GetMapping("/services")
    public V3ApiResponse<List<Map<String, Object>>> services(HttpServletRequest request) {
        Object value = request.getAttribute(IntegrationClientFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof IntegrationPrincipal principal)) {
            throw new V3ApiException(HttpStatus.UNAUTHORIZED, "INTEGRATION_AUTH_FAILED", "集成客户端身份无效");
        }
        return V3ApiResponse.success(service.catalog(principal), V3RequestSupport.requestId(request));
    }
}
