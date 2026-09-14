package com.face.platform.client;

import com.face.platform.api.ApiException;
import com.face.platform.api.ApiResponse;
import com.face.platform.appointment.AppointmentCreateRequest;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.shop.ShopContextService;
import com.face.platform.servicecare.CustomerConfirmationActionRequest;
import com.face.platform.servicecare.CustomerConfirmationService;
import com.face.platform.servicecare.ServiceCompleteRequest;
import com.face.platform.servicecare.ServiceRecordService;
import com.face.platform.servicecare.ServiceStartRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/client")
public class ClientController {

    private final ClientAuthService authService;
    private final ClientCatalogService catalogService;
    private final ClientPortalService portalService;
    private final ServiceRecordService serviceRecordService;
    private final CustomerConfirmationService confirmationService;
    private final ShopContextService shopContextService;

    public ClientController(
        ClientAuthService authService,
        ClientCatalogService catalogService,
        ClientPortalService portalService,
        ServiceRecordService serviceRecordService,
        CustomerConfirmationService confirmationService,
        ShopContextService shopContextService
    ) {
        this.authService = authService;
        this.catalogService = catalogService;
        this.portalService = portalService;
        this.serviceRecordService = serviceRecordService;
        this.confirmationService = confirmationService;
        this.shopContextService = shopContextService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.login(body));
    }

    @PostMapping("/auth/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        throw new com.face.platform.api.ApiException(
            org.springframework.http.HttpStatus.GONE,
            "旧注册入口已停用，请使用需要短信验证的新注册入口"
        );
    }

    @GetMapping("/public/banners")
    public ApiResponse<List<Map<String, Object>>> banners(
        @RequestParam(required = false) Long shopId
    ) {
        long resolvedShopId = shopContextService.requirePublicShop(shopId).shopId();
        return ApiResponse.ok(catalogService.banners(resolvedShopId));
    }

    @GetMapping("/public/service-categories")
    public ApiResponse<List<Map<String, Object>>> categories(
        @RequestParam(required = false) Long shopId
    ) {
        long resolvedShopId = shopContextService.requirePublicShop(shopId).shopId();
        return ApiResponse.ok(catalogService.categories(resolvedShopId));
    }

    @GetMapping("/public/services")
    public ApiResponse<List<Map<String, Object>>> services(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Boolean featured,
        @RequestParam(required = false) String sort
    ) {
        long resolvedShopId = shopContextService.requirePublicShop(shopId).shopId();
        return ApiResponse.ok(catalogService.services(resolvedShopId, categoryId, featured, sort));
    }

    @GetMapping("/public/services/{serviceId}")
    public ApiResponse<Map<String, Object>> service(@PathVariable long serviceId) {
        long shopId = shopContextService.requirePublicShop(null).shopId();
        return ApiResponse.ok(catalogService.service(shopId, serviceId));
    }

    @PostMapping("/public/services/{serviceId}/view")
    public ApiResponse<Boolean> recordServiceView(@PathVariable long serviceId) {
        long shopId = shopContextService.requirePublicShop(null).shopId();
        return ApiResponse.ok(catalogService.recordServiceView(shopId, serviceId));
    }

    @GetMapping("/public/staff")
    public ApiResponse<List<Map<String, Object>>> staff(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false) Long serviceId
    ) {
        long resolvedShopId = shopContextService.requirePublicShop(shopId).shopId();
        return ApiResponse.ok(catalogService.staff(resolvedShopId, serviceId));
    }

    @GetMapping("/public/availability")
    public ApiResponse<Map<String, Object>> availability(
        @RequestParam(required = false) Long shopId,
        @RequestParam long staffId,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        long resolvedShopId = shopContextService.requirePublicShop(shopId).shopId();
        return ApiResponse.ok(catalogService.availability(resolvedShopId, staffId, date));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(portalService.profile(principal(request)));
    }

    @PutMapping("/me")
    public ApiResponse<Map<String, Object>> updateMe(
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(portalService.updateProfile(principal(request), body));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        return ApiResponse.ok(portalService.dashboard(principal(request)));
    }

    @GetMapping("/appointments")
    public ApiResponse<List<Map<String, Object>>> appointments(
        @RequestParam(required = false) String status,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(portalService.appointments(
            principal(request), status, date, 100
        ));
    }

    @PostMapping("/appointments")
    public ApiResponse<Map<String, Object>> createAppointment(
        @Valid @RequestBody AppointmentCreateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(portalService.createAppointment(principal(request), body));
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public ApiResponse<Map<String, Object>> cancelAppointment(
        @PathVariable long appointmentId,
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(portalService.cancelAppointment(
            principal(request), appointmentId, integer(body, "version")
        ));
    }

    @PostMapping("/appointments/{appointmentId}/status")
    public ApiResponse<Map<String, Object>> updateAppointmentStatus(
        @PathVariable long appointmentId,
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(portalService.updateAppointmentStatus(
            principal(request),
            appointmentId,
            text(body, "status"),
            integer(body, "version"),
            text(body, "reason")
        ));
    }

    @GetMapping("/service-records/resources")
    public ApiResponse<Map<String, Object>> serviceResources(
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.resources(principal(request), shopId)
        );
    }

    @GetMapping("/service-records/{serviceRecordId}")
    public ApiResponse<Map<String, Object>> serviceRecord(
        @PathVariable long serviceRecordId,
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.detail(principal(request), shopId, serviceRecordId)
        );
    }

    @PostMapping("/service-records/start")
    public ApiResponse<Map<String, Object>> startService(
        @Valid @RequestBody ServiceStartRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(serviceRecordService.start(principal(request), body));
    }

    @PostMapping("/service-records/{serviceRecordId}/complete")
    public ApiResponse<Map<String, Object>> completeService(
        @PathVariable long serviceRecordId,
        @Valid @RequestBody ServiceCompleteRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            serviceRecordService.complete(principal(request), serviceRecordId, body)
        );
    }

    @GetMapping("/confirmations")
    public ApiResponse<List<Map<String, Object>>> confirmations(
        HttpServletRequest request
    ) {
        return ApiResponse.ok(confirmationService.listMine(principal(request)));
    }

    @PostMapping("/confirmations/{confirmationId}/action")
    public ApiResponse<Map<String, Object>> actOnConfirmation(
        @PathVariable long confirmationId,
        @Valid @RequestBody CustomerConfirmationActionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            confirmationService.act(principal(request), confirmationId, body)
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return principal;
    }

    private Integer integer(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " 参数不正确");
        }
    }

    private String text(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        return value == null ? null : value.toString().trim();
    }
}
