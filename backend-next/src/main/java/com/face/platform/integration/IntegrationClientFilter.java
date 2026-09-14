package com.face.platform.integration;

import com.face.platform.api.ApiException;
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
public class IntegrationClientFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = IntegrationPrincipal.class.getName();
    private final IntegrationClientApplicationService service;

    public IntegrationClientFilter(IntegrationClientApplicationService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v3/open/v1/catalog/")
            || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {
        IntegrationPrincipal principal = null;
        try {
            String authorization = request.getHeader("Authorization");
            String secret = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : null;
            if (secret == null || secret.isBlank()) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "缺少集成客户端密钥");
            String timestamp = request.getHeader("X-Integration-Timestamp");
            if (timestamp == null || timestamp.isBlank()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "缺少开放请求时间戳");
            }
            String requestId = requestId(request);
            principal = service.authenticate(
                request.getHeader("X-Integration-Client"), secret,
                Instant.parse(timestamp),
                request.getHeader("X-Integration-Nonce"), requestId,
                request.getMethod(), request.getRequestURI(), "catalog:read"
            );
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            chain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR", "开放请求时间戳格式不正确", requestId(request));
        } catch (ApiException exception) {
            writeError(response, exception.status().value(),
                exception.status().value() == 429 ? "RATE_LIMITED" : "INTEGRATION_AUTH_FAILED",
                exception.getMessage(), requestId(request));
        } finally {
            if (principal != null) service.finishRequest(principal, response.getStatus());
        }
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value instanceof String id && !id.isBlank() ? id : "unavailable";
    }

    private void writeError(HttpServletResponse response, int status, String code, String message, String requestId) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safeMessage = message == null ? "开放请求验证失败" : message.replace("\"", "");
        response.getWriter().write(
            "{\"code\":\"" + code + "\",\"message\":\"" + safeMessage
                + "\",\"data\":null,\"request_id\":\"" + requestId
                + "\",\"timestamp\":\"" + Instant.now() + "\"}"
        );
    }
}
