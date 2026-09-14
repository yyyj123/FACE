package com.face.platform.v3.packageaccount;

import com.face.platform.idempotency.CommandIdempotencyService;
import com.face.platform.packageaccount.MemberAccountApplicationService;
import com.face.platform.packageaccount.PackageAccountService;
import com.face.platform.packageaccount.PackageCatalogService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.api.V3ApiException;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import com.face.platform.v3.auth.SessionTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class V3PackageAccountController {

    private final PackageCatalogService packageCatalogService;
    private final PackageAccountService packageAccountService;
    private final MemberAccountApplicationService memberAccountService;
    private final CommandIdempotencyService idempotencyService;

    public V3PackageAccountController(
        PackageCatalogService packageCatalogService,
        PackageAccountService packageAccountService,
        MemberAccountApplicationService memberAccountService,
        CommandIdempotencyService idempotencyService
    ) {
        this.packageCatalogService = packageCatalogService;
        this.packageAccountService = packageAccountService;
        this.memberAccountService = memberAccountService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/package-products")
    public V3ApiResponse<List<Map<String, Object>>> packageProducts(
        @RequestParam(name = "shop_id") long shopId,
        @RequestParam(defaultValue = "ALL") String status,
        HttpServletRequest request
    ) {
        return success(
            packageCatalogService.list(principal(request), shopId, status),
            request
        );
    }

    @PostMapping("/package-products")
    public V3ApiResponse<Map<String, Object>> createPackageProduct(
        @Valid @RequestBody PackageProductBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredIdempotencyKey(idempotencyKey);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            "PACKAGE_PRODUCT_CREATE",
            hash(body),
            () -> result[0] = packageCatalogService.create(
                principal,
                body.shop_id(),
                body.package_code(),
                body.name(),
                body.description(),
                body.sale_price(),
                body.validity_days(),
                body.status(),
                body.items().stream()
                    .map(item -> new PackageCatalogService.PackageItemInput(
                        item.service_id(), item.quantity()
                    ))
                    .toList()
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
            request
        );
    }

    @PatchMapping("/package-products/{packageProductId}")
    public V3ApiResponse<Map<String, Object>> updatePackageProduct(
        @PathVariable long packageProductId,
        @Valid @RequestBody PackageProductUpdateBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredIdempotencyKey(idempotencyKey);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            "PACKAGE_PRODUCT_UPDATE",
            hash(packageProductId, body),
            () -> result[0] = packageCatalogService.update(
                principal,
                body.shop_id(),
                packageProductId,
                body.name(),
                body.description(),
                body.sale_price(),
                body.validity_days(),
                body.status(),
                body.items().stream()
                    .map(item -> new PackageCatalogService.PackageItemInput(
                        item.service_id(), item.quantity()
                    ))
                    .toList(),
                body.version()
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
            request
        );
    }

    @GetMapping("/members/{memberId}/packages")
    public V3ApiResponse<List<Map<String, Object>>> memberPackages(
        @PathVariable long memberId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            packageAccountService.memberPackages(principal(request), shopId, memberId),
            request
        );
    }

    @PostMapping("/members/{memberId}/packages")
    public V3ApiResponse<Map<String, Object>> issuePackage(
        @PathVariable long memberId,
        @Valid @RequestBody PackageIssueBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        return success(
            packageAccountService.issue(
                principal(request),
                body.shop_id(),
                memberId,
                body.package_product_id(),
                body.source_order_id(),
                key,
                hash(memberId, body)
            ),
            request
        );
    }

    @GetMapping("/package-instances/{instanceId}/ledger")
    public V3ApiResponse<List<Map<String, Object>>> packageLedger(
        @PathVariable long instanceId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            packageAccountService.ledger(principal(request), shopId, instanceId),
            request
        );
    }

    @PostMapping("/package-instances/{instanceId}/write-offs")
    public V3ApiResponse<Map<String, Object>> writeOffPackage(
        @PathVariable long instanceId,
        @Valid @RequestBody PackageWriteOffBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        return success(
            packageAccountService.writeOff(
                principal(request),
                body.shop_id(),
                instanceId,
                body.service_record_id(),
                body.service_id(),
                body.quantity(),
                body.version(),
                key,
                hash(instanceId, body),
                body.reason()
            ),
            request
        );
    }

    @PostMapping("/package-ledger/{ledgerId}/reversals")
    public V3ApiResponse<Map<String, Object>> reversePackageWriteOff(
        @PathVariable long ledgerId,
        @Valid @RequestBody ReversalBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        return success(
            packageAccountService.reverse(
                principal(request),
                body.shop_id(),
                ledgerId,
                body.version(),
                key,
                hash(ledgerId, body),
                body.reason()
            ),
            request
        );
    }

    @PostMapping("/package-instances/{instanceId}/freeze")
    public V3ApiResponse<Map<String, Object>> freezePackage(
        @PathVariable long instanceId,
        @Valid @RequestBody VersionedShopBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return packageStatus(
            instanceId, body, idempotencyKey, "FROZEN", "PACKAGE_FREEZE", request
        );
    }

    @PostMapping("/package-instances/{instanceId}/unfreeze")
    public V3ApiResponse<Map<String, Object>> unfreezePackage(
        @PathVariable long instanceId,
        @Valid @RequestBody VersionedShopBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return packageStatus(
            instanceId, body, idempotencyKey, "ACTIVE", "PACKAGE_UNFREEZE", request
        );
    }

    @GetMapping("/members/{memberId}/accounts")
    public V3ApiResponse<List<Map<String, Object>>> memberAccounts(
        @PathVariable long memberId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            memberAccountService.accounts(principal(request), shopId, memberId),
            request
        );
    }

    @GetMapping("/member-accounts/{accountId}/ledger")
    public V3ApiResponse<List<Map<String, Object>>> memberAccountLedger(
        @PathVariable long accountId,
        @RequestParam(name = "shop_id") long shopId,
        HttpServletRequest request
    ) {
        return success(
            memberAccountService.ledger(principal(request), shopId, accountId),
            request
        );
    }

    @PostMapping("/member-accounts/{accountId}/credits")
    public V3ApiResponse<Map<String, Object>> creditMemberAccount(
        @PathVariable long accountId,
        @Valid @RequestBody AccountEntryBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return postAccountEntry(
            accountId, body, idempotencyKey, true, request
        );
    }

    @PostMapping("/member-accounts/{accountId}/debits")
    public V3ApiResponse<Map<String, Object>> debitMemberAccount(
        @PathVariable long accountId,
        @Valid @RequestBody AccountEntryBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return postAccountEntry(
            accountId, body, idempotencyKey, false, request
        );
    }

    @PostMapping("/member-account-ledger/{ledgerId}/reversals")
    public V3ApiResponse<Map<String, Object>> reverseMemberAccountEntry(
        @PathVariable long ledgerId,
        @Valid @RequestBody ReversalBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        return success(
            memberAccountService.reverse(
                principal(request),
                body.shop_id(),
                ledgerId,
                body.version(),
                key,
                hash(ledgerId, body),
                body.reason()
            ),
            request
        );
    }

    @PostMapping("/member-accounts/{accountId}/freeze")
    public V3ApiResponse<Map<String, Object>> freezeMemberAccount(
        @PathVariable long accountId,
        @Valid @RequestBody VersionedShopBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return accountStatus(
            accountId, body, idempotencyKey, "FROZEN", "MEMBER_ACCOUNT_FREEZE", request
        );
    }

    @PostMapping("/member-accounts/{accountId}/unfreeze")
    public V3ApiResponse<Map<String, Object>> unfreezeMemberAccount(
        @PathVariable long accountId,
        @Valid @RequestBody VersionedShopBody body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        return accountStatus(
            accountId, body, idempotencyKey, "ACTIVE", "MEMBER_ACCOUNT_UNFREEZE", request
        );
    }

    private V3ApiResponse<Map<String, Object>> packageStatus(
        long instanceId,
        VersionedShopBody body,
        String idempotencyKey,
        String status,
        String operation,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredIdempotencyKey(idempotencyKey);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            operation,
            hash(instanceId, status, body),
            () -> result[0] = packageAccountService.changeStatus(
                principal, body.shop_id(), instanceId, body.version(), status
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
            request
        );
    }

    private V3ApiResponse<Map<String, Object>> postAccountEntry(
        long accountId,
        AccountEntryBody body,
        String idempotencyKey,
        boolean credit,
        HttpServletRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        String entryType = body.entry_type();
        if (entryType == null || entryType.isBlank()) {
            entryType = credit ? "MANUAL_CREDIT" : "MANUAL_DEBIT";
        }
        return success(
            memberAccountService.postManualEntry(
                principal(request),
                body.shop_id(),
                accountId,
                credit,
                entryType,
                body.amount(),
                body.reference_type(),
                body.reference_id(),
                key,
                hash(accountId, credit, body),
                body.remark(),
                body.version()
            ),
            request
        );
    }

    private V3ApiResponse<Map<String, Object>> accountStatus(
        long accountId,
        VersionedShopBody body,
        String idempotencyKey,
        String status,
        String operation,
        HttpServletRequest request
    ) {
        TenantPrincipal principal = principal(request);
        String key = requiredIdempotencyKey(idempotencyKey);
        final Map<String, Object>[] result = new Map[1];
        idempotencyService.run(
            principal,
            key,
            operation,
            hash(accountId, status, body),
            () -> result[0] = memberAccountService.changeStatus(
                principal, body.shop_id(), accountId, body.version(), status
            )
        );
        return success(
            result[0] == null ? Map.of("accepted", true) : result[0],
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

    private String hash(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : value.toString();
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        return SessionTokenCodec.sha256(canonical.toString());
    }

    private <T> V3ApiResponse<T> success(T data, HttpServletRequest request) {
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }

    public record PackageItemBody(
        @Positive long service_id,
        @NotNull @Positive BigDecimal quantity
    ) {
    }

    public record PackageProductBody(
        @Positive long shop_id,
        @NotBlank String package_code,
        @NotBlank String name,
        String description,
        @NotNull @PositiveOrZero BigDecimal sale_price,
        @Positive int validity_days,
        String status,
        @NotEmpty @Valid List<PackageItemBody> items
    ) {
    }

    public record PackageProductUpdateBody(
        @Positive long shop_id,
        @NotBlank String name,
        String description,
        @NotNull @PositiveOrZero BigDecimal sale_price,
        @Positive int validity_days,
        @NotBlank String status,
        @NotEmpty @Valid List<PackageItemBody> items,
        @PositiveOrZero int version
    ) {
    }

    public record PackageIssueBody(
        @Positive long shop_id,
        @Positive long package_product_id,
        @Positive long source_order_id
    ) {
    }

    public record PackageWriteOffBody(
        @Positive long shop_id,
        @Positive long service_record_id,
        @Positive long service_id,
        @NotNull @Positive BigDecimal quantity,
        @PositiveOrZero int version,
        String reason
    ) {
    }

    public record VersionedShopBody(
        @Positive long shop_id,
        @PositiveOrZero int version
    ) {
    }

    public record ReversalBody(
        @Positive long shop_id,
        @PositiveOrZero int version,
        @NotBlank String reason
    ) {
    }

    public record AccountEntryBody(
        @Positive long shop_id,
        String entry_type,
        @NotNull @Positive BigDecimal amount,
        String reference_type,
        Long reference_id,
        String remark,
        @PositiveOrZero int version
    ) {
    }
}
