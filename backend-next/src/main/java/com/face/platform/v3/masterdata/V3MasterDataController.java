package com.face.platform.v3.masterdata;

import com.face.platform.audit.DataAccessAuditService;
import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.masterdata.MasterDataQueryService;
import com.face.platform.masterdata.ServiceCatalogService;
import com.face.platform.masterdata.StaffScheduleService;
import com.face.platform.masterdata.StaffSkillVersionService;
import com.face.platform.resource.ServiceResourceService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import com.face.platform.v3.auth.SessionTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3MasterDataController {

    private final MasterDataQueryService queryService;
    private final ServiceCatalogService serviceCatalogService;
    private final ServiceResourceService resourceService;
    private final StaffSkillVersionService skillService;
    private final StaffScheduleService scheduleService;
    private final CommandIdempotencyService idempotencyService;
    private final DataAccessAuditService accessAuditService;

    public V3MasterDataController(
        MasterDataQueryService queryService,
        ServiceCatalogService serviceCatalogService,
        ServiceResourceService resourceService,
        StaffSkillVersionService skillService,
        StaffScheduleService scheduleService,
        CommandIdempotencyService idempotencyService,
        DataAccessAuditService accessAuditService
    ) {
        this.queryService = queryService;
        this.serviceCatalogService = serviceCatalogService;
        this.resourceService = resourceService;
        this.skillService = skillService;
        this.scheduleService = scheduleService;
        this.idempotencyService = idempotencyService;
        this.accessAuditService = accessAuditService;
    }

    @GetMapping("/services")
    public V3ApiResponse<List<Map<String, Object>>> services(
        @RequestParam("shop_id") long shopId,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        List<Map<String, Object>> data = queryService.services(principal, shopId);
        accessAuditService.recordView(principal, shopId, "SERVICE_CATALOG", null, requestId);
        return V3ApiResponse.success(data, requestId);
    }

    @GetMapping("/staff")
    public V3ApiResponse<List<Map<String, Object>>> staff(
        @RequestParam("shop_id") long shopId,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        List<Map<String, Object>> data = queryService.staff(principal, shopId);
        accessAuditService.recordView(principal, shopId, "STAFF_DIRECTORY", null, requestId);
        return V3ApiResponse.success(data, requestId);
    }

    @PutMapping("/services/{serviceId}")
    public V3ApiResponse<Map<String, Object>> updateService(
        @PathVariable long serviceId,
        @Valid @RequestBody ServiceUpdateRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "SERVICE_CATALOG_UPDATE",
            hash(
                serviceId, body.shop_id(), body.name(), body.duration_minutes(),
                body.cleanup_minutes(), body.list_price(), body.member_price(),
                body.cover_url(), body.status(), body.version()
            ),
            () -> serviceCatalogService.update(
                principal,
                body.shop_id(),
                serviceId,
                body.name(),
                body.cover_url(),
                body.duration_minutes(),
                body.cleanup_minutes(),
                body.list_price(),
                body.member_price(),
                body.status(),
                body.version()
            )
        );
        return accepted(request);
    }

    @GetMapping("/resources")
    public V3ApiResponse<List<Map<String, Object>>> resources(
        @RequestParam("shop_id") long shopId,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        List<Map<String, Object>> data = resourceService.list(principal, shopId);
        accessAuditService.recordView(principal, shopId, "SERVICE_RESOURCE", null, requestId);
        return V3ApiResponse.success(data, requestId);
    }

    @PostMapping("/resources")
    public V3ApiResponse<Map<String, Object>> createResource(
        @Valid @RequestBody ResourceCreateRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "RESOURCE_CREATE",
            hash(
                body.shop_id(), body.resource_code(), body.resource_name(),
                body.resource_type(), body.capacity()
            ),
            () -> resourceService.create(
                principal,
                body.shop_id(),
                body.resource_code(),
                body.resource_name(),
                body.resource_type(),
                body.capacity()
            )
        );
        return accepted(request);
    }

    @PutMapping("/resources/{resourceId}")
    public V3ApiResponse<Map<String, Object>> updateResource(
        @PathVariable long resourceId,
        @Valid @RequestBody ResourceUpdateRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "RESOURCE_UPDATE",
            hash(
                resourceId, body.shop_id(), body.resource_code(), body.resource_name(),
                body.resource_type(), body.capacity(), body.version()
            ),
            () -> resourceService.update(
                principal,
                body.shop_id(),
                resourceId,
                body.resource_code(),
                body.resource_name(),
                body.resource_type(),
                body.capacity(),
                body.version()
            )
        );
        return accepted(request);
    }

    @PostMapping("/resources/{resourceId}/deactivate")
    public V3ApiResponse<Map<String, Object>> deactivateResource(
        @PathVariable long resourceId,
        @Valid @RequestBody VersionedShopRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "RESOURCE_DEACTIVATE",
            hash(resourceId, body.shop_id(), body.version()),
            () -> resourceService.deactivate(
                principal,
                body.shop_id(),
                resourceId,
                body.version()
            )
        );
        return accepted(request);
    }

    @GetMapping("/staff/{staffId}/skills")
    public V3ApiResponse<List<Map<String, Object>>> skills(
        @PathVariable long staffId,
        @RequestParam("shop_id") long shopId,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        List<Map<String, Object>> data = skillService.currentSkills(principal, shopId, staffId);
        accessAuditService.recordView(
            principal,
            shopId,
            "STAFF_SKILL_VERSION",
            String.valueOf(staffId),
            requestId
        );
        return V3ApiResponse.success(data, requestId);
    }

    @PutMapping("/staff/{staffId}/skills/{serviceId}")
    public V3ApiResponse<Map<String, Object>> changeSkill(
        @PathVariable long staffId,
        @PathVariable long serviceId,
        @Valid @RequestBody SkillChangeRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "STAFF_SKILL_VERSION_CHANGE",
            hash(
                staffId, serviceId, body.shop_id(), body.enabled(),
                body.custom_duration_minutes(), body.effective_from(), body.version()
            ),
            () -> skillService.changeSkill(
                principal,
                body.shop_id(),
                staffId,
                serviceId,
                body.enabled(),
                body.custom_duration_minutes(),
                body.effective_from(),
                body.version()
            )
        );
        return accepted(request);
    }

    @GetMapping("/staff/{staffId}/schedules")
    public V3ApiResponse<List<Map<String, Object>>> schedules(
        @PathVariable long staffId,
        @RequestParam("shop_id") long shopId,
        @RequestParam(name = "from", required = false) LocalDate from,
        @RequestParam(name = "to", required = false) LocalDate to,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        String requestId = V3RequestSupport.requestId(request);
        List<Map<String, Object>> data = scheduleService.list(
            principal,
            shopId,
            staffId,
            from,
            to
        );
        accessAuditService.recordView(
            principal,
            shopId,
            "STAFF_SCHEDULE",
            String.valueOf(staffId),
            requestId
        );
        return V3ApiResponse.success(data, requestId);
    }

    @PostMapping("/staff/{staffId}/schedules")
    public V3ApiResponse<Map<String, Object>> createSchedule(
        @PathVariable long staffId,
        @Valid @RequestBody ScheduleCreateRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "STAFF_SCHEDULE_CREATE",
            hash(
                staffId, body.shop_id(), body.schedule_date(), body.start_time(),
                body.end_time(), body.schedule_type(), body.remark()
            ),
            () -> scheduleService.create(
                principal,
                body.shop_id(),
                staffId,
                body.schedule_date(),
                body.start_time(),
                body.end_time(),
                body.schedule_type(),
                body.remark()
            )
        );
        return accepted(request);
    }

    @PostMapping("/staff/{staffId}/schedules/{scheduleId}/deactivate")
    public V3ApiResponse<Map<String, Object>> deactivateSchedule(
        @PathVariable long staffId,
        @PathVariable long scheduleId,
        @Valid @RequestBody VersionedShopRequest body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = V3RequestSupport.principal(request);
        idempotencyService.run(
            principal,
            idempotencyKey,
            "STAFF_SCHEDULE_DEACTIVATE",
            hash(staffId, scheduleId, body.shop_id(), body.version()),
            () -> scheduleService.deactivate(
                principal,
                body.shop_id(),
                staffId,
                scheduleId,
                body.version()
            )
        );
        return accepted(request);
    }

    private V3ApiResponse<Map<String, Object>> accepted(HttpServletRequest request) {
        return V3ApiResponse.success(
            Map.of("accepted", true),
            V3RequestSupport.requestId(request)
        );
    }

    private String hash(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : value.toString();
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        return SessionTokenCodec.sha256(canonical.toString());
    }

    public record ServiceUpdateRequest(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String cover_url,
        @Min(1) @Max(1440) int duration_minutes,
        @Min(0) @Max(1440) int cleanup_minutes,
        @NotNull @DecimalMin("0.00") BigDecimal list_price,
        @NotNull @DecimalMin("0.00") BigDecimal member_price,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @Min(1) int version
    ) {
    }

    public record ResourceCreateRequest(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 50) String resource_code,
        @NotBlank @Size(max = 100) String resource_name,
        @NotBlank @Pattern(regexp = "ROOM|EQUIPMENT") String resource_type,
        @Min(1) @Max(100) int capacity
    ) {
    }

    public record ResourceUpdateRequest(
        @NotNull Long shop_id,
        @NotBlank @Size(max = 50) String resource_code,
        @NotBlank @Size(max = 100) String resource_name,
        @NotBlank @Pattern(regexp = "ROOM|EQUIPMENT") String resource_type,
        @Min(1) @Max(100) int capacity,
        @Min(1) int version
    ) {
    }

    public record VersionedShopRequest(
        @NotNull Long shop_id,
        @Min(1) int version
    ) {
    }

    public record SkillChangeRequest(
        @NotNull Long shop_id,
        boolean enabled,
        @Min(1) Integer custom_duration_minutes,
        @NotNull LocalDateTime effective_from,
        @Min(0) int version
    ) {
    }

    public record ScheduleCreateRequest(
        @NotNull Long shop_id,
        @NotNull LocalDate schedule_date,
        LocalTime start_time,
        LocalTime end_time,
        @NotBlank @Pattern(regexp = "WORK|LEAVE|BLOCKED") String schedule_type,
        @Size(max = 500) String remark
    ) {
    }
}
