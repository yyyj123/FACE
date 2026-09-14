package com.face.platform.packageaccount;

import com.face.platform.api.ApiException;
import com.face.platform.member.MemberService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.servicecare.ServiceRecordService;
import com.face.platform.transaction.TransactionPackageContextService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PackageAccountService {

    private static final Set<String> SERVICE_RECORD_STATUSES =
        Set.of("IN_PROGRESS", "COMPLETED");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final MemberService memberService;
    private final PackageCatalogService packageCatalogService;
    private final TransactionPackageContextService transactionContextService;
    private final ServiceRecordService serviceRecordService;
    private final OutboxEventService outboxEventService;

    public PackageAccountService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        MemberService memberService,
        PackageCatalogService packageCatalogService,
        TransactionPackageContextService transactionContextService,
        ServiceRecordService serviceRecordService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.memberService = memberService;
        this.packageCatalogService = packageCatalogService;
        this.transactionContextService = transactionContextService;
        this.serviceRecordService = serviceRecordService;
        this.outboxEventService = outboxEventService;
    }

    public List<Map<String, Object>> memberPackages(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        requireMemberRead(principal, shopId, memberId);
        List<Map<String, Object>> instances = jdbcTemplate.queryForList(
            """
            SELECT pi.id, pi.instance_no AS instanceNo,
                   pi.package_product_id AS packageProductId,
                   pp.name AS packageName, pi.source_order_id AS sourceOrderId,
                   pi.purchase_price AS purchasePrice,
                   pi.valid_from AS validFrom, pi.valid_until AS validUntil,
                   pi.total_quantity AS totalQuantity,
                   pi.remaining_quantity AS remainingQuantity,
                   CASE
                     WHEN pi.status = 'ACTIVE' AND pi.valid_until < CURRENT_DATE
                       THEN 'EXPIRED'
                     ELSE pi.status
                   END AS status,
                   pi.version, pi.created_at AS createdAt
            FROM package_instance pi
            JOIN package_product pp ON pp.id = pi.package_product_id
            WHERE pi.tenant_id = ? AND pi.shop_id = ? AND pi.member_id = ?
            ORDER BY FIELD(pi.status, 'ACTIVE', 'FROZEN', 'EXHAUSTED', 'EXPIRED', 'CANCELLED'),
                     pi.valid_until DESC, pi.id DESC
            """,
            principal.tenantId(),
            shopId,
            memberId
        );
        for (Map<String, Object> instance : instances) {
            instance.put(
                "items",
                jdbcTemplate.queryForList(
                    """
                    SELECT id, service_id AS serviceId,
                           service_name_snapshot AS serviceName,
                           total_quantity AS totalQuantity,
                           remaining_quantity AS remainingQuantity
                    FROM package_instance_item
                    WHERE tenant_id = ? AND package_instance_id = ?
                    ORDER BY id
                    """,
                    principal.tenantId(),
                    number(instance.get("id"))
                )
            );
        }
        return instances;
    }

    public List<Map<String, Object>> ledger(
        TenantPrincipal principal,
        long shopId,
        long packageInstanceId
    ) {
        Map<String, Object> instance = requireInstance(principal, shopId, packageInstanceId, false);
        requireMemberRead(principal, shopId, number(instance.get("memberId")));
        return jdbcTemplate.queryForList(
            """
            SELECT pl.id, pl.package_instance_item_id AS packageInstanceItemId,
                   pii.service_id AS serviceId,
                   pii.service_name_snapshot AS serviceName,
                   pl.service_record_id AS serviceRecordId,
                   pl.entry_type AS entryType,
                   pl.quantity_delta AS quantityDelta,
                   pl.balance_after AS balanceAfter,
                   pl.original_ledger_id AS originalLedgerId,
                   pl.reason, pl.created_at AS createdAt
            FROM package_ledger pl
            LEFT JOIN package_instance_item pii
              ON pii.id = pl.package_instance_item_id
            WHERE pl.tenant_id = ? AND pl.package_instance_id = ?
            ORDER BY pl.created_at DESC, pl.id DESC
            LIMIT 300
            """,
            principal.tenantId(),
            packageInstanceId
        );
    }

    @Transactional
    public Map<String, Object> issue(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long packageProductId,
        long sourceOrderId,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        String safeKey = requiredKey(idempotencyKey);
        String safeHash = requiredHash(requestHash);
        Map<String, Object> replay = issueReplay(
            principal, memberId, packageProductId, sourceOrderId, safeKey, safeHash
        );
        if (replay != null) return replay;

        memberService.requireActiveMemberInShop(principal, shopId, memberId);
        Map<String, Object> product = packageCatalogService.requireActiveProduct(
            principal, shopId, packageProductId
        );
        Map<String, Object> orderLine = transactionContextService.requirePaidPackageLine(
            principal, shopId, sourceOrderId, memberId, packageProductId
        );
        ensureOrderProductNotIssued(principal, sourceOrderId, packageProductId);

        BigDecimal multiplier = quantity(orderLine.get("quantity"), "订单套餐数量");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productItems =
            (List<Map<String, Object>>) product.get("items");
        if (productItems == null || productItems.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐产品没有可发放的服务项目");
        }
        BigDecimal totalQuantity = productItems.stream()
            .map(item -> quantity(item.get("quantity"), "套餐项目次数").multiply(multiplier))
            .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
        LocalDate validFrom = businessDate();
        int validityDays = Math.toIntExact(number(product.get("validityDays")));
        LocalDate validUntil = validFrom.plusDays(validityDays - 1L);
        BigDecimal purchasePrice = money(orderLine.get("lineAmount"));

        long packageInstanceId = insertInstance(
            principal,
            shopId,
            memberId,
            packageProductId,
            sourceOrderId,
            purchasePrice,
            validFrom,
            validUntil,
            totalQuantity,
            safeKey,
            safeHash
        );
        for (Map<String, Object> item : productItems) {
            BigDecimal itemQuantity =
                quantity(item.get("quantity"), "套餐项目次数").multiply(multiplier);
            jdbcTemplate.update(
                """
                INSERT INTO package_instance_item (
                    tenant_id, package_instance_id, service_id,
                    service_name_snapshot, total_quantity, remaining_quantity
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                principal.tenantId(),
                packageInstanceId,
                number(item.get("serviceId")),
                item.get("serviceName").toString(),
                itemQuantity,
                itemQuantity
            );
        }
        long ledgerId = insertLedger(
            principal,
            shopId,
            packageInstanceId,
            null,
            null,
            "ISSUE",
            totalQuantity,
            totalQuantity,
            null,
            "ISSUE:" + packageInstanceId,
            safeKey,
            safeHash,
            "已支付订单发放套餐"
        );
        audit(
            principal, shopId, "PACKAGE_ISSUE", "PACKAGE_INSTANCE",
            packageInstanceId, Map.of("ledgerId", ledgerId, "sourceOrderId", sourceOrderId)
        );
        outboxEventService.append(
            principal,
            shopId,
            "PACKAGE_INSTANCE",
            Long.toString(packageInstanceId),
            "PackageIssued",
            Map.of(
                "packageInstanceId", packageInstanceId,
                "memberId", memberId,
                "sourceOrderId", sourceOrderId,
                "ledgerId", ledgerId
            )
        );
        return instanceResult(packageInstanceId);
    }

    @Transactional
    public Map<String, Object> writeOff(
        TenantPrincipal principal,
        long shopId,
        long packageInstanceId,
        long serviceRecordId,
        long serviceId,
        BigDecimal writeOffQuantity,
        int version,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        accessService.requireShopPermission(principal, shopId, "package:writeoff");
        String safeKey = requiredKey(idempotencyKey);
        String safeHash = requiredHash(requestHash);
        Map<String, Object> replay = ledgerReplay(
            principal, safeKey, safeHash, packageInstanceId
        );
        if (replay != null) return replay;

        Map<String, Object> serviceContext = serviceRecordService.packageWriteOffContext(
            principal, shopId, serviceRecordId
        );
        String serviceStatus = serviceContext.get("status").toString();
        if (!SERVICE_RECORD_STATUSES.contains(serviceStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "只有进行中或已完成的服务可以核销套餐");
        }
        @SuppressWarnings("unchecked")
        List<Long> serviceIds = (List<Long>) serviceContext.get("serviceIds");
        if (serviceIds == null || !serviceIds.contains(serviceId)) {
            throw new ApiException(HttpStatus.CONFLICT, "服务记录不包含该套餐项目");
        }

        Map<String, Object> instance = requireInstance(
            principal, shopId, packageInstanceId, true
        );
        if (number(instance.get("memberId")) != number(serviceContext.get("memberId"))) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐会员与服务记录会员不一致");
        }
        requireVersion(instance, version);
        Map<String, Object> item = lockInstanceItem(
            principal, packageInstanceId, serviceId
        );
        BigDecimal safeQuantity = quantity(writeOffQuantity, "核销次数");
        BigDecimal itemAfter;
        try {
            itemAfter = PackageLifecyclePolicy.balanceAfterWriteOff(
                instance.get("status").toString(),
                decimal(item.get("remainingQuantity")),
                safeQuantity,
                localDate(instance.get("validFrom")),
                localDate(instance.get("validUntil")),
                businessDate()
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
        BigDecimal totalAfter =
            decimal(instance.get("remainingQuantity")).subtract(safeQuantity);
        if (totalAfter.signum() < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐总余额不足");
        }
        String statusAfter = PackageLifecyclePolicy.statusAfterBalance(totalAfter);
        int itemChanged = jdbcTemplate.update(
            """
            UPDATE package_instance_item
            SET remaining_quantity = ?
            WHERE id = ? AND tenant_id = ? AND remaining_quantity = ?
            """,
            itemAfter,
            number(item.get("id")),
            principal.tenantId(),
            decimal(item.get("remainingQuantity"))
        );
        if (itemChanged != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐项目余额已变化，请刷新后重试");
        }
        int instanceChanged = jdbcTemplate.update(
            """
            UPDATE package_instance
            SET remaining_quantity = ?, status = ?, version = version + 1,
                updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            totalAfter,
            statusAfter,
            principal.accountId(),
            packageInstanceId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(instanceChanged, "套餐已被其他操作修改");
        long ledgerId = insertLedger(
            principal,
            shopId,
            packageInstanceId,
            number(item.get("id")),
            serviceRecordId,
            "WRITE_OFF",
            safeQuantity.negate(),
            itemAfter,
            null,
            "SERVICE:" + serviceRecordId + ":ITEM:" + number(item.get("id")),
            safeKey,
            safeHash,
            trimToNull(reason)
        );
        audit(
            principal, shopId, "PACKAGE_WRITE_OFF", "PACKAGE_INSTANCE",
            packageInstanceId,
            Map.of("ledgerId", ledgerId, "serviceRecordId", serviceRecordId)
        );
        appendEntryEvent(
            principal, shopId, packageInstanceId, ledgerId, serviceRecordId,
            "PackageWrittenOff"
        );
        return ledgerResult(ledgerId);
    }

    @Transactional
    public Map<String, Object> reverse(
        TenantPrincipal principal,
        long shopId,
        long originalLedgerId,
        int version,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        accessService.requireShopPermission(principal, shopId, "package:reverse");
        String safeKey = requiredKey(idempotencyKey);
        String safeHash = requiredHash(requestHash);
        String safeReason = required(reason, "冲正原因");
        Map<String, Object> replay = ledgerReplay(principal, safeKey, safeHash, null);
        if (replay != null) return replay;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pl.id, pl.package_instance_id AS packageInstanceId,
                   pl.package_instance_item_id AS packageInstanceItemId,
                   pl.service_record_id AS serviceRecordId,
                   pl.entry_type AS entryType, pl.quantity_delta AS quantityDelta,
                   pi.member_id AS memberId, pi.status, pi.version,
                   pi.remaining_quantity AS instanceRemaining,
                   pii.total_quantity AS itemTotal,
                   pii.remaining_quantity AS itemRemaining
            FROM package_ledger pl
            JOIN package_instance pi ON pi.id = pl.package_instance_id
            JOIN package_instance_item pii ON pii.id = pl.package_instance_item_id
            WHERE pl.id = ? AND pl.tenant_id = ?
              AND pi.tenant_id = ? AND pi.shop_id = ?
            FOR UPDATE
            """,
            originalLedgerId,
            principal.tenantId(),
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "原套餐核销流水不存在");
        }
        Map<String, Object> original = rows.getFirst();
        if (!"WRITE_OFF".equals(original.get("entryType"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有套餐核销流水可以冲正");
        }
        requireVersion(original, version);
        Integer reversed = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM package_ledger
            WHERE tenant_id = ? AND original_ledger_id = ?
            """,
            Integer.class,
            principal.tenantId(),
            originalLedgerId
        );
        if (reversed != null && reversed > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该套餐核销流水已经冲正");
        }

        BigDecimal restored = decimal(original.get("quantityDelta")).abs();
        BigDecimal itemAfter = decimal(original.get("itemRemaining")).add(restored);
        if (itemAfter.compareTo(decimal(original.get("itemTotal"))) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "冲正后套餐项目余额超过发放额度");
        }
        BigDecimal totalAfter =
            decimal(original.get("instanceRemaining")).add(restored);
        String currentStatus = original.get("status").toString();
        String targetStatus = "EXHAUSTED".equals(currentStatus) ? "ACTIVE" : currentStatus;
        int itemChanged = jdbcTemplate.update(
            """
            UPDATE package_instance_item
            SET remaining_quantity = ?
            WHERE id = ? AND tenant_id = ? AND remaining_quantity = ?
            """,
            itemAfter,
            number(original.get("packageInstanceItemId")),
            principal.tenantId(),
            decimal(original.get("itemRemaining"))
        );
        requireChanged(itemChanged, "套餐项目余额已变化，请刷新后重试");
        long packageInstanceId = number(original.get("packageInstanceId"));
        int instanceChanged = jdbcTemplate.update(
            """
            UPDATE package_instance
            SET remaining_quantity = ?, status = ?, version = version + 1,
                updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            totalAfter,
            targetStatus,
            principal.accountId(),
            packageInstanceId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(instanceChanged, "套餐已被其他操作修改");
        long ledgerId = insertLedger(
            principal,
            shopId,
            packageInstanceId,
            number(original.get("packageInstanceItemId")),
            nullableNumber(original.get("serviceRecordId")),
            "REVERSAL",
            restored,
            itemAfter,
            originalLedgerId,
            "REVERSAL:" + originalLedgerId,
            safeKey,
            safeHash,
            safeReason
        );
        audit(
            principal, shopId, "PACKAGE_WRITE_OFF_REVERSAL", "PACKAGE_INSTANCE",
            packageInstanceId, Map.of("ledgerId", ledgerId, "originalLedgerId", originalLedgerId)
        );
        appendEntryEvent(
            principal,
            shopId,
            packageInstanceId,
            ledgerId,
            nullableNumber(original.get("serviceRecordId")),
            "PackageWriteOffReversed"
        );
        return ledgerResult(ledgerId);
    }

    @Transactional
    public void cancelUnusedForRefund(
        TenantPrincipal principal,
        long shopId,
        long sourceOrderId,
        long refundId,
        String requestHash
    ) {
        List<Map<String, Object>> instances = jdbcTemplate.queryForList(
            """
            SELECT id, total_quantity AS totalQuantity,
                   remaining_quantity AS remainingQuantity, status, version
            FROM package_instance
            WHERE tenant_id = ? AND shop_id = ? AND source_order_id = ?
            FOR UPDATE
            """,
            principal.tenantId(),
            shopId,
            sourceOrderId
        );
        for (Map<String, Object> instance : instances) {
            long instanceId = number(instance.get("id"));
            String key = "REFUND-PACKAGE-" + refundId + "-" + instanceId;
            Map<String, Object> replay = ledgerReplay(
                principal, key, requestHash, instanceId
            );
            if (replay != null) continue;
            if (!Set.of("ACTIVE", "FROZEN").contains(instance.get("status").toString())
                || decimal(instance.get("remainingQuantity")).compareTo(
                    decimal(instance.get("totalQuantity"))
                ) != 0) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "已使用、耗尽或过期套餐不能随订单直接退款，请先完成专项审批"
                );
            }
            Long issueLedgerId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM package_ledger
                WHERE tenant_id = ? AND package_instance_id = ? AND entry_type = 'ISSUE'
                ORDER BY id
                LIMIT 1
                """,
                Long.class,
                principal.tenantId(),
                instanceId
            );
            int itemsChanged = jdbcTemplate.update(
                """
                UPDATE package_instance_item
                SET remaining_quantity = 0
                WHERE tenant_id = ? AND package_instance_id = ?
                  AND remaining_quantity = total_quantity
                """,
                principal.tenantId(),
                instanceId
            );
            Integer itemCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM package_instance_item
                WHERE tenant_id = ? AND package_instance_id = ?
                """,
                Integer.class,
                principal.tenantId(),
                instanceId
            );
            if (itemCount == null || itemsChanged != itemCount) {
                throw new ApiException(HttpStatus.CONFLICT, "套餐项目余额已变化，请刷新后重试");
            }
            int changed = jdbcTemplate.update(
                """
                UPDATE package_instance
                SET remaining_quantity = 0, status = 'CANCELLED',
                    version = version + 1, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
                """,
                principal.accountId(),
                instanceId,
                principal.tenantId(),
                shopId,
                number(instance.get("version"))
            );
            requireChanged(changed, "套餐已被其他操作修改");
            long ledgerId = insertLedger(
                principal,
                shopId,
                instanceId,
                null,
                null,
                "REVERSAL",
                decimal(instance.get("totalQuantity")).negate(),
                BigDecimal.ZERO.setScale(4),
                issueLedgerId,
                "REFUND:" + refundId + ":" + instanceId,
                key,
                requestHash,
                "订单退款取消未使用套餐"
            );
            audit(
                principal, shopId, "PACKAGE_REFUND_REVERSAL", "PACKAGE_INSTANCE",
                instanceId, Map.of("ledgerId", ledgerId, "refundId", refundId)
            );
            appendEntryEvent(
                principal, shopId, instanceId, ledgerId, null, "PackageRefundReversed"
            );
        }
    }

    @Transactional
    public Map<String, Object> changeStatus(
        TenantPrincipal principal,
        long shopId,
        long packageInstanceId,
        int version,
        String targetStatus
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        String status = required(targetStatus, "套餐状态").toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "FROZEN").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐只能冻结或解冻");
        }
        Map<String, Object> instance = requireInstance(
            principal, shopId, packageInstanceId, true
        );
        requireVersion(instance, version);
        String current = instance.get("status").toString();
        if ("ACTIVE".equals(status) && !"FROZEN".equals(current)) {
            throw new ApiException(HttpStatus.CONFLICT, "只有冻结中的套餐可以解冻");
        }
        if ("FROZEN".equals(status) && !"ACTIVE".equals(current)) {
            throw new ApiException(HttpStatus.CONFLICT, "只有生效中的套餐可以冻结");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE package_instance
            SET status = ?, version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            status,
            principal.accountId(),
            packageInstanceId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "套餐已被其他操作修改");
        audit(
            principal, shopId, "PACKAGE_STATUS_CHANGE", "PACKAGE_INSTANCE",
            packageInstanceId, Map.of("packageInstanceId", packageInstanceId)
        );
        outboxEventService.append(
            principal,
            shopId,
            "PACKAGE_INSTANCE",
            Long.toString(packageInstanceId),
            "PackageStatusChanged",
            Map.of("packageInstanceId", packageInstanceId)
        );
        return instanceResult(packageInstanceId);
    }

    private long insertInstance(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long packageProductId,
        long sourceOrderId,
        BigDecimal purchasePrice,
        LocalDate validFrom,
        LocalDate validUntil,
        BigDecimal totalQuantity,
        String idempotencyKey,
        String requestHash
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO package_instance (
                    tenant_id, shop_id, member_id, package_product_id,
                    instance_no, source_order_id, purchase_price,
                    valid_from, valid_until, total_quantity, remaining_quantity,
                    status, version, issue_idempotency_key, issue_request_hash,
                    created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setLong(4, packageProductId);
            statement.setString(5, instanceNumber());
            statement.setLong(6, sourceOrderId);
            statement.setBigDecimal(7, purchasePrice);
            statement.setDate(8, Date.valueOf(validFrom));
            statement.setDate(9, Date.valueOf(validUntil));
            statement.setBigDecimal(10, totalQuantity);
            statement.setBigDecimal(11, totalQuantity);
            statement.setString(12, idempotencyKey);
            statement.setString(13, requestHash);
            statement.setLong(14, principal.accountId());
            statement.setLong(15, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "套餐发放失败");
        }
        return key.longValue();
    }

    private long insertLedger(
        TenantPrincipal principal,
        long shopId,
        long packageInstanceId,
        Long packageInstanceItemId,
        Long serviceRecordId,
        String entryType,
        BigDecimal quantityDelta,
        BigDecimal balanceAfter,
        Long originalLedgerId,
        String businessKey,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO package_ledger (
                tenant_id, shop_id, package_instance_id, package_instance_item_id,
                service_record_id, entry_type, quantity_delta, balance_after,
                original_ledger_id, business_key, idempotency_key, request_hash,
                reason, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            packageInstanceId,
            packageInstanceItemId,
            serviceRecordId,
            entryType,
            quantityDelta,
            balanceAfter,
            originalLedgerId,
            businessKey,
            idempotencyKey,
            requestHash,
            reason,
            principal.accountId()
        );
        Long id = jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM package_ledger
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            Long.class,
            principal.tenantId(),
            idempotencyKey
        );
        if (id == null) throw new IllegalStateException("套餐流水创建失败");
        return id;
    }

    private Map<String, Object> issueReplay(
        TenantPrincipal principal,
        long memberId,
        long packageProductId,
        long sourceOrderId,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, package_product_id AS packageProductId,
                   source_order_id AS sourceOrderId,
                   issue_request_hash AS requestHash
            FROM package_instance
            WHERE tenant_id = ? AND issue_idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> existing = rows.getFirst();
        if (number(existing.get("memberId")) != memberId
            || number(existing.get("packageProductId")) != packageProductId
            || number(existing.get("sourceOrderId")) != sourceOrderId
            || !requestHash.equals(existing.get("requestHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐发放幂等键已用于不同请求");
        }
        return instanceResult(number(existing.get("id")));
    }

    private Map<String, Object> ledgerReplay(
        TenantPrincipal principal,
        String idempotencyKey,
        String requestHash,
        Long expectedPackageInstanceId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, package_instance_id AS packageInstanceId,
                   request_hash AS requestHash
            FROM package_ledger
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> existing = rows.getFirst();
        if (!requestHash.equals(existing.get("requestHash"))
            || (expectedPackageInstanceId != null
                && number(existing.get("packageInstanceId"))
                    != expectedPackageInstanceId.longValue())) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐流水幂等键已用于不同请求");
        }
        return ledgerResult(number(existing.get("id")));
    }

    private void ensureOrderProductNotIssued(
        TenantPrincipal principal,
        long sourceOrderId,
        long packageProductId
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM package_instance
            WHERE tenant_id = ? AND source_order_id = ? AND package_product_id = ?
            """,
            Integer.class,
            principal.tenantId(),
            sourceOrderId,
            packageProductId
        );
        if (count != null && count > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该订单套餐已经发放");
        }
    }

    private Map<String, Object> requireInstance(
        TenantPrincipal principal,
        long shopId,
        long packageInstanceId,
        boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, package_product_id AS packageProductId,
                   source_order_id AS sourceOrderId,
                   valid_from AS validFrom, valid_until AS validUntil,
                   remaining_quantity AS remainingQuantity,
                   total_quantity AS totalQuantity, status, version
            FROM package_instance
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            %s
            """.formatted(lock ? "FOR UPDATE" : ""),
            packageInstanceId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "会员套餐不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockInstanceItem(
        TenantPrincipal principal,
        long packageInstanceId,
        long serviceId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, service_id AS serviceId, total_quantity AS totalQuantity,
                   remaining_quantity AS remainingQuantity
            FROM package_instance_item
            WHERE tenant_id = ? AND package_instance_id = ? AND service_id = ?
            FOR UPDATE
            """,
            principal.tenantId(),
            packageInstanceId,
            serviceId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "会员套餐不包含该服务项目");
        }
        return rows.getFirst();
    }

    private Map<String, Object> instanceResult(long packageInstanceId) {
        Map<String, Object> result = new LinkedHashMap<>(jdbcTemplate.queryForMap(
            """
            SELECT pi.id, pi.instance_no AS instanceNo, pi.member_id AS memberId,
                   pi.package_product_id AS packageProductId,
                   pp.name AS packageName, pi.source_order_id AS sourceOrderId,
                   pi.purchase_price AS purchasePrice,
                   pi.valid_from AS validFrom, pi.valid_until AS validUntil,
                   pi.total_quantity AS totalQuantity,
                   pi.remaining_quantity AS remainingQuantity,
                   pi.status, pi.version, pi.created_at AS createdAt
            FROM package_instance pi
            JOIN package_product pp ON pp.id = pi.package_product_id
            WHERE pi.id = ?
            """,
            packageInstanceId
        ));
        result.put(
            "items",
            jdbcTemplate.queryForList(
                """
                SELECT id, service_id AS serviceId,
                       service_name_snapshot AS serviceName,
                       total_quantity AS totalQuantity,
                       remaining_quantity AS remainingQuantity
                FROM package_instance_item
                WHERE package_instance_id = ?
                ORDER BY id
                """,
                packageInstanceId
            )
        );
        return result;
    }

    private Map<String, Object> ledgerResult(long ledgerId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT id, package_instance_id AS packageInstanceId,
                   package_instance_item_id AS packageInstanceItemId,
                   service_record_id AS serviceRecordId,
                   entry_type AS entryType, quantity_delta AS quantityDelta,
                   balance_after AS balanceAfter,
                   original_ledger_id AS originalLedgerId,
                   reason, created_at AS createdAt
            FROM package_ledger
            WHERE id = ?
            """,
            ledgerId
        );
    }

    private void requireMemberRead(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        if (principal.roles().contains("MEMBER")) {
            if (accessService.requireMemberId(principal) != memberId) {
                throw new ApiException(HttpStatus.FORBIDDEN, "只能查看本人套餐");
            }
        } else {
            accessService.requireShopPermission(principal, shopId, "package:view");
        }
        memberService.requireActiveMemberInShop(principal, shopId, memberId);
    }

    private void requireVersion(Map<String, Object> entity, int version) {
        if (number(entity.get("version")) != version) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐版本已变化，请刷新后重试");
        }
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId,
        Map<String, Object> afterData
    ) {
        String json = afterData.entrySet().stream()
            .map(entry -> "'" + entry.getKey() + "', " + number(entry.getValue()))
            .reduce((left, right) -> left + ", " + right)
            .orElse("'entityId', " + entityId);
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, ?, ?, ?, JSON_OBJECT(%s))
            """.formatted(json),
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityType,
            entityId
        );
    }

    private void appendEntryEvent(
        TenantPrincipal principal,
        long shopId,
        long packageInstanceId,
        long ledgerId,
        Long serviceRecordId,
        String eventType
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packageInstanceId", packageInstanceId);
        payload.put("ledgerId", ledgerId);
        if (serviceRecordId != null) payload.put("serviceRecordId", serviceRecordId);
        outboxEventService.append(
            principal,
            shopId,
            "PACKAGE_INSTANCE",
            Long.toString(packageInstanceId),
            eventType,
            payload
        );
    }

    private LocalDate businessDate() {
        LocalDate date = jdbcTemplate.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        return date == null ? LocalDate.now() : date;
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private BigDecimal money(Object value) {
        return decimal(value).setScale(2);
    }

    private BigDecimal quantity(Object value, String label) {
        BigDecimal result = decimal(value);
        try {
            result = result.setScale(4);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "最多四位小数");
        }
        if (result.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "必须大于零");
        }
        return result;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "数值不能为空");
        return new BigDecimal(value.toString());
    }

    private String requiredKey(String value) {
        String key = required(value, "幂等键");
        if (key.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "幂等键不能超过80个字符");
        }
        return key;
    }

    private String requiredHash(String value) {
        String hash = required(value, "请求摘要");
        if (!hash.matches("[0-9a-fA-F]{64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求摘要格式不正确");
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private String required(String value, String label) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return safe;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String safe = value.trim();
        return safe.isEmpty() ? null : safe;
    }

    private String instanceNumber() {
        return "PKG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20)
            .toUpperCase(Locale.ROOT);
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("套餐账户数据不完整");
        }
        return number.longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
