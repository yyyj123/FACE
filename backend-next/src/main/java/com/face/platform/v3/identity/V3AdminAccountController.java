package com.face.platform.v3.identity;

import com.face.platform.identity.AdminAccountApplicationService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/admin-accounts")
public class V3AdminAccountController {

    private final AdminAccountApplicationService service;

    public V3AdminAccountController(AdminAccountApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public V3ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return V3ApiResponse.success(
            service.list(V3RequestSupport.principal(request)),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody CreateRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.create(
                V3RequestSupport.principal(request), body.username(), body.display_name(),
                body.password(), body.role()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/{accountId}/deactivate")
    public V3ApiResponse<Map<String, Object>> deactivate(
        @PathVariable long accountId,
        @Valid @RequestBody VersionRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.deactivate(V3RequestSupport.principal(request), accountId, body.version()),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/{accountId}/reset-password")
    public V3ApiResponse<Map<String, Object>> resetPassword(
        @PathVariable long accountId,
        @Valid @RequestBody ResetPasswordRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.resetPassword(
                V3RequestSupport.principal(request), accountId, body.version(), body.password()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    public record CreateRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(max = 80) String display_name,
        @NotBlank @Size(min = 8, max = 200) String password,
        @NotBlank String role
    ) {
    }

    public record VersionRequest(@Min(1) int version) {
    }

    public record ResetPasswordRequest(
        @Min(1) int version,
        @NotBlank @Size(min = 8, max = 200) String password
    ) {
    }
}
