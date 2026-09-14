package com.face.platform.v3.purchase;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.inventory.InventoryReceiptApplicationService;
import com.face.platform.purchase.PurchaseOrderApplicationService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3PurchaseController {

    private final PurchaseOrderApplicationService purchaseService;
    private final InventoryReceiptApplicationService inventoryReceiptService;
    private final CommandIdempotencyService idempotencyService;

    public V3PurchaseController(
        PurchaseOrderApplicationService purchaseService,
        InventoryReceiptApplicationService inventoryReceiptService,
        CommandIdempotencyService idempotencyService
    ) {
        this.purchaseService = purchaseService;
        this.inventoryReceiptService = inventoryReceiptService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/purchase-orders")
    public V3ApiResponse<Map<String, Object>> purchaseOrders(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
        HttpServletRequest request
    ) {
        return success(
            purchaseService.list(principal(request), shopId, status, page, pageSize),
            request
        );
    }

    @GetMapping("/purchase-orders/{purchaseOrderId}")
    public V3ApiResponse<Map<String, Object>> purchaseOrder(
        @PathVariable long purchaseOrderId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            purchaseService.detail(principal(request), shopId, purchaseOrderId),
            request
        );
    }

    @PostMapping("/purchase-orders")
    public V3ApiResponse<Map<String, Object>> createPurchaseOrder(
        @Valid @RequestBody PurchaseOrderCreateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        List<PurchaseOrderApplicationService.OrderLine> lines = body.lines().stream()
            .map(line -> new PurchaseOrderApplicationService.OrderLine(
                line.product_id(),
                line.quantity(),
                line.unit_cost()
            ))
            .toList();
        return success(
            purchaseService.create(
                principal(request),
                body.shop_id(),
                body.supplier_name(),
                body.expected_date(),
                body.currency_code(),
                body.remark(),
                lines,
                key,
                RequestHash.of(
                    body.shop_id(),
                    body.supplier_name(),
                    body.expected_date(),
                    body.currency_code(),
                    body.remark(),
                    body.lines()
                )
            ),
            request
        );
    }

    @PostMapping("/purchase-orders/{purchaseOrderId}/submit")
    public V3ApiResponse<Map<String, Object>> submitPurchaseOrder(
        @PathVariable long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderVersionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            requiredIdempotencyKey(idempotencyKey),
            "PURCHASE_ORDER_SUBMIT",
            RequestHash.of(purchaseOrderId, body.shop_id(), body.version()),
            () -> result[0] = purchaseService.submit(
                principal, body.shop_id(), purchaseOrderId, body.version()
            )
        );
        return success(
            result[0] == null
                ? purchaseService.detail(principal, body.shop_id(), purchaseOrderId)
                : result[0],
            request
        );
    }

    @PostMapping("/purchase-orders/{purchaseOrderId}/decision")
    public V3ApiResponse<Map<String, Object>> decidePurchaseOrder(
        @PathVariable long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderDecisionBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            requiredIdempotencyKey(idempotencyKey),
            "PURCHASE_ORDER_DECISION",
            RequestHash.of(
                purchaseOrderId,
                body.shop_id(),
                body.version(),
                body.action(),
                body.decision_note()
            ),
            () -> result[0] = purchaseService.decide(
                principal,
                body.shop_id(),
                purchaseOrderId,
                body.version(),
                body.action(),
                body.decision_note()
            )
        );
        return success(
            result[0] == null
                ? purchaseService.detail(principal, body.shop_id(), purchaseOrderId)
                : result[0],
            request
        );
    }

    @PostMapping("/purchase-orders/{purchaseOrderId}/receipts")
    public V3ApiResponse<Map<String, Object>> receivePurchaseOrder(
        @PathVariable long purchaseOrderId,
        @Valid @RequestBody PurchaseReceiptBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        List<PurchaseOrderApplicationService.ReceiptLine> lines = body.lines().stream()
            .map(line -> new PurchaseOrderApplicationService.ReceiptLine(
                line.purchase_order_item_id(),
                line.location_id(),
                line.received_quantity(),
                line.unit_cost(),
                line.vendor_batch_no(),
                line.produced_date(),
                line.expiry_date()
            ))
            .toList();
        return success(
            purchaseService.receive(
                principal(request),
                body.shop_id(),
                purchaseOrderId,
                body.version(),
                body.received_at(),
                body.remark(),
                lines,
                key,
                RequestHash.of(
                    purchaseOrderId,
                    body.shop_id(),
                    body.version(),
                    body.received_at(),
                    body.remark(),
                    body.lines()
                )
            ),
            request
        );
    }

    @GetMapping("/inventory/batches")
    public V3ApiResponse<List<Map<String, Object>>> inventoryBatches(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(name = "product_id", required = false) Long productId,
        @RequestParam(name = "location_id", required = false) Long locationId,
        HttpServletRequest request
    ) {
        return success(
            inventoryReceiptService.batches(
                principal(request), shopId, productId, locationId
            ),
            request
        );
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        return V3RequestSupport.principal(request);
    }

    private String requiredIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 80) {
            throw new V3ApiException(
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED",
                "关键写操作必须提供有效的 Idempotency-Key"
            );
        }
        return value.trim();
    }

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record PurchaseOrderCreateBody(
        @NotNull @Positive Long shop_id,
        @NotBlank @Size(max = 160) String supplier_name,
        LocalDate expected_date,
        @Size(min = 3, max = 3) String currency_code,
        @Size(max = 500) String remark,
        @NotEmpty @Size(max = 100) List<@Valid PurchaseOrderLineBody> lines
    ) {
    }

    public record PurchaseOrderLineBody(
        @NotNull @Positive Long product_id,
        @NotNull
        @DecimalMin(value = "0.001")
        @Digits(integer = 11, fraction = 3)
        BigDecimal quantity,
        @NotNull
        @DecimalMin(value = "0.0000")
        @Digits(integer = 10, fraction = 4)
        BigDecimal unit_cost
    ) {
    }

    public record PurchaseOrderVersionBody(
        @NotNull @Positive Long shop_id,
        @NotNull Integer version
    ) {
    }

    public record PurchaseOrderDecisionBody(
        @NotNull @Positive Long shop_id,
        @NotNull Integer version,
        @NotBlank @Size(max = 20) String action,
        @Size(max = 500) String decision_note
    ) {
    }

    public record PurchaseReceiptBody(
        @NotNull @Positive Long shop_id,
        @NotNull Integer version,
        Instant received_at,
        @Size(max = 500) String remark,
        @NotEmpty @Size(max = 100) List<@Valid PurchaseReceiptLineBody> lines
    ) {
    }

    public record PurchaseReceiptLineBody(
        @NotNull @Positive Long purchase_order_item_id,
        @NotNull @Positive Long location_id,
        @NotNull
        @DecimalMin(value = "0.001")
        @Digits(integer = 11, fraction = 3)
        BigDecimal received_quantity,
        @NotNull
        @DecimalMin(value = "0.0000")
        @Digits(integer = 10, fraction = 4)
        BigDecimal unit_cost,
        @Size(max = 100) String vendor_batch_no,
        LocalDate produced_date,
        LocalDate expiry_date
    ) {
    }
}
