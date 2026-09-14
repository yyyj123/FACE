package com.face.platform.inventory;

import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam long shopId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "false") boolean lowStockOnly,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(inventoryService.list(
            principal(request), shopId, keyword, lowStockOnly, page, pageSize
        ));
    }

    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> resources(
        @RequestParam long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(inventoryService.resources(principal(request), shopId));
    }

    @GetMapping("/movements")
    public ApiResponse<Map<String, Object>> movements(
        @RequestParam long shopId,
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) String movementType,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(inventoryService.movements(
            principal(request), shopId, productId, movementType, page, pageSize
        ));
    }

    @GetMapping("/transfers")
    public ApiResponse<Map<String, Object>> transfers(
        @RequestParam long shopId,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(inventoryService.transfers(
            principal(request), shopId, status, page, pageSize
        ));
    }

    @PostMapping("/adjustments")
    public ApiResponse<Map<String, Object>> adjust(
        @Valid @RequestBody InventoryAdjustmentRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(inventoryService.adjust(principal(request), body));
    }

    @PostMapping("/transfers")
    public ApiResponse<Map<String, Object>> createTransfer(
        @Valid @RequestBody InventoryTransferRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(inventoryService.createTransfer(principal(request), body));
    }

    @PostMapping("/transfers/{transferId}/decision")
    public ApiResponse<Map<String, Object>> decideTransfer(
        @PathVariable long transferId,
        @Valid @RequestBody InventoryTransferDecisionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(
            inventoryService.decideTransfer(principal(request), transferId, body)
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return principal;
    }
}
