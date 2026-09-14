package com.face.platform.v3.identity;

import com.face.platform.identity.IdentityApplicationService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v3/client/identity")
public class V3ClientIdentityController {

    private final IdentityApplicationService identityService;

    public V3ClientIdentityController(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/sms/request")
    public V3ApiResponse<Map<String, Object>> requestSms(
        @Valid @RequestBody SmsRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            identityService.requestSms(body.phone(), body.purpose()),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/password-login")
    public V3ApiResponse<Map<String, Object>> passwordLogin(
        @Valid @RequestBody PasswordLoginRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            identityService.passwordLogin(body.phone(), body.password()).toMap(),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/sms-login")
    public V3ApiResponse<Map<String, Object>> smsLogin(
        @Valid @RequestBody SmsLoginRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            identityService.smsLogin(body.phone(), body.code()).toMap(),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/register")
    public V3ApiResponse<Map<String, Object>> register(
        @Valid @RequestBody RegisterRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            identityService.register(body.phone(), body.code(), body.password(), body.name()).toMap(),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/password-reset")
    public V3ApiResponse<Map<String, Object>> resetPassword(
        @Valid @RequestBody PasswordResetRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            identityService.resetPassword(body.phone(), body.code(), body.password()),
            V3RequestSupport.requestId(request)
        );
    }

    public record SmsRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank String purpose
    ) {
    }

    public record PasswordLoginRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Size(max = 200) String password
    ) {
    }

    public record SmsLoginRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}") String code
    ) {
    }

    public record RegisterRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotBlank @Size(min = 8, max = 200) String password,
        @Size(max = 80) String name
    ) {
    }

    public record PasswordResetRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotBlank @Size(min = 8, max = 200) String password
    ) {
    }
}
