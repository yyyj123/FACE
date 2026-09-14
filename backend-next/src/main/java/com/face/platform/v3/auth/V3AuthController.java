package com.face.platform.v3.auth;

import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/auth")
public class V3AuthController {

    private final V3AuthSessionService sessionService;

    public V3AuthController(V3AuthSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public V3ApiResponse<Map<String, Object>> login(
        @Valid @RequestBody LoginRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            response(sessionService.login(body.username(), body.password())),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/admin-login")
    public V3ApiResponse<Map<String, Object>> adminLogin(
        @Valid @RequestBody LoginRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            response(sessionService.loginAdmin(body.username(), body.password())),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/refresh")
    public V3ApiResponse<Map<String, Object>> refresh(
        @Valid @RequestBody RefreshRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            response(sessionService.refresh(body.refresh_token())),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/logout")
    public V3ApiResponse<Map<String, Object>> logout(HttpServletRequest request) {
        sessionService.logout(V3RequestSupport.bearerToken(request));
        return V3ApiResponse.success(
            Map.of("revoked", true),
            V3RequestSupport.requestId(request)
        );
    }

    private Map<String, Object> response(V3AuthSessionService.SessionTokens tokens) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", tokens.sessionId());
        data.put("access_token", tokens.accessToken());
        data.put("token_type", "Bearer");
        data.put("access_expires_at", tokens.accessExpiresAt().toString());
        data.put("refresh_token", tokens.refreshToken());
        data.put("refresh_expires_at", tokens.refreshExpiresAt().toString());
        return data;
    }

    public record LoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = 80, message = "用户名长度不能超过 80 个字符")
        String username,
        @NotBlank(message = "请输入密码")
        @Size(max = 200, message = "密码长度不正确")
        String password
    ) {
    }

    public record RefreshRequest(
        @NotBlank(message = "缺少刷新令牌")
        @Size(max = 200, message = "刷新令牌长度不正确")
        String refresh_token
    ) {
    }
}
