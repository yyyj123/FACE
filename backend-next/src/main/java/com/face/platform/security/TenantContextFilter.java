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
import java.time.Instant;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = TenantPrincipal.class.getName();

    private final TenantAccessService tenantAccessService;

    public TenantContextFilter(TenantAccessService tenantAccessService) {
        this.tenantAccessService = tenantAccessService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || path.startsWith("/actuator/")
            || path.equals("/api/v2/health")
            || path.equals("/api/v3/health/readiness")
            || path.startsWith("/upload/")
            || path.startsWith("/api/v2/auth/")
            || path.startsWith("/api/v2/client/auth/")
            || path.startsWith("/api/v2/client/public/")
            || path.equals("/api/v3/auth/login")
            || path.equals("/api/v3/auth/admin-login")
            || path.equals("/api/v3/auth/refresh")
            || path.startsWith("/api/v3/client/identity/")
            || path.startsWith("/api/v3/demo/access/")
            || path.startsWith("/api/v3/open/")
            || (
                "POST".equalsIgnoreCase(request.getMethod())
                && path.startsWith("/api/v3/payment-channels/")
                && path.endsWith("/callbacks")
            );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = request.getHeader("Token");
        if ((token == null || token.isBlank()) && request.getHeader("Authorization") != null) {
            String authorization = request.getHeader("Authorization");
            if (authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean v3 = path.startsWith("/api/v3/");
        var principal = v3
            ? tenantAccessService.resolveV3(token)
            : tenantAccessService.resolve(token);
        if (principal.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (v3) {
                Object requestIdValue = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
                String requestId = requestIdValue instanceof String value ? value : "unavailable";
                response.getWriter().write(
                    "{\"code\":\"UNAUTHENTICATED\","
                        + "\"message\":\"登录状态已失效，请重新登录\","
                        + "\"data\":null,"
                        + "\"request_id\":\"" + requestId + "\","
                        + "\"timestamp\":\"" + Instant.now() + "\"}"
                );
            } else {
                response.getWriter().write(
                    "{\"code\":401,\"msg\":\"登录状态已失效，请重新登录\",\"data\":null}"
                );
            }
            return;
        }

        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal.orElseThrow());
        filterChain.doFilter(request, response);
    }
}
