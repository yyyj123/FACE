package com.face.platform.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSurfaceGuardFilterTest {

    private final AdminSurfaceGuardFilter filter = new AdminSurfaceGuardFilter();

    @Test
    void mobileAndUnmarkedRequestsCannotReachDesktopOnlyAdministration() throws Exception {
        for (String path : List.of(
            "/api/v3/legacy-import/template",
            "/api/v3/admin-accounts",
            "/api/v3/integrations/clients",
            "/api/v3/payment-settings/channels",
            "/api/v3/system-initialization/run",
            "/api/v3/bulk-assets/adjustments"
        )) {
            assertForbidden(path, null);
            assertForbidden(path, "MOBILE");
        }
    }

    @Test
    void desktopCanReachRestrictedRoutesAndMobileCanReachWorkbench() throws Exception {
        assertPassed("/api/v3/legacy-import/template", "DESKTOP");
        assertPassed("/api/v3/admin-accounts", "desktop");
        assertPassed("/api/v3/operations/workbench", "MOBILE");
    }

    private void assertForbidden(String path, String surface) throws ServletException, IOException {
        MockHttpServletRequest request = request(path, surface);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus(), path);
        assertTrue(response.getContentAsString().contains("ADMIN_SURFACE_FORBIDDEN"), path);
    }

    private void assertPassed(String path, String surface) throws ServletException, IOException {
        MockHttpServletRequest request = request(path, surface);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertNotNull(chain.getRequest(), path);
    }

    private MockHttpServletRequest request(String path, String surface) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (surface != null) request.addHeader(AdminSurfaceGuardFilter.SURFACE_HEADER, surface);
        return request;
    }
}
