package com.face.platform.v3.api;

import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;

public final class V3RequestSupport {

    private V3RequestSupport() {
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return "unavailable";
    }

    public static TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof TenantPrincipal principal) {
            return principal;
        }
        throw new V3ApiException(
            org.springframework.http.HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "登录状态已失效，请重新登录"
        );
    }

    public static String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            if (!token.isBlank()) {
                return token;
            }
        }
        throw new V3ApiException(
            org.springframework.http.HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "登录状态已失效，请重新登录"
        );
    }
}
