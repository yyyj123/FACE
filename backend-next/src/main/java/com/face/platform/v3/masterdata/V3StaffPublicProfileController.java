package com.face.platform.v3.masterdata;

import com.face.platform.masterdata.StaffPublicProfileService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class V3StaffPublicProfileController {

    private final StaffPublicProfileService service;

    public V3StaffPublicProfileController(StaffPublicProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/v3/open/staff")
    public V3ApiResponse<List<Map<String, Object>>> publicProfiles(
        @RequestParam(name = "service_id", required = false) Long serviceId,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.publicProfiles(serviceId),
            V3RequestSupport.requestId(request)
        );
    }

    @PutMapping("/api/v3/staff/{staffId}/public-profile")
    public V3ApiResponse<Map<String, Object>> update(
        @PathVariable long staffId,
        @Valid @RequestBody UpdateRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.update(
                V3RequestSupport.principal(request), staffId, body.job_role(),
                body.level_name(), body.avatar_url(), body.bio()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    public record UpdateRequest(
        @NotBlank @Size(max = 30) String job_role,
        @Size(max = 50) String level_name,
        @Size(max = 500) String avatar_url,
        @Size(max = 1000) String bio
    ) {
    }
}
