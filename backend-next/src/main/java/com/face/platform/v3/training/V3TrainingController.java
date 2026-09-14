package com.face.platform.v3.training;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.training.TrainingApplicationService;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/training")
public class V3TrainingController {

    private final TrainingApplicationService service;
    private final CommandIdempotencyService idempotency;

    public V3TrainingController(TrainingApplicationService service, CommandIdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping("/courses")
    public V3ApiResponse<List<Map<String, Object>>> courses(
        @RequestParam(name = "shop_id") long shopId, HttpServletRequest request
    ) {
        return ok(service.courses(principal(request), shopId), request);
    }

    @PostMapping("/courses")
    public V3ApiResponse<Map<String, Object>> createCourse(
        @Valid @RequestBody CourseBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(body.shop_id(), body.course_code(), body.revision(), body.title(),
            body.safe_summary(), body.pass_score(), body.validity_days());
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "TRAINING_COURSE_CREATE", hash,
            () -> result[0] = service.createCourse(principal, body.shop_id(), body.course_code(),
                body.revision(), body.title(), body.safe_summary(), body.pass_score(), body.validity_days(), safeKey));
        return ok(result[0] == null ? Map.of("replayed", true) : result[0], request);
    }

    @PostMapping("/courses/{courseId}/{action}")
    public V3ApiResponse<Map<String, Object>> courseAction(
        @PathVariable long courseId,
        @PathVariable String action,
        @Valid @RequestBody VersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        String target = switch (action) { case "publish" -> "ACTIVE"; case "retire" -> "RETIRED"; default -> throw new V3ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "课程动作不正确"); };
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(courseId, body.shop_id(), body.version(), target);
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "TRAINING_COURSE_" + target, hash,
            () -> result[0] = service.transitionCourse(principal, body.shop_id(), courseId, body.version(), target));
        return ok(result[0] == null ? Map.of("replayed", true) : result[0], request);
    }

    @GetMapping("/records")
    public V3ApiResponse<List<Map<String, Object>>> records(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return ok(service.records(principal(request), shopId, status), request);
    }

    @PostMapping("/records")
    public V3ApiResponse<Map<String, Object>> assign(
        @Valid @RequestBody AssignBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(body.shop_id(), body.course_id(), body.staff_id(), body.due_at());
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "TRAINING_RECORD_ASSIGN", hash,
            () -> result[0] = service.assign(principal, body.shop_id(), body.course_id(), body.staff_id(),
                body.due_at(), safeKey, hash));
        return ok(result[0] == null ? Map.of("replayed", true) : result[0], request);
    }

    @GetMapping("/me")
    public V3ApiResponse<List<Map<String, Object>>> me(HttpServletRequest request) {
        return ok(service.myRecords(principal(request)), request);
    }

    @PostMapping("/me/{recordId}/{action}")
    public V3ApiResponse<Map<String, Object>> selfAction(
        @PathVariable long recordId,
        @PathVariable String action,
        @Valid @RequestBody SelfActionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        String target = switch (action) { case "start" -> "IN_PROGRESS"; case "submit" -> "SUBMITTED"; default -> throw new V3ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "培训动作不正确"); };
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(recordId, body.version(), target, body.evidence_summary());
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "TRAINING_SELF_" + target, hash,
            () -> result[0] = service.selfTransition(principal, recordId, body.version(), target,
                body.evidence_summary(), safeKey, hash));
        return ok(result[0] == null ? Map.of("replayed", true) : result[0], request);
    }

    @PostMapping("/records/{recordId}/verify")
    public V3ApiResponse<Map<String, Object>> verify(
        @PathVariable long recordId,
        @Valid @RequestBody VerifyBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String key,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String safeKey = key(key);
        String hash = RequestHash.of(recordId, body.shop_id(), body.version(), body.score(), body.safe_reason());
        final Map<String, Object>[] result = new Map[1];
        idempotency.run(principal, safeKey, "TRAINING_RECORD_VERIFY", hash,
            () -> result[0] = service.verify(principal, body.shop_id(), recordId, body.version(), body.score(),
                body.safe_reason(), safeKey, hash));
        return ok(result[0] == null ? Map.of("replayed", true) : result[0], request);
    }

    private TenantPrincipal principal(HttpServletRequest request) { return V3RequestSupport.principal(request); }
    private <T> V3ApiResponse<T> ok(T data, HttpServletRequest request) { return V3ApiResponse.success(data, V3RequestSupport.requestId(request)); }
    private String key(String value) {
        if (value == null || value.isBlank() || value.length() > 80 || !value.matches("[A-Za-z0-9._:-]+"))
            throw new V3ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "关键写操作必须提供有效的 Idempotency-Key");
        return value;
    }

    public record CourseBody(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 40) String course_code,
        @Min(1) int revision,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 500) String safe_summary,
        @Min(0) @Max(100) int pass_score,
        @Min(1) @Max(3650) Integer validity_days
    ) {}
    public record VersionBody(@NotNull Long shop_id, @NotNull Integer version) {}
    public record AssignBody(@NotNull Long shop_id, @NotNull Long course_id, @NotNull Long staff_id, LocalDateTime due_at) {}
    public record SelfActionBody(@NotNull Integer version, @Size(max = 500) String evidence_summary) {}
    public record VerifyBody(@NotNull Long shop_id, @NotNull Integer version, @Min(0) @Max(100) int score, @Size(max = 500) String safe_reason) {}
}
