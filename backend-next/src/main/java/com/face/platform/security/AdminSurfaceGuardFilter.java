package com.face.platform.security;

import com.face.platform.v3.api.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AdminSurfaceGuardFilter extends OncePerRequestFilter {

    public static final String SURFACE_HEADER = "X-FACE-Admin-Surface";
    private static final List<String> DESKTOP_ONLY_PREFIXES = List.of(
        "/api/v3/legacy-import",
        "/api/v3/admin-accounts",
        "/api/v3/integrations/clients",
        "/api/v3/payment-settings",
        "/api/v3/system-initialization",
        "/api/v3/bulk-assets"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return DESKTOP_ONLY_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if ("DESKTOP".equalsIgnoreCase(request.getHeader(SURFACE_HEADER))) {
            filterChain.doFilter(request, response);
            return;
        }
        Object requestIdValue = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = requestIdValue instanceof String value ? value : "unavailable";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"code\":\"ADMIN_SURFACE_FORBIDDEN\","
                + "\"message\":\"该操作仅允许在桌面端运营管理平台执行\","
                + "\"data\":null,"
                + "\"request_id\":\"" + requestId + "\"}"
        );
    }
}
