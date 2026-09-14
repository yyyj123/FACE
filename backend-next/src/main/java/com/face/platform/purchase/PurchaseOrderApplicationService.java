package com.face.platform.purchase;

import com.face.platform.api.ApiException;
import com.face.platform.inventory.InventoryReceiptApplicationService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseOrderApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final InventoryReceiptApplicationService inventoryReceiptService;
    private final OutboxEventService outboxEventService;

    public PurchaseOrderApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        InventoryReceiptApplicationService inventoryReceiptService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.inventoryReceiptService = inventoryReceiptService;
        this.outboxEventService = outboxEventService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        long shopId,
        String status,
        int page,
        int pageSize
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "purchase:view");
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        String normalizedStatus = normalizedStatus(status);
        String statusFilter = normalizedStatus == null ? "" : " AND po.status = ?";
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        if (normalizedStatus != null) args.add(normalizedStatus);
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safePageSize);
        listArgs.add((safePage - 1) * safePageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT po.id, po.shop_id AS shopId,
                   po.purchase_order_no AS purchaseOrderNo,
                   po.supplier_name AS supplierName,
                   po.expected_date AS expectedDate,
                   po.currency_code AS currencyCode,
                   po.total_amount AS totalAmount, po.status, po.version,
                   po.created_by AS createdBy, creator.username AS createdByName,
                   po.approved_by AS approvedBy, approver.username AS approvedByName,
                   po.submitted_at AS submittedAt, po.approved_at AS approvedAt,
                   po.closed_at AS closedAt, po.created_at AS createdAt,
                   COUNT(poi.id) AS itemCount,
                   COALESCE(SUM(poi.ordered_quantity), 0) AS orderedQuantity,
                   COALESCE(SUM(poi.received_quantity), 0) AS receivedQuantity
            FROM purchase_order po
            JOIN account creator ON creator.id = po.created_by
            LEFT JOIN account approver ON approver.id = po.approved_by
            LEFT JOIN purchase_order_item poi ON poi.purchase_order_id = po.id
            WHERE po.tenant_id = ? AND po.shop_id = ?
            %s
            GROUP BY po.id, po.shop_id, po.purchase_order_no, po.supplier_name,
                     po.expected_date, po.currency_code, po.total_amount,
                     po.status, po.version, po.created_by, creator.username,
                     po.approved_by, approver.username, po.submitted_at,
                     po.approved_at, po.closed_at, po.created_at
            ORDER BY po.created_at DESC, po.id DESC
            LIMIT ? OFFSET ?
            """.formatted(statusFilter),
            listArgs.toArray()
        );
        for (Map<String, Object> record : records) {
            normalizeDate(record, "expectedDate");
        }
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM purchase_order po
            WHERE po.tenant_id = ? AND po.shop_id = ?
            %s
            """.formatted(statusFilter),
            Long.class,
            args.toArray()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        return result;
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "purchase:view");
        return purchaseOrderResult(principal.tenantId(), shopId, purchaseOrderId);
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        long shopId,
        String supplierName,
        LocalDate expectedDate,
        String currencyCode,
        String remark,
        List<OrderLine> lines,
        String idempotencyKey,
        String requestHash
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "purchase:manage");
        String key = required(idempotencyKey, "采购单幂等键", 80);
        String hash = requiredHash(requestHash);
        Map<String, Object> replay = orderReplay(principal, shopId, key, hash);
        if (replay != null) return replay;

        String supplier = required(supplierName, "供应商名称", 160);
        String currency = normalizeCurrency(currencyCode);
        String safeRemark = optional(remark, 500, "采购备注");
        List<NormalizedOrderLine> normalizedLines = normalizeOrderLines(
            principal.tenantId(), shopId, lines
        );
        BigDecimal totalAmount = normalizedLines.stream()
            .map(NormalizedOrderLine::lineAmount)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        String purchaseOrderNo = businessNo("PO");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO purchase_order (
                    tenant_id, shop_id, purchase_order_no, supplier_name,
                    expected_date, currency_code, total_amount, status,
                    idempotency_key, request_hash, remark, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, purchaseOrderNo);
            statement.setString(4, supplier);
            if (expectedDate == null) {
                statement.setNull(5, java.sql.Types.DATE);
            } else {
                statement.setObject(5, expectedDate);
            }
            statement.setString(6, currency);
            statement.setBigDecimal(7, totalAmount);
            statement.setString(8, key);
            statement.setString(9, hash);
            statement.setString(10, safeRemark);
            statement.setLong(11, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "采购单创建失败");
        }
        long purchaseOrderId = generated.longValue();
        for (int index = 0; index < normalizedLines.size(); index++) {
            NormalizedOrderLine line = normalizedLines.get(index);
            jdbcTemplate.update(
                """
                INSERT INTO purchase_order_item (
                    purchase_order_id, product_id, sku_snapshot,
                    product_name_snapshot, unit_name_snapshot,
                    ordered_quantity, received_quantity, unit_cost,
                    line_amount, sort_order, version
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0)
                """,
                purchaseOrderId,
                line.productId(),
                line.sku(),
                line.productName(),
                line.unitName(),
                line.quantity(),
                line.unitCost(),
                line.lineAmount(),
                index
            );
        }
        audit(principal, shopId, "PURCHASE_ORDER_CREATE", "PURCHASE_ORDER", purchaseOrderId);
        appendEvent(
            principal, shopId, purchaseOrderId, "PurchaseOrderCreated",
            Map.of("purchaseOrderId", purchaseOrderId)
        );
        return purchaseOrderResult(principal.tenantId(), shopId, purchaseOrderId);
    }

    @Transactional
    public Map<String, Object> submit(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId,
        int version
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "purchase:manage");
        Map<String, Object> order = lockOrder(
            principal.tenantId(), shopId, purchaseOrderId
        );
        requireVersion(order, version);
        requireTransition(order.get("status").toString(), "SUBMITTED");
        int changed = jdbcTemplate.update(
            """
            UPDATE purchase_order
            SET status = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'DRAFT'
            """,
            purchaseOrderId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "采购单提交状态已变化，请刷新后重试");
        audit(principal, shopId, "PURCHASE_ORDER_SUBMIT", "PURCHASE_ORDER", purchaseOrderId);
        appendEvent(
            principal, shopId, purchaseOrderId, "PurchaseOrderSubmitted",
            Map.of("purchaseOrderId", purchaseOrderId)
        );
        return purchaseOrderResult(principal.tenantId(), shopId, purchaseOrderId);
    }

    @Transactional
    public Map<String, Object> decide(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId,
        int version,
        String action,
        String decisionNote
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "purchase:approve");
        Map<String, Object> order = lockOrder(
            principal.tenantId(), shopId, purchaseOrderId
        );
        requireVersion(order, version);
        if (number(order.get("createdBy")) == principal.accountId()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "采购申请人与审批人必须分离"
            );
        }
        String normalizedAction = required(action, "审批动作", 20)
            .toUpperCase(Locale.ROOT);
        String target = switch (normalizedAction) {
            case "APPROVE", "APPROVED" -> "APPROVED";
            case "CLOSE", "REJECT", "REJECTED" -> "CLOSED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的采购审批动作");
        };
        String note = optional(decisionNote, 500, "审批说明");
        if ("CLOSED".equals(target) && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "关闭采购申请必须填写审批说明");
        }
        requireTransition(order.get("status").toString(), target);
        int changed = jdbcTemplate.update(
            """
            UPDATE purchase_order
            SET status = ?, decision_note = ?, approved_by = ?,
                approved_at = CASE WHEN ? = 'APPROVED'
                  THEN CURRENT_TIMESTAMP(3) ELSE approved_at END,
                closed_at = CASE WHEN ? = 'CLOSED'
                  THEN CURRENT_TIMESTAMP(3) ELSE closed_at END,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'SUBMITTED'
            """,
            target,
            note,
            principal.accountId(),
            target,
            target,
            purchaseOrderId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed, "采购单已被其他人审批，请刷新后重试");
        audit(
            principal,
            shopId,
            "APPROVED".equals(target)
                ? "PURCHASE_ORDER_APPROVE"
                : "PURCHASE_ORDER_CLOSE",
            "PURCHASE_ORDER",
            purchaseOrderId
        );
        appendEvent(
            principal,
            shopId,
            purchaseOrderId,
            "APPROVED".equals(target)
                ? "PurchaseOrderApproved"
                : "PurchaseOrderClosed",
            Map.of("purchaseOrderId", purchaseOrderId)
        );
        return purchaseOrderResult(principal.tenantId(), shopId, purchaseOrderId);
    }

    @Transactional
    public Map<String, Object> receive(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId,
        int version,
        Instant receivedAt,
        String remark,
        List<ReceiptLine> lines,
        String idempotencyKey,
        String requestHash
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "purchase:receive");
        String key = required(idempotencyKey, "收货幂等键", 80);
        String hash = requiredHash(requestHash);
        Map<String, Object> replay = receiptReplay(
            principal, shopId, purchaseOrderId, key, hash
        );
        if (replay != null) return replay;

        Map<String, Object> order = lockOrder(
            principal.tenantId(), shopId, purchaseOrderId
        );
        requireVersion(order, version);
        String currentStatus = order.get("status").toString();
        if (!List.of("APPROVED", "PARTIALLY_RECEIVED").contains(currentStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已审批采购单可以收货");
        }
        replay = receiptReplay(principal, shopId, purchaseOrderId, key, hash);
        if (replay != null) return replay;

        Instant receiptTime = receivedAt == null ? Instant.now() : receivedAt;
        if (receiptTime.isAfter(Instant.now().plusSeconds(300))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收货时间不能晚于当前时间");
        }
        String safeRemark = optional(remark, 500, "收货备注");
        List<ReceiptLine> normalizedLines = normalizeReceiptLines(lines);
        String receiptNo = businessNo("PR");
        long receiptId = insertReceipt(
            principal,
            shopId,
            purchaseOrderId,
            receiptNo,
            key,
            hash,
            receiptTime,
            safeRemark
        );

        for (ReceiptLine line : normalizedLines) {
            Map<String, Object> item = lockOrderItem(
                purchaseOrderId, line.purchaseOrderItemId()
            );
            BigDecimal receivedQuantity;
            try {
                receivedQuantity = PurchaseReceiptPolicy.requireReceivable(
                    decimal(item.get("orderedQuantity")),
                    decimal(item.get("receivedQuantity")),
                    line.receivedQuantity()
                );
                PurchaseReceiptPolicy.requireValidDates(
                    line.producedDate(), line.expiryDate()
                );
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
            }
            BigDecimal unitCost = nonNegativeCost(line.unitCost());
            long productId = number(item.get("productId"));
            String batchNo = "PB-" + principal.tenantId()
                + "-" + receiptId + "-" + line.purchaseOrderItemId();
            var posting = inventoryReceiptService.receivePurchaseBatch(
                principal,
                shopId,
                receiptId,
                line.purchaseOrderItemId(),
                productId,
                line.locationId(),
                batchNo,
                optional(line.vendorBatchNo(), 100, "供应商批号"),
                line.producedDate(),
                line.expiryDate(),
                receivedQuantity,
                unitCost,
                receiptNo,
                safeRemark
            );
            jdbcTemplate.update(
                """
                INSERT INTO purchase_receipt_item (
                    purchase_receipt_id, purchase_order_item_id, product_id,
                    location_id, stock_batch_id, inventory_movement_id,
                    received_quantity, unit_cost, vendor_batch_no,
                    produced_date, expiry_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                receiptId,
                line.purchaseOrderItemId(),
                productId,
                line.locationId(),
                posting.stockBatchId(),
                posting.inventoryMovementId(),
                receivedQuantity,
                unitCost,
                optional(line.vendorBatchNo(), 100, "供应商批号"),
                line.producedDate(),
                line.expiryDate()
            );
            int itemChanged = jdbcTemplate.update(
                """
                UPDATE purchase_order_item
                SET received_quantity = received_quantity + ?, version = version + 1
                WHERE id = ? AND purchase_order_id = ? AND version = ?
                  AND received_quantity + ? <= ordered_quantity
                """,
                receivedQuantity,
                line.purchaseOrderItemId(),
                purchaseOrderId,
                number(item.get("version")),
                receivedQuantity
            );
            requireChanged(itemChanged, "采购明细可收数量已变化，请刷新后重试");
        }

        Integer incomplete = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM purchase_order_item
            WHERE purchase_order_id = ? AND received_quantity < ordered_quantity
            """,
            Integer.class,
            purchaseOrderId
        );
        String targetStatus = PurchaseReceiptPolicy.statusAfterReceipt(
            incomplete != null && incomplete == 0
        );
        requireTransition(currentStatus, targetStatus);
        int orderChanged = jdbcTemplate.update(
            """
            UPDATE purchase_order
            SET status = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = ?
            """,
            targetStatus,
            purchaseOrderId,
            principal.tenantId(),
            shopId,
            version,
            currentStatus
        );
        requireChanged(orderChanged, "采购单收货状态已变化，请刷新后重试");
        audit(principal, shopId, "PURCHASE_RECEIPT_CREATE", "PURCHASE_RECEIPT", receiptId);
        appendEvent(
            principal,
            shopId,
            purchaseOrderId,
            "PurchaseReceiptPosted",
            Map.of("purchaseOrderId", purchaseOrderId, "purchaseReceiptId", receiptId)
        );
        return receiptResult(principal.tenantId(), shopId, receiptId);
    }

    private List<NormalizedOrderLine> normalizeOrderLines(
        long tenantId,
        long shopId,
        List<OrderLine> lines
    ) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "采购明细数量必须为1至100条");
        }
        Set<Long> productIds = new HashSet<>();
        List<NormalizedOrderLine> result = new ArrayList<>();
        for (OrderLine line : lines) {
            if (line == null || line.productId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "采购商品不能为空");
            }
            if (!productIds.add(line.productId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一商品不能重复填写");
            }
            BigDecimal quantity = positiveQuantity(line.quantity(), "采购数量");
            BigDecimal unitCost = nonNegativeCost(line.unitCost());
            List<Map<String, Object>> products = jdbcTemplate.queryForList(
                """
                SELECT id, sku, name, unit_name AS unitName
                FROM product
                WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
                """,
                line.productId(),
                tenantId,
                shopId
            );
            if (products.isEmpty()) {
                throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "采购商品不存在或不属于当前门店"
                );
            }
            Map<String, Object> product = products.getFirst();
            BigDecimal lineAmount = quantity.multiply(unitCost)
                .setScale(2, RoundingMode.HALF_UP);
            result.add(new NormalizedOrderLine(
                line.productId(),
                product.get("sku").toString(),
                product.get("name").toString(),
                product.get("unitName").toString(),
                quantity,
                unitCost,
                lineAmount
            ));
        }
        return result;
    }

    private List<ReceiptLine> normalizeReceiptLines(List<ReceiptLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收货明细数量必须为1至100条");
        }
        Set<Long> orderItemIds = new HashSet<>();
        List<ReceiptLine> result = new ArrayList<>();
        for (ReceiptLine line : lines) {
            if (line == null
                || line.purchaseOrderItemId() <= 0
                || line.locationId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "收货明细不完整");
            }
            if (!orderItemIds.add(line.purchaseOrderItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一采购明细不能重复收货");
            }
            result.add(line);
        }
        result.sort(java.util.Comparator.comparingLong(ReceiptLine::purchaseOrderItemId));
        return result;
    }

    private long insertReceipt(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId,
        String receiptNo,
        String idempotencyKey,
        String requestHash,
        Instant receivedAt,
        String remark
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO purchase_receipt (
                    tenant_id, shop_id, purchase_order_id, receipt_no,
                    idempotency_key, request_hash, received_at,
                    remark, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, purchaseOrderId);
            statement.setString(4, receiptNo);
            statement.setString(5, idempotencyKey);
            statement.setString(6, requestHash);
            statement.setTimestamp(7, Timestamp.from(receivedAt));
            statement.setString(8, remark);
            statement.setLong(9, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "收货单创建失败");
        }
        return generated.longValue();
    }

    private Map<String, Object> orderReplay(
        TenantPrincipal principal,
        long shopId,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, request_hash AS requestHash
            FROM purchase_order
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        if (number(row.get("shopId")) != shopId
            || !requestHash.equals(row.get("requestHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "采购单幂等键已用于不同请求");
        }
        return purchaseOrderResult(principal.tenantId(), shopId, number(row.get("id")));
    }

    private Map<String, Object> receiptReplay(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, purchase_order_id AS purchaseOrderId,
                   request_hash AS requestHash
            FROM purchase_receipt
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        if (number(row.get("shopId")) != shopId
            || number(row.get("purchaseOrderId")) != purchaseOrderId
            || !requestHash.equals(row.get("requestHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "收货幂等键已用于不同请求");
        }
        return receiptResult(principal.tenantId(), shopId, number(row.get("id")));
    }

    private Map<String, Object> lockOrder(long tenantId, long shopId, long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, status, version, created_by AS createdBy
            FROM purchase_order
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            orderId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "采购单不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockOrderItem(long orderId, long orderItemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, product_id AS productId,
                   ordered_quantity AS orderedQuantity,
                   received_quantity AS receivedQuantity,
                   unit_cost AS unitCost, version
            FROM purchase_order_item
            WHERE id = ? AND purchase_order_id = ?
            FOR UPDATE
            """,
            orderItemId,
            orderId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "采购明细不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> purchaseOrderResult(
        long tenantId,
        long shopId,
        long purchaseOrderId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT po.id, po.shop_id AS shopId,
                   po.purchase_order_no AS purchaseOrderNo,
                   po.supplier_name AS supplierName,
                   po.expected_date AS expectedDate,
                   po.currency_code AS currencyCode,
                   po.total_amount AS totalAmount, po.status,
                   po.remark, po.decision_note AS decisionNote, po.version,
                   po.created_by AS createdBy, creator.username AS createdByName,
                   po.approved_by AS approvedBy, approver.username AS approvedByName,
                   po.submitted_at AS submittedAt, po.approved_at AS approvedAt,
                   po.closed_at AS closedAt, po.created_at AS createdAt,
                   po.updated_at AS updatedAt
            FROM purchase_order po
            JOIN account creator ON creator.id = po.created_by
            LEFT JOIN account approver ON approver.id = po.approved_by
            WHERE po.id = ? AND po.tenant_id = ? AND po.shop_id = ?
            """,
            purchaseOrderId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "采购单不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        normalizeDate(result, "expectedDate");
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            """
            SELECT poi.id, poi.product_id AS productId,
                   poi.sku_snapshot AS sku,
                   poi.product_name_snapshot AS productName,
                   poi.unit_name_snapshot AS unitName,
                   poi.ordered_quantity AS orderedQuantity,
                   poi.received_quantity AS receivedQuantity,
                   poi.ordered_quantity - poi.received_quantity AS outstandingQuantity,
                   poi.unit_cost AS unitCost, poi.line_amount AS lineAmount,
                   poi.version
            FROM purchase_order_item poi
            WHERE poi.purchase_order_id = ?
            ORDER BY poi.sort_order, poi.id
            """,
            purchaseOrderId
        );
        result.put("items", items);
        result.put("receipts", jdbcTemplate.queryForList(
            """
            SELECT pr.id, pr.receipt_no AS receiptNo,
                   pr.received_at AS receivedAt, pr.remark,
                   pr.created_by AS createdBy, a.username AS createdByName,
                   pr.created_at AS createdAt
            FROM purchase_receipt pr
            JOIN account a ON a.id = pr.created_by
            WHERE pr.tenant_id = ? AND pr.shop_id = ?
              AND pr.purchase_order_id = ?
            ORDER BY pr.received_at DESC, pr.id DESC
            """,
            tenantId,
            shopId,
            purchaseOrderId
        ));
        return result;
    }

    private Map<String, Object> receiptResult(long tenantId, long shopId, long receiptId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pr.id, pr.shop_id AS shopId,
                   pr.purchase_order_id AS purchaseOrderId,
                   po.purchase_order_no AS purchaseOrderNo,
                   pr.receipt_no AS receiptNo, pr.received_at AS receivedAt,
                   pr.remark, pr.version, pr.created_by AS createdBy,
                   a.username AS createdByName, pr.created_at AS createdAt,
                   po.status AS purchaseOrderStatus, po.version AS purchaseOrderVersion
            FROM purchase_receipt pr
            JOIN purchase_order po
              ON po.id = pr.purchase_order_id AND po.tenant_id = pr.tenant_id
            JOIN account a ON a.id = pr.created_by
            WHERE pr.id = ? AND pr.tenant_id = ? AND pr.shop_id = ?
            """,
            receiptId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "收货单不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            """
            SELECT pri.id, pri.purchase_order_item_id AS purchaseOrderItemId,
                   pri.product_id AS productId,
                   poi.sku_snapshot AS sku,
                   poi.product_name_snapshot AS productName,
                   poi.unit_name_snapshot AS unitName,
                   pri.location_id AS locationId, sl.name AS locationName,
                   pri.stock_batch_id AS stockBatchId, sb.batch_no AS batchNo,
                   pri.inventory_movement_id AS inventoryMovementId,
                   pri.received_quantity AS receivedQuantity,
                   pri.unit_cost AS unitCost,
                   pri.vendor_batch_no AS vendorBatchNo,
                   pri.produced_date AS producedDate,
                   pri.expiry_date AS expiryDate
            FROM purchase_receipt_item pri
            JOIN purchase_order_item poi ON poi.id = pri.purchase_order_item_id
            JOIN stock_location sl ON sl.id = pri.location_id
            JOIN stock_batch sb ON sb.id = pri.stock_batch_id
            WHERE pri.purchase_receipt_id = ?
            ORDER BY pri.id
            """,
            receiptId
        );
        for (Map<String, Object> item : items) {
            normalizeDate(item, "producedDate");
            normalizeDate(item, "expiryDate");
        }
        result.put("items", items);
        return result;
    }

    private String normalizedStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of(
            "DRAFT", "SUBMITTED", "APPROVED",
            "PARTIALLY_RECEIVED", "RECEIVED", "CLOSED"
        ).contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的采购单状态");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        String currency = trimToNull(value);
        if (currency == null) return "CNY";
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "币种代码必须为3位大写字母");
        }
        return currency;
    }

    private BigDecimal positiveQuantity(BigDecimal value, String label) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        BigDecimal normalized;
        try {
            normalized = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "最多保留3位小数");
        }
        if (normalized.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "必须大于0");
        }
        return normalized;
    }

    private BigDecimal nonNegativeCost(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "采购成本不能为空");
        BigDecimal normalized;
        try {
            normalized = value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "采购成本最多保留4位小数");
        }
        if (normalized.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "采购成本不能小于0");
        }
        return normalized;
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String required(String value, String label, int maxLength) {
        String result = trimToNull(value);
        if (result == null) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        if (result.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "长度不能超过" + maxLength);
        }
        return result;
    }

    private String optional(String value, int maxLength, String label) {
        String result = trimToNull(value);
        if (result != null && result.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "长度不能超过" + maxLength);
        }
        return result;
    }

    private String requiredHash(String value) {
        String hash = required(value, "请求摘要", 64);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求摘要格式不正确");
        }
        return hash;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void normalizeDate(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof java.sql.Date date) {
            row.put(key, date.toLocalDate());
        }
    }

    private void requireVersion(Map<String, Object> row, int expected) {
        if (number(row.get("version")) != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "采购单版本已变化，请刷新后重试");
        }
    }

    private void requireTransition(String current, String target) {
        if (current.equals(target)) return;
        if (!PurchaseOrderStatusPolicy.canTransition(current, target)) {
            throw new ApiException(HttpStatus.CONFLICT, "当前采购单状态不能执行该操作");
        }
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private String businessNo(String prefix) {
        return prefix
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id,
                after_data
            ) VALUES (?, ?, ?, ?, ?, ?, JSON_OBJECT('entityId', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityType,
            entityId,
            entityId
        );
    }

    private void appendEvent(
        TenantPrincipal principal,
        long shopId,
        long purchaseOrderId,
        String eventType,
        Map<String, Object> payload
    ) {
        outboxEventService.append(
            principal,
            shopId,
            "PURCHASE_ORDER",
            Long.toString(purchaseOrderId),
            eventType,
            payload
        );
    }

    public record OrderLine(
        long productId,
        BigDecimal quantity,
        BigDecimal unitCost
    ) {
    }

    public record ReceiptLine(
        long purchaseOrderItemId,
        long locationId,
        BigDecimal receivedQuantity,
        BigDecimal unitCost,
        String vendorBatchNo,
        LocalDate producedDate,
        LocalDate expiryDate
    ) {
    }

    private record NormalizedOrderLine(
        long productId,
        String sku,
        String productName,
        String unitName,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal lineAmount
    ) {
    }
}
