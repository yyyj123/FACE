package com.face.platform.inventory;

import com.face.platform.api.ApiException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class InventoryReceiptApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    public InventoryReceiptApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
    }

    @Transactional
    public PurchaseReceiptPosting receivePurchaseBatch(
        TenantPrincipal principal,
        long shopId,
        long purchaseReceiptId,
        long purchaseOrderItemId,
        long productId,
        long locationId,
        String batchNo,
        String vendorBatchNo,
        LocalDate producedDate,
        LocalDate expiryDate,
        BigDecimal quantity,
        BigDecimal unitCost,
        String receiptNo,
        String remark
    ) {
        BigDecimal received = positiveQuantity(quantity);
        BigDecimal cost = nonNegativeMoney(unitCost);
        requireLocation(principal.tenantId(), shopId, locationId);
        requireProduct(principal.tenantId(), shopId, productId);

        Map<String, Object> balance = lockBalance(
            principal.tenantId(), locationId, productId
        );
        BigDecimal before = decimal(balance.get("quantityOnHand"));
        BigDecimal reserved = decimal(balance.get("quantityReserved"));
        BigDecimal after = before.add(received);
        int changed = jdbcTemplate.update(
            """
            UPDATE stock_balance
            SET quantity_on_hand = ?, quantity_reserved = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ?
            """,
            after,
            reserved,
            number(balance.get("id")),
            principal.tenantId(),
            number(balance.get("version"))
        );
        if (changed != 1) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "库存已被其他入库操作修改，请刷新后重试"
            );
        }

        long batchId = insertBatch(
            principal,
            shopId,
            purchaseReceiptId,
            productId,
            locationId,
            batchNo,
            trimToNull(vendorBatchNo),
            producedDate,
            expiryDate,
            received,
            cost
        );
        String businessKey = "PURCHASE_RECEIPT:"
            + purchaseReceiptId + ":" + purchaseOrderItemId;
        jdbcTemplate.update(
            """
            INSERT INTO stock_batch_movement (
                tenant_id, shop_id, stock_batch_id, movement_type,
                quantity_delta, balance_after, reference_type, reference_id,
                business_key, remark, created_by
            ) VALUES (?, ?, ?, 'PURCHASE_IN', ?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            batchId,
            received,
            received,
            purchaseReceiptId,
            businessKey,
            trimToNull(remark),
            principal.accountId()
        );
        long inventoryMovementId = insertInventoryMovement(
            principal,
            shopId,
            locationId,
            productId,
            received,
            after,
            receiptNo,
            businessKey,
            remark
        );
        syncProductStock(principal.tenantId(), productId);
        return new PurchaseReceiptPosting(batchId, inventoryMovementId, after);
    }

    public List<Map<String, Object>> batches(
        TenantPrincipal principal,
        long shopId,
        Long productId,
        Long locationId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "inventory:view");
        StringBuilder filters = new StringBuilder();
        var args = new java.util.ArrayList<Object>();
        args.add(principal.tenantId());
        args.add(shopId);
        if (productId != null) {
            filters.append(" AND b.product_id = ?");
            args.add(productId);
        }
        if (locationId != null) {
            filters.append(" AND b.location_id = ?");
            args.add(locationId);
        }
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT b.id, b.shop_id AS shopId, b.location_id AS locationId,
                   sl.name AS locationName, b.product_id AS productId,
                   p.sku, p.name AS productName, p.unit_name AS unitName,
                   b.batch_no AS batchNo, b.vendor_batch_no AS vendorBatchNo,
                   b.produced_date AS producedDate, b.expiry_date AS expiryDate,
                   b.unit_cost AS unitCost, b.quantity_received AS quantityReceived,
                   b.quantity_on_hand AS quantityOnHand,
                   b.quantity_reserved AS quantityReserved, b.status,
                   b.source_type AS sourceType, b.source_id AS sourceId,
                   b.version, b.created_at AS createdAt
            FROM stock_batch b
            JOIN stock_location sl
              ON sl.id = b.location_id AND sl.tenant_id = b.tenant_id
            JOIN product p
              ON p.id = b.product_id AND p.tenant_id = b.tenant_id
            WHERE b.tenant_id = ? AND b.shop_id = ?
            %s
            ORDER BY b.product_id, b.location_id,
                     b.expiry_date IS NULL, b.expiry_date, b.created_at, b.id
            LIMIT 500
            """.formatted(filters),
            args.toArray()
        );
        for (Map<String, Object> record : records) {
            normalizeDate(record, "producedDate");
            normalizeDate(record, "expiryDate");
        }
        return records;
    }

    private long insertBatch(
        TenantPrincipal principal,
        long shopId,
        long receiptId,
        long productId,
        long locationId,
        String batchNo,
        String vendorBatchNo,
        LocalDate producedDate,
        LocalDate expiryDate,
        BigDecimal quantity,
        BigDecimal unitCost
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO stock_batch (
                    tenant_id, shop_id, location_id, product_id,
                    batch_no, vendor_batch_no, produced_date, expiry_date,
                    unit_cost, quantity_received, quantity_on_hand,
                    quantity_reserved, status, source_type, source_id,
                    version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0,
                          'ACTIVE', 'PURCHASE_RECEIPT', ?, 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, locationId);
            statement.setLong(4, productId);
            statement.setString(5, requiredText(batchNo, "批次号"));
            statement.setString(6, vendorBatchNo);
            if (producedDate == null) {
                statement.setNull(7, java.sql.Types.DATE);
            } else {
                statement.setObject(7, producedDate);
            }
            if (expiryDate == null) {
                statement.setNull(8, java.sql.Types.DATE);
            } else {
                statement.setObject(8, expiryDate);
            }
            statement.setBigDecimal(9, unitCost);
            statement.setBigDecimal(10, quantity);
            statement.setBigDecimal(11, quantity);
            statement.setLong(12, receiptId);
            statement.setLong(13, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "库存批次创建失败");
        }
        return generated.longValue();
    }

    private long insertInventoryMovement(
        TenantPrincipal principal,
        long shopId,
        long locationId,
        long productId,
        BigDecimal quantity,
        BigDecimal balanceAfter,
        String receiptNo,
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
                ) VALUES (?, ?, ?, ?, 'PURCHASE_IN', ?, ?, NULL, NULL, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, locationId);
            statement.setLong(4, productId);
            statement.setBigDecimal(5, quantity);
            statement.setBigDecimal(6, balanceAfter);
            statement.setString(7, requiredText(receiptNo, "收货单号"));
            statement.setString(8, idempotencyKey);
            statement.setString(9, trimToNull(remark));
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "采购入库流水创建失败");
        }
        return generated.longValue();
    }

    private Map<String, Object> lockBalance(
        long tenantId,
        long locationId,
        long productId
    ) {
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
            throw new ApiException(
                HttpStatus.CONFLICT,
                "商品在该库存地点尚未建立库存账"
            );
        }
        return rows.getFirst();
    }

    private void requireLocation(long tenantId, long shopId, long locationId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM stock_location
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            """,
            Integer.class,
            locationId,
            tenantId,
            shopId
        );
        if (count == null || count != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "库存地点不存在或不属于当前门店");
        }
    }

    private void requireProduct(long tenantId, long shopId, long productId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM product
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            """,
            Integer.class,
            productId,
            tenantId,
            shopId
        );
        if (count == null || count != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "商品不存在或不属于当前门店");
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

    private BigDecimal positiveQuantity(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "收货数量不能为空");
        BigDecimal normalized;
        try {
            normalized = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收货数量最多保留3位小数");
        }
        if (normalized.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收货数量必须大于0");
        }
        return normalized;
    }

    private BigDecimal nonNegativeMoney(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "入库成本不能为空");
        BigDecimal normalized;
        try {
            normalized = value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "入库成本最多保留4位小数");
        }
        if (normalized.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "入库成本不能小于0");
        }
        return normalized;
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String requiredText(String value, String label) {
        String result = trimToNull(value);
        if (result == null) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        return result;
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

    public record PurchaseReceiptPosting(
        long stockBatchId,
        long inventoryMovementId,
        BigDecimal balanceAfter
    ) {
    }
}
