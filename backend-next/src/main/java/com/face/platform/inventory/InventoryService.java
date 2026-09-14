package com.face.platform.inventory;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InventoryService {

    private static final List<String> MOVEMENT_TYPES = List.of(
        "INITIAL_BALANCE", "LEGACY_OUT", "PURCHASE_IN", "SALE_OUT",
        "SERVICE_USE", "RETURN_IN", "TRANSFER_OUT", "TRANSFER_IN",
        "MANUAL_IN", "MANUAL_OUT", "ADJUSTMENT"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    public InventoryService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        long shopId,
        String keyword,
        boolean lowStockOnly,
        int page,
        int pageSize
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "inventory:view");
        int safePage = Math.max(page, 1);
        int safeLimit = safePageSize(pageSize);
        String normalizedKeyword = trimToNull(keyword);
        String keywordWhere = normalizedKeyword == null
            ? ""
            : " AND (p.name LIKE ? OR p.sku LIKE ? OR p.brand_name LIKE ?)";
        String lowWhere = lowStockOnly
            ? " AND p.stock_quantity <= p.warning_quantity"
            : "";
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        if (normalizedKeyword != null) {
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeLimit);
        listArgs.add((safePage - 1) * safeLimit);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT p.id, p.sku, p.name, p.brand_name AS brandName,
                   p.unit_name AS unitName, p.stock_quantity AS quantityOnHand,
                   p.warning_quantity AS warningQuantity,
                   COALESCE(SUM(sb.quantity_reserved), 0) AS quantityReserved,
                   p.stock_quantity - COALESCE(SUM(sb.quantity_reserved), 0)
                     AS quantityAvailable,
                   CASE WHEN p.stock_quantity <= p.warning_quantity THEN 1 ELSE 0 END
                     AS lowStock,
                   MAX(sb.updated_at) AS updatedAt
            FROM product p
            LEFT JOIN stock_balance sb
              ON sb.tenant_id = p.tenant_id AND sb.product_id = p.id
            WHERE p.tenant_id = ? AND p.shop_id = ? AND p.status = 'ACTIVE'
            %s
            %s
            GROUP BY p.id, p.sku, p.name, p.brand_name, p.unit_name,
                     p.stock_quantity, p.warning_quantity
            ORDER BY lowStock DESC, p.name, p.id
            LIMIT ? OFFSET ?
            """.formatted(keywordWhere, lowWhere),
            listArgs.toArray()
        );
        for (Map<String, Object> record : records) {
            record.put("locations", balanceRows(principal.tenantId(), number(record.get("id"))));
        }

        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM product p
            WHERE p.tenant_id = ? AND p.shop_id = ? AND p.status = 'ACTIVE'
            %s
            %s
            """.formatted(keywordWhere, lowWhere),
            Long.class,
            args.toArray()
        );
        Map<String, Object> summary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS productCount,
                   COALESCE(SUM(stock_quantity), 0) AS quantityOnHand,
                   COALESCE(SUM(CASE
                     WHEN stock_quantity <= warning_quantity THEN 1 ELSE 0 END), 0)
                     AS lowStockCount,
                   COALESCE((SELECT SUM(sb.quantity_reserved)
                     FROM stock_balance sb
                     JOIN product sp ON sp.id = sb.product_id
                     WHERE sb.tenant_id = ? AND sp.shop_id = ?), 0) AS quantityReserved
            FROM product
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            """,
            principal.tenantId(),
            shopId,
            principal.tenantId(),
            shopId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("summary", summary);
        result.put("page", safePage);
        result.put("pageSize", safeLimit);
        return result;
    }

    public Map<String, Object> resources(TenantPrincipal principal, long shopId) {
        tenantAccessService.requireShopPermission(principal, shopId, "inventory:view");
        List<Long> shopScope = tenantAccessService.accessibleShopIds(principal, "inventory:view");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", locationsForShops(principal.tenantId(), shopScope));
        result.put("products", jdbcTemplate.queryForList(
            """
            SELECT id, sku, name, brand_name AS brandName, unit_name AS unitName,
                   stock_quantity AS quantityOnHand, warning_quantity AS warningQuantity
            FROM product
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            ORDER BY name, id
            """,
            principal.tenantId(),
            shopId
        ));
        return result;
    }

    public Map<String, Object> movements(
        TenantPrincipal principal,
        long shopId,
        Long productId,
        String movementType,
        int page,
        int pageSize
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "inventory:view");
        int safePage = Math.max(page, 1);
        int safeLimit = safePageSize(pageSize);
        String normalizedType = normalizeMovementType(movementType);
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        StringBuilder filters = new StringBuilder();
        if (productId != null) {
            filters.append(" AND im.product_id = ?");
            args.add(productId);
        }
        if (normalizedType != null) {
            filters.append(" AND im.movement_type = ?");
            args.add(normalizedType);
        }
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeLimit);
        listArgs.add((safePage - 1) * safeLimit);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT im.id, im.shop_id AS shopId, im.location_id AS locationId,
                   sl.name AS locationName, im.product_id AS productId,
                   p.sku, p.name AS productName, p.unit_name AS unitName,
                   im.movement_type AS movementType,
                   im.quantity_delta AS quantityDelta,
                   im.balance_after AS balanceAfter,
                   im.order_id AS orderId, im.reference_no AS referenceNo,
                   im.remark, a.username AS createdByName, im.created_at AS createdAt
            FROM inventory_movement im
            JOIN stock_location sl ON sl.id = im.location_id
            JOIN product p ON p.id = im.product_id
            JOIN account a ON a.id = im.created_by
            WHERE im.tenant_id = ? AND im.shop_id = ?
            %s
            ORDER BY im.created_at DESC, im.id DESC
            LIMIT ? OFFSET ?
            """.formatted(filters),
            listArgs.toArray()
        );
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM inventory_movement im
            WHERE im.tenant_id = ? AND im.shop_id = ?
            %s
            """.formatted(filters),
            Long.class,
            args.toArray()
        );
        return pageResult(records, total, safePage, safeLimit);
    }

    public Map<String, Object> transfers(
        TenantPrincipal principal,
        long shopId,
        String status,
        int page,
        int pageSize
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "inventory:view");
        String normalizedStatus = normalizeTransferStatus(status);
        int safePage = Math.max(page, 1);
        int safeLimit = safePageSize(pageSize);
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        args.add(shopId);
        String statusWhere = "";
        if (normalizedStatus != null) {
            statusWhere = " AND it.status = ?";
            args.add(normalizedStatus);
        }
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeLimit);
        listArgs.add((safePage - 1) * safeLimit);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT it.id, it.transfer_no AS transferNo,
                   it.source_shop_id AS sourceShopId, ss.name AS sourceShopName,
                   it.source_location_id AS sourceLocationId, sl.name AS sourceLocationName,
                   it.destination_shop_id AS destinationShopId,
                   ds.name AS destinationShopName,
                   it.destination_location_id AS destinationLocationId,
                   dl.name AS destinationLocationName,
                   iti.source_product_id AS productId,
                   iti.sku_snapshot AS sku, iti.product_name_snapshot AS productName,
                   iti.quantity, it.status, it.remark, it.decision_note AS decisionNote,
                   it.version, creator.username AS createdByName,
                   approver.username AS approvedByName,
                   it.created_at AS createdAt, it.reviewed_at AS reviewedAt,
                   it.completed_at AS completedAt
            FROM inventory_transfer it
            JOIN inventory_transfer_item iti ON iti.transfer_id = it.id
            JOIN shop ss ON ss.id = it.source_shop_id
            JOIN shop ds ON ds.id = it.destination_shop_id
            JOIN stock_location sl ON sl.id = it.source_location_id
            JOIN stock_location dl ON dl.id = it.destination_location_id
            JOIN account creator ON creator.id = it.created_by
            LEFT JOIN account approver ON approver.id = it.approved_by
            WHERE it.tenant_id = ?
              AND (it.source_shop_id = ? OR it.destination_shop_id = ?)
            %s
            ORDER BY it.created_at DESC, it.id DESC
            LIMIT ? OFFSET ?
            """.formatted(statusWhere),
            listArgs.toArray()
        );
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_transfer it
            WHERE it.tenant_id = ?
              AND (it.source_shop_id = ? OR it.destination_shop_id = ?)
            %s
            """.formatted(statusWhere),
            Long.class,
            args.toArray()
        );
        return pageResult(records, total, safePage, safeLimit);
    }

    @Transactional
    public Map<String, Object> adjust(
        TenantPrincipal principal,
        InventoryAdjustmentRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "inventory:manage"
        );
        Map<String, Object> existing = findMovement(
            principal.tenantId(), request.idempotencyKey().trim()
        );
        if (existing != null) return existing;

        validateLocation(principal.tenantId(), shopId, request.locationId());
        validateProduct(principal.tenantId(), shopId, request.productId());
        BigDecimal delta = quantity(request.quantityDelta());
        if (delta.signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "库存变动数量不能为0");
        }
        String movementType = request.movementType().trim().toUpperCase(Locale.ROOT);
        validateMovementDirection(movementType, delta);
        Map<String, Object> balance = lockBalance(
            principal.tenantId(), request.locationId(), request.productId()
        );
        requireVersion(balance, request.version(), "库存");
        BigDecimal onHand = quantity(balance.get("quantityOnHand"));
        BigDecimal reserved = quantity(balance.get("quantityReserved"));
        BigDecimal after = onHand.add(delta);
        if (after.signum() < 0 || after.compareTo(reserved) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "可用库存不足，不能完成本次出库");
        }
        updateBalance(
            principal.tenantId(), request.locationId(), request.productId(),
            after, reserved, request.version()
        );
        syncProductStock(principal.tenantId(), request.productId());
        long movementId = insertMovement(
            principal, shopId, request.locationId(), request.productId(),
            movementType, delta, after, null, trimToNull(request.referenceNo()),
            request.idempotencyKey().trim(), request.remark().trim()
        );
        audit(
            principal, shopId, "INVENTORY_ADJUST", "INVENTORY_MOVEMENT",
            movementId, request.referenceNo(), delta, request.remark()
        );
        return movementResult(movementId);
    }

    @Transactional
    public Map<String, Object> createTransfer(
        TenantPrincipal principal,
        InventoryTransferRequest request
    ) {
        long sourceShopId = tenantAccessService.requireShopPermission(
            principal,
            request.sourceShopId(),
            "inventory:manage"
        );
        String key = request.idempotencyKey().trim();
        Map<String, Object> existing = findTransfer(principal.tenantId(), key);
        if (existing != null) return existing;

        Map<String, Object> sourceLocation = validateLocation(
            principal.tenantId(), sourceShopId, request.sourceLocationId()
        );
        Map<String, Object> destinationLocation = locationById(
            principal.tenantId(), request.destinationLocationId()
        );
        long destinationShopId = number(destinationLocation.get("shopId"));
        tenantAccessService.requireShopPermission(
            principal,
            destinationShopId,
            "inventory:manage"
        );
        if (number(sourceLocation.get("id")) == number(destinationLocation.get("id"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "调出和调入库存地点不能相同");
        }
        Map<String, Object> sourceProduct = validateProduct(
            principal.tenantId(), sourceShopId, request.productId()
        );
        Map<String, Object> destinationProduct = resolveDestinationProduct(
            principal.tenantId(), destinationShopId, sourceProduct
        );
        Map<String, Object> sourceBalance = lockBalance(
            principal.tenantId(), request.sourceLocationId(), request.productId()
        );
        requireVersion(sourceBalance, request.version(), "库存");
        BigDecimal amount = positiveQuantity(request.quantity(), "调拨数量");
        BigDecimal onHand = quantity(sourceBalance.get("quantityOnHand"));
        BigDecimal reserved = quantity(sourceBalance.get("quantityReserved"));
        if (onHand.subtract(reserved).compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "可用库存不足，不能创建调拨单");
        }
        updateBalance(
            principal.tenantId(), request.sourceLocationId(), request.productId(),
            onHand, reserved.add(amount), request.version()
        );

        String transferNo = businessNo("TR");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO inventory_transfer (
                    tenant_id, transfer_no, source_shop_id, source_location_id,
                    destination_shop_id, destination_location_id, status,
                    idempotency_key, remark, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setString(2, transferNo);
            statement.setLong(3, sourceShopId);
            statement.setLong(4, request.sourceLocationId());
            statement.setLong(5, destinationShopId);
            statement.setLong(6, request.destinationLocationId());
            statement.setString(7, key);
            statement.setString(8, trimToNull(request.remark()));
            statement.setLong(9, principal.accountId());
            return statement;
        }, keyHolder);
        Number keyValue = keyHolder.getKey();
        if (keyValue == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "调拨单编号生成失败");
        }
        long transferId = keyValue.longValue();
        jdbcTemplate.update(
            """
            INSERT INTO inventory_transfer_item (
                transfer_id, source_product_id, destination_product_id,
                sku_snapshot, product_name_snapshot, quantity
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            transferId,
            request.productId(),
            number(destinationProduct.get("id")),
            sourceProduct.get("sku"),
            sourceProduct.get("name"),
            amount
        );
        audit(
            principal, sourceShopId, "INVENTORY_TRANSFER_CREATE", "INVENTORY_TRANSFER",
            transferId, transferNo, amount, request.remark()
        );
        return transferResult(transferId);
    }

    @Transactional
    public Map<String, Object> decideTransfer(
        TenantPrincipal principal,
        long transferId,
        InventoryTransferDecisionRequest request
    ) {
        tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "inventory:manage"
        );
        Map<String, Object> transfer = lockTransfer(principal.tenantId(), transferId);
        long sourceShopId = number(transfer.get("sourceShopId"));
        long destinationShopId = number(transfer.get("destinationShopId"));
        if (request.shopId() != sourceShopId && request.shopId() != destinationShopId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "该调拨单不属于当前门店范围");
        }
        tenantAccessService.requireShopPermission(
            principal,
            sourceShopId,
            "inventory:manage"
        );
        tenantAccessService.requireShopPermission(
            principal,
            destinationShopId,
            "inventory:manage"
        );
        requireVersion(transfer, request.version(), "调拨单");
        if (!"PENDING".equals(transfer.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "该调拨单已完成审核");
        }
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if ("REJECT".equals(action) && trimToNull(request.decisionNote()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "拒绝调拨必须填写审核说明");
        }

        Map<String, Object> item = jdbcTemplate.queryForMap(
            """
            SELECT source_product_id AS sourceProductId,
                   destination_product_id AS destinationProductId,
                   quantity
            FROM inventory_transfer_item
            WHERE transfer_id = ?
            """,
            transferId
        );
        long sourceLocationId = number(transfer.get("sourceLocationId"));
        long destinationLocationId = number(transfer.get("destinationLocationId"));
        long sourceProductId = number(item.get("sourceProductId"));
        long destinationProductId = number(item.get("destinationProductId"));
        BigDecimal amount = quantity(item.get("quantity"));
        Map<String, Object> sourceBalance;
        Map<String, Object> destinationBalance = null;
        if ("APPROVE".equals(action)) {
            Map<String, Map<String, Object>> pair = lockBalancePair(
                principal.tenantId(),
                sourceLocationId,
                sourceProductId,
                destinationLocationId,
                destinationProductId
            );
            sourceBalance = pair.get("source");
            destinationBalance = pair.get("destination");
        } else {
            sourceBalance = lockBalance(
                principal.tenantId(), sourceLocationId, sourceProductId
            );
        }
        BigDecimal sourceOnHand = quantity(sourceBalance.get("quantityOnHand"));
        BigDecimal sourceReserved = quantity(sourceBalance.get("quantityReserved"));
        if (sourceReserved.compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "调拨冻结库存异常，请先核对库存");
        }

        String newStatus;
        if ("APPROVE".equals(action)) {
            if (sourceOnHand.compareTo(amount) < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "调出库存不足，不能批准调拨");
            }
            BigDecimal destinationOnHand = quantity(destinationBalance.get("quantityOnHand"));
            BigDecimal destinationReserved = quantity(destinationBalance.get("quantityReserved"));
            BigDecimal sourceAfter = sourceOnHand.subtract(amount);
            BigDecimal destinationAfter = destinationOnHand.add(amount);
            forceUpdateBalance(
                principal.tenantId(), sourceLocationId, sourceProductId,
                sourceAfter, sourceReserved.subtract(amount)
            );
            forceUpdateBalance(
                principal.tenantId(), destinationLocationId, destinationProductId,
                destinationAfter, destinationReserved
            );
            syncProductStock(principal.tenantId(), sourceProductId);
            if (destinationProductId != sourceProductId) {
                syncProductStock(principal.tenantId(), destinationProductId);
            }
            insertMovement(
                principal, sourceShopId, sourceLocationId, sourceProductId,
                "TRANSFER_OUT", amount.negate(), sourceAfter, null,
                transfer.get("transferNo").toString(),
                "TRANSFER-OUT-" + transferId + "-" + sourceProductId,
                "库存调拨出库"
            );
            insertMovement(
                principal, destinationShopId, destinationLocationId, destinationProductId,
                "TRANSFER_IN", amount, destinationAfter, null,
                transfer.get("transferNo").toString(),
                "TRANSFER-IN-" + transferId + "-" + destinationProductId,
                "库存调拨入库"
            );
            newStatus = "APPROVED";
        } else {
            forceUpdateBalance(
                principal.tenantId(), sourceLocationId, sourceProductId,
                sourceOnHand, sourceReserved.subtract(amount)
            );
            newStatus = "REJECTED";
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE inventory_transfer
            SET status = ?, decision_note = ?, version = version + 1,
                approved_by = ?, reviewed_at = CURRENT_TIMESTAMP(3),
                completed_at = CASE WHEN ? = 'APPROVED'
                  THEN CURRENT_TIMESTAMP(3) ELSE NULL END
            WHERE id = ? AND tenant_id = ? AND version = ? AND status = 'PENDING'
            """,
            newStatus,
            trimToNull(request.decisionNote()),
            principal.accountId(),
            newStatus,
            transferId,
            principal.tenantId(),
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "调拨单已被其他人审核，请刷新后重试");
        }
        audit(
            principal, sourceShopId,
            "APPROVED".equals(newStatus)
                ? "INVENTORY_TRANSFER_APPROVE"
                : "INVENTORY_TRANSFER_REJECT",
            "INVENTORY_TRANSFER", transferId, transfer.get("transferNo").toString(),
            amount, request.decisionNote()
        );
        return transferResult(transferId);
    }

    @Transactional
    public void deductPaidOrder(
        TenantPrincipal principal,
        long shopId,
        long orderId
    ) {
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            """
            SELECT id, order_no AS orderNo, status,
                   inventory_deducted_at AS inventoryDeductedAt
            FROM sales_order
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            orderId,
            principal.tenantId(),
            shopId
        );
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Map<String, Object> order = orders.getFirst();
        if (order.get("inventoryDeductedAt") != null) return;
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            """
            SELECT product_id AS productId, SUM(quantity) AS quantity
            FROM sales_order_item
            WHERE order_id = ? AND item_type = 'PRODUCT' AND product_id IS NOT NULL
            GROUP BY product_id
            ORDER BY product_id
            """,
            orderId
        );
        Long locationId = jdbcTemplate.queryForObject(
            """
            SELECT id FROM stock_location
            WHERE tenant_id = ? AND shop_id = ? AND location_type = 'SHOP'
              AND status = 'ACTIVE'
            ORDER BY id LIMIT 1
            """,
            Long.class,
            principal.tenantId(),
            shopId
        );
        if (locationId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "门店主仓尚未配置");
        }
        for (Map<String, Object> item : items) {
            long productId = number(item.get("productId"));
            BigDecimal amount = positiveQuantity(item.get("quantity"), "商品数量");
            Map<String, Object> balance = lockBalance(
                principal.tenantId(), locationId, productId
            );
            BigDecimal onHand = quantity(balance.get("quantityOnHand"));
            BigDecimal reserved = quantity(balance.get("quantityReserved"));
            BigDecimal after = onHand.subtract(amount);
            if (after.signum() < 0 || after.compareTo(reserved) < 0) {
                Map<String, Object> product = validateProduct(
                    principal.tenantId(), shopId, productId
                );
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "商品“" + product.get("name") + "”库存不足，收款未完成"
                );
            }
            forceUpdateBalance(
                principal.tenantId(), locationId, productId, after, reserved
            );
            syncProductStock(principal.tenantId(), productId);
            insertMovement(
                principal, shopId, locationId, productId,
                "SALE_OUT", amount.negate(), after, orderId,
                order.get("orderNo").toString(),
                "ORDER-SALE-" + orderId + "-" + productId,
                "订单完成收款自动扣库"
            );
        }
        jdbcTemplate.update(
            """
            UPDATE sales_order
            SET inventory_deducted_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ? AND inventory_deducted_at IS NULL
            """,
            orderId,
            principal.tenantId()
        );
    }

    public List<Map<String, Object>> consumeForService(
        TenantPrincipal principal,
        long shopId,
        long serviceRecordId,
        List<InventoryConsumptionLine> lines,
        String idempotencyPrefix
    ) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<InventoryConsumptionLine> ordered = new ArrayList<>(lines);
        ordered.sort(
            java.util.Comparator.comparingLong(InventoryConsumptionLine::locationId)
                .thenComparingLong(InventoryConsumptionLine::productId)
        );
        Set<String> uniqueLines = new HashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (InventoryConsumptionLine line : ordered) {
            String lineKey = line.locationId() + ":" + line.productId();
            if (!uniqueLines.add(lineKey)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一库存地点的同一耗材不能重复填写");
            }
            validateLocation(principal.tenantId(), shopId, line.locationId());
            validateProduct(principal.tenantId(), shopId, line.productId());
            BigDecimal amount = positiveQuantity(line.quantity(), "耗材领用数量");
            Map<String, Object> balance = lockBalance(
                principal.tenantId(), line.locationId(), line.productId()
            );
            requireVersion(balance, line.balanceVersion(), "库存");
            BigDecimal onHand = quantity(balance.get("quantityOnHand"));
            BigDecimal reserved = quantity(balance.get("quantityReserved"));
            BigDecimal after = onHand.subtract(amount);
            if (after.signum() < 0 || after.compareTo(reserved) < 0) {
                Map<String, Object> product = validateProduct(
                    principal.tenantId(), shopId, line.productId()
                );
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "耗材“" + product.get("name") + "”可用库存不足，服务未完成"
                );
            }
            updateBalance(
                principal.tenantId(), line.locationId(), line.productId(),
                after, reserved, line.balanceVersion()
            );
            syncProductStock(principal.tenantId(), line.productId());
            long movementId = insertMovement(
                principal,
                shopId,
                line.locationId(),
                line.productId(),
                "SERVICE_USE",
                amount.negate(),
                after,
                null,
                serviceRecordId,
                "SERVICE-" + serviceRecordId,
                idempotencyPrefix + "-" + line.locationId() + "-" + line.productId(),
                "到店护理耗材领用"
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("movementId", movementId);
            result.put("locationId", line.locationId());
            result.put("productId", line.productId());
            result.put("quantity", amount);
            result.put("balanceAfter", after);
            results.add(result);
        }
        return results;
    }

    private List<Map<String, Object>> balanceRows(long tenantId, long productId) {
        return jdbcTemplate.queryForList(
            """
            SELECT sb.id, sb.location_id AS locationId, sl.name AS locationName,
                   sl.location_type AS locationType,
                   sb.quantity_on_hand AS quantityOnHand,
                   sb.quantity_reserved AS quantityReserved,
                   sb.quantity_on_hand - sb.quantity_reserved AS quantityAvailable,
                   sb.version, sb.updated_at AS updatedAt
            FROM stock_balance sb
            JOIN stock_location sl ON sl.id = sb.location_id
            WHERE sb.tenant_id = ? AND sb.product_id = ?
            ORDER BY FIELD(sl.location_type, 'SHOP', 'ROOM', 'HEADQUARTERS'), sl.id
            """,
            tenantId,
            productId
        );
    }

    private List<Map<String, Object>> locationsForShops(long tenantId, List<Long> shopIds) {
        if (shopIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(shopIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(shopIds);
        return jdbcTemplate.queryForList(
            """
            SELECT sl.id, sl.shop_id AS shopId, sh.name AS shopName,
                   sl.location_code AS locationCode, sl.name,
                   sl.location_type AS locationType
            FROM stock_location sl
            JOIN shop sh ON sh.id = sl.shop_id
            WHERE sl.tenant_id = ? AND sl.shop_id IN (%s)
              AND sl.status = 'ACTIVE'
            ORDER BY sh.name, FIELD(sl.location_type, 'SHOP', 'ROOM', 'HEADQUARTERS'), sl.id
            """.formatted(placeholders),
            args.toArray()
        );
    }

    private Map<String, Object> validateLocation(long tenantId, long shopId, long locationId) {
        Map<String, Object> location = locationById(tenantId, locationId);
        if (number(location.get("shopId")) != shopId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "库存地点不属于所选门店");
        }
        return location;
    }

    private Map<String, Object> locationById(long tenantId, long locationId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT id, shop_id AS shopId, location_code AS locationCode,
                       name, location_type AS locationType
                FROM stock_location
                WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
                """,
                locationId,
                tenantId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "库存地点不存在");
        }
    }

    private Map<String, Object> validateProduct(long tenantId, long shopId, long productId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT id, shop_id AS shopId, sku, name
                FROM product
                WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
                """,
                productId,
                tenantId,
                shopId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "商品不存在或不属于所选门店");
        }
    }

    private Map<String, Object> resolveDestinationProduct(
        long tenantId,
        long destinationShopId,
        Map<String, Object> sourceProduct
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, sku, name
            FROM product
            WHERE tenant_id = ? AND shop_id = ? AND sku = ? AND status = 'ACTIVE'
            """,
            tenantId,
            destinationShopId,
            sourceProduct.get("sku")
        );
        if (rows.isEmpty()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "调入门店尚未配置同一 SKU 商品，不能跨店调拨"
            );
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockBalance(long tenantId, long locationId, long productId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, quantity_on_hand AS quantityOnHand,
                   quantity_reserved AS quantityReserved, version
            FROM stock_balance
            WHERE tenant_id = ? AND location_id = ? AND product_id = ?
            FOR UPDATE
            """,
            tenantId,
            locationId,
            productId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "商品在该库存地点尚未建立库存账");
        }
        return rows.getFirst();
    }

    private Map<String, Map<String, Object>> lockBalancePair(
        long tenantId,
        long sourceLocationId,
        long sourceProductId,
        long destinationLocationId,
        long destinationProductId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, location_id AS locationId, product_id AS productId,
                   quantity_on_hand AS quantityOnHand,
                   quantity_reserved AS quantityReserved, version
            FROM stock_balance
            WHERE tenant_id = ?
              AND (
                (location_id = ? AND product_id = ?)
                OR (location_id = ? AND product_id = ?)
              )
            ORDER BY location_id, product_id
            FOR UPDATE
            """,
            tenantId,
            sourceLocationId,
            sourceProductId,
            destinationLocationId,
            destinationProductId
        );
        Map<String, Object> source = null;
        Map<String, Object> destination = null;
        for (Map<String, Object> row : rows) {
            long locationId = number(row.get("locationId"));
            long productId = number(row.get("productId"));
            if (locationId == sourceLocationId && productId == sourceProductId) {
                source = row;
            }
            if (locationId == destinationLocationId && productId == destinationProductId) {
                destination = row;
            }
        }
        if (source == null || destination == null) {
            throw new ApiException(HttpStatus.CONFLICT, "调拨两端库存账不完整");
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("destination", destination);
        return result;
    }

    private void updateBalance(
        long tenantId,
        long locationId,
        long productId,
        BigDecimal onHand,
        BigDecimal reserved,
        int version
    ) {
        int changed = jdbcTemplate.update(
            """
            UPDATE stock_balance
            SET quantity_on_hand = ?, quantity_reserved = ?, version = version + 1
            WHERE tenant_id = ? AND location_id = ? AND product_id = ? AND version = ?
            """,
            onHand,
            reserved,
            tenantId,
            locationId,
            productId,
            version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "库存已被其他操作修改，请刷新后重试");
        }
    }

    private void forceUpdateBalance(
        long tenantId,
        long locationId,
        long productId,
        BigDecimal onHand,
        BigDecimal reserved
    ) {
        int changed = jdbcTemplate.update(
            """
            UPDATE stock_balance
            SET quantity_on_hand = ?, quantity_reserved = ?, version = version + 1
            WHERE tenant_id = ? AND location_id = ? AND product_id = ?
            """,
            onHand,
            reserved,
            tenantId,
            locationId,
            productId
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "库存账不存在");
        }
    }

    private void syncProductStock(long tenantId, long productId) {
        jdbcTemplate.update(
            """
            UPDATE product p
            SET p.stock_quantity = (
              SELECT COALESCE(SUM(sb.quantity_on_hand), 0)
              FROM stock_balance sb
              WHERE sb.tenant_id = ? AND sb.product_id = p.id
            )
            WHERE p.id = ? AND p.tenant_id = ?
            """,
            tenantId,
            productId,
            tenantId
        );
    }

    private long insertMovement(
        TenantPrincipal principal,
        long shopId,
        long locationId,
        long productId,
        String movementType,
        BigDecimal delta,
        BigDecimal balanceAfter,
        Long orderId,
        String referenceNo,
        String idempotencyKey,
        String remark
    ) {
        return insertMovement(
            principal, shopId, locationId, productId, movementType,
            delta, balanceAfter, orderId, null, referenceNo,
            idempotencyKey, remark
        );
    }

    private long insertMovement(
        TenantPrincipal principal,
        long shopId,
        long locationId,
        long productId,
        String movementType,
        BigDecimal delta,
        BigDecimal balanceAfter,
        Long orderId,
        Long serviceRecordId,
        String referenceNo,
        String idempotencyKey,
        String remark
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO inventory_movement (
                    tenant_id, shop_id, location_id, product_id, movement_type,
                    quantity_delta, balance_after, order_id, service_record_id,
                    reference_no, idempotency_key, remark, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, locationId);
            statement.setLong(4, productId);
            statement.setString(5, movementType);
            statement.setBigDecimal(6, delta);
            statement.setBigDecimal(7, balanceAfter);
            if (orderId == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, orderId);
            if (serviceRecordId == null) statement.setNull(9, java.sql.Types.BIGINT);
            else statement.setLong(9, serviceRecordId);
            statement.setString(10, trimToNull(referenceNo));
            statement.setString(11, idempotencyKey);
            statement.setString(12, trimToNull(remark));
            statement.setLong(13, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "库存流水生成失败");
        }
        return key.longValue();
    }

    private Map<String, Object> lockTransfer(long tenantId, long transferId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, transfer_no AS transferNo, source_shop_id AS sourceShopId,
                   source_location_id AS sourceLocationId,
                   destination_shop_id AS destinationShopId,
                   destination_location_id AS destinationLocationId,
                   status, version
            FROM inventory_transfer
            WHERE id = ? AND tenant_id = ?
            FOR UPDATE
            """,
            transferId,
            tenantId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "调拨单不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> findMovement(long tenantId, String key) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, location_id AS locationId,
                   product_id AS productId, movement_type AS movementType,
                   quantity_delta AS quantityDelta, balance_after AS balanceAfter,
                   reference_no AS referenceNo, remark, created_at AS createdAt
            FROM inventory_movement
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            tenantId,
            key
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> findTransfer(long tenantId, String key) {
        List<Long> rows = jdbcTemplate.queryForList(
            """
            SELECT id FROM inventory_transfer
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            Long.class,
            tenantId,
            key
        );
        return rows.isEmpty() ? null : transferResult(rows.getFirst());
    }

    private Map<String, Object> movementResult(long movementId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT id, shop_id AS shopId, location_id AS locationId,
                       product_id AS productId, movement_type AS movementType,
                       quantity_delta AS quantityDelta, balance_after AS balanceAfter,
                       reference_no AS referenceNo, remark, created_at AS createdAt
                FROM inventory_movement
                WHERE id = ?
                """,
                movementId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "库存流水不存在");
        }
    }

    private Map<String, Object> transferResult(long transferId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT it.id, it.transfer_no AS transferNo,
                       it.source_shop_id AS sourceShopId,
                       it.source_location_id AS sourceLocationId,
                       it.destination_shop_id AS destinationShopId,
                       it.destination_location_id AS destinationLocationId,
                       iti.source_product_id AS productId,
                       iti.destination_product_id AS destinationProductId,
                       iti.quantity, it.status, it.remark,
                       it.decision_note AS decisionNote, it.version,
                       it.created_at AS createdAt, it.reviewed_at AS reviewedAt,
                       it.completed_at AS completedAt
                FROM inventory_transfer it
                JOIN inventory_transfer_item iti ON iti.transfer_id = it.id
                WHERE it.id = ?
                """,
                transferId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "调拨单不存在");
        }
    }

    private void validateMovementDirection(String movementType, BigDecimal delta) {
        if (List.of("PURCHASE_IN", "RETURN_IN", "MANUAL_IN").contains(movementType)
            && delta.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "入库数量必须为正数");
        }
        if (List.of("MANUAL_OUT", "SERVICE_USE").contains(movementType)
            && delta.signum() > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "出库数量必须为负数");
        }
    }

    private String normalizeMovementType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!MOVEMENT_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的库存流水类型");
        }
        return normalized;
    }

    private String normalizeTransferStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的调拨状态");
        }
        return normalized;
    }

    private int safePageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 100));
    }

    private BigDecimal quantity(Object value) {
        return new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveQuantity(Object value, String name) {
        BigDecimal result = quantity(value);
        if (result.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, name + "必须大于0");
        }
        return result;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireVersion(Map<String, Object> row, int expected, String entityName) {
        if (number(row.get("version")) != expected) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                entityName + "已被其他操作修改，请刷新后重试"
            );
        }
    }

    private String businessNo(String prefix) {
        return prefix
            + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
            )
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private Map<String, Object> pageResult(
        List<Map<String, Object>> records,
        Long total,
        int page,
        int pageSize
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId,
        String businessNo,
        BigDecimal quantity,
        String note
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id,
                after_data, metadata
            ) VALUES (?, ?, ?, ?, ?, ?,
                      JSON_OBJECT('businessNo', ?, 'quantity', ?),
                      JSON_OBJECT('note', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityType,
            entityId,
            businessNo,
            quantity,
            note
        );
    }
}
