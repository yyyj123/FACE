package com.face.platform.v3.content;

import com.face.platform.content.ContentApplicationService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class V3ContentController {

    private final ContentApplicationService service;

    public V3ContentController(ContentApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/v3/open/content/home")
    public V3ApiResponse<Map<String, Object>> home(HttpServletRequest request) {
        return V3ApiResponse.success(service.publicHome(), V3RequestSupport.requestId(request));
    }

    @GetMapping("/api/v3/content")
    public V3ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return V3ApiResponse.success(
            service.list(V3RequestSupport.principal(request)),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/content")
    public V3ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody DraftRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.create(V3RequestSupport.principal(request), body.toDraft()),
            V3RequestSupport.requestId(request)
        );
    }

    @PutMapping("/api/v3/content/{contentId}")
    public V3ApiResponse<Map<String, Object>> update(
        @PathVariable long contentId,
        @Valid @RequestBody UpdateRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.update(
                V3RequestSupport.principal(request), contentId, body.version(), body.toDraft()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    @PostMapping("/api/v3/content/{contentId}/status")
    public V3ApiResponse<Map<String, Object>> status(
        @PathVariable long contentId,
        @Valid @RequestBody StatusRequest body,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.changeStatus(
                V3RequestSupport.principal(request), contentId, body.version(),
                body.status(), body.scheduled_at()
            ),
            V3RequestSupport.requestId(request)
        );
    }

    public record DraftRequest(
        @NotBlank String content_type,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String summary,
        @Size(max = 5000) String body,
        @Size(max = 500) String image_url,
        @Size(max = 30) String target_type,
        @Size(max = 255) String target_value,
        @Min(0) int sort_order
    ) {
        ContentApplicationService.ContentDraft toDraft() {
            return new ContentApplicationService.ContentDraft(
                content_type, title, summary, body, image_url,
                target_type, target_value, sort_order
            );
        }
    }

    public record UpdateRequest(
        @Min(1) int version,
        @NotBlank String content_type,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String summary,
        @Size(max = 5000) String body,
        @Size(max = 500) String image_url,
        @Size(max = 30) String target_type,
        @Size(max = 255) String target_value,
        @Min(0) int sort_order
    ) {
        ContentApplicationService.ContentDraft toDraft() {
            return new ContentApplicationService.ContentDraft(
                content_type, title, summary, body, image_url,
                target_type, target_value, sort_order
            );
        }
    }

    public record StatusRequest(
        @Min(1) int version,
        @NotBlank String status,
        Instant scheduled_at
    ) {
    }
}
