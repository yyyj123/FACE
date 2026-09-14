package com.face.platform.store;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.payment.PaymentAdapterRegistry;
import com.face.platform.payment.PaymentChannelAdapter;
import com.face.platform.points.PointsApplicationService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MallApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final PointsApplicationService pointsService;
    private final PaymentAdapterRegistry adapterRegistry;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public MallApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        PointsApplicationService pointsService,
        PaymentAdapterRegistry adapterRegistry,
        OutboxEventService outboxEventService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.pointsService = pointsService;
        this.adapterRegistry = adapterRegistry;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> catalog(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            """
            SELECT p.id, p.product_code AS productCode, p.product_type AS productType,
                   p.name, p.brand_name AS brandName, p.cover_url AS coverUrl,
                   p.description, p.delivery_mode AS deliveryMode,
                   p.after_sale_policy AS afterSalePolicy,
                   c.id AS categoryId, c.name AS categoryName
            FROM mall_product p
            JOIN mall_category c ON c.id = p.category_id AND c.status = 'ACTIVE'
            WHERE p.tenant_id = ? AND p.status = 'ON_SALE'
              AND (p.shop_id IS NULL OR p.shop_id = ?)
              AND (p.sale_starts_at IS NULL OR p.sale_starts_at <= CURRENT_TIMESTAMP(3))
              AND (p.sale_ends_at IS NULL OR p.sale_ends_at > CURRENT_TIMESTAMP(3))
            ORDER BY c.sort_order, p.id DESC
            """,
            principal.tenantId(), principal.homeShopId()
        );
        for (Map<String, Object> product : products) {
            product.put("skus", jdbcTemplate.queryForList(
                """
                SELECT s.id, s.sku_code AS skuCode, s.spec_json AS spec,
                       s.image_url AS imageUrl, s.cash_price AS cashPrice,
                       s.points_price AS pointsPrice, s.combo_cash_price AS comboCashPrice,
                       s.combo_points_price AS comboPointsPrice,
                       s.cash_enabled AS cashEnabled, s.points_enabled AS pointsEnabled,
                       s.combo_enabled AS comboEnabled, s.purchase_limit AS purchaseLimit,
                       COALESCE(i.available_quantity, 0) AS availableQuantity
                FROM mall_sku s
                LEFT JOIN mall_sku_inventory i ON i.sku_id = s.id AND i.shop_id = ?
                WHERE s.tenant_id = ? AND s.product_id = ? AND s.status = 'ACTIVE'
                ORDER BY s.id
                """,
                principal.homeShopId(), principal.tenantId(), number(product.get("id"))
            ));
        }
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
            "SELECT id, category_code AS categoryCode, name FROM mall_category WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY sort_order, id",
            principal.tenantId()
        );
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
            """
            SELECT mc.id, mc.coupon_no AS couponNo, r.name, r.coupon_type AS couponType,
                   r.fixed_amount AS fixedAmount, r.minimum_cash_amount AS minimumCashAmount,
                   r.product_scope_json AS productScope, mc.valid_until AS validUntil
            FROM member_mall_coupon mc
            JOIN mall_coupon_rule r ON r.id = mc.coupon_rule_id
            WHERE mc.tenant_id = ? AND mc.member_id = ?
              AND mc.status IN ('AVAILABLE', 'RETURNED')
              AND mc.valid_from <= CURRENT_TIMESTAMP(3) AND mc.valid_until > CURRENT_TIMESTAMP(3)
            ORDER BY mc.valid_until, mc.id
            """,
            principal.tenantId(), memberId
        );
        return Map.of("categories", categories, "products", products, "coupons", coupons);
    }

    public Map<String, Object> administration(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "mall:view");
        List<Map<String, Object>> inventory = jdbcTemplate.queryForList(
            """
            SELECT i.id, p.name AS productName, s.id AS skuId, s.sku_code AS skuCode,
                   s.spec_json AS spec, i.available_quantity AS availableQuantity,
                   i.reserved_quantity AS reservedQuantity, i.sold_quantity AS soldQuantity,
                   i.warning_threshold AS warningThreshold, i.version
            FROM mall_sku_inventory i
            JOIN mall_sku s ON s.id = i.sku_id
            JOIN mall_product p ON p.id = s.product_id
            WHERE i.tenant_id = ? AND i.shop_id = ? ORDER BY p.id, s.id
            """,
            principal.tenantId(), shopId
        );
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            """
            SELECT o.id, o.order_no AS orderNo, o.member_id AS memberId,
                   o.cash_amount AS cashAmount, o.points_amount AS pointsAmount,
                   o.status, o.created_at AS createdAt,
                   COUNT(DISTINCT so.id) AS subOrderCount,
                   COUNT(DISTINCT ps.package_id) AS packageCount
            FROM mall_order o
            LEFT JOIN mall_sub_order so ON so.mall_order_id = o.id
            LEFT JOIN mall_package_sub_order ps ON ps.sub_order_id = so.id
            WHERE o.tenant_id = ? AND o.shop_id = ?
            GROUP BY o.id ORDER BY o.created_at DESC LIMIT 100
            """,
            principal.tenantId(), shopId
        );
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            "SELECT id, product_code AS productCode, product_type AS productType, name, brand_name AS brandName, cover_url AS coverUrl, delivery_mode AS deliveryMode, status, version FROM mall_product WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) ORDER BY id DESC",
            principal.tenantId(), shopId
        );
        List<Map<String, Object>> couponRules = jdbcTemplate.queryForList(
            "SELECT id, coupon_code AS couponCode, name, coupon_type AS couponType, fixed_amount AS fixedAmount, minimum_cash_amount AS minimumCashAmount, valid_days AS validDays, status, version FROM mall_coupon_rule WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) ORDER BY id DESC",
            principal.tenantId(), shopId
        );
        return Map.of(
            "inventory", inventory,
            "orders", orders,
            "products", products,
            "couponRules", couponRules
        );
    }

    @Transactional
    public Map<String, Object> createProduct(TenantPrincipal principal, ProductCommand command) {
        long shopId = accessService.requireShopPermission(principal, command.shopId(), "mall:manage");
        String categoryCode = required(command.categoryCode(), "分类编码");
        jdbcTemplate.update(
            "INSERT INTO mall_category (tenant_id, category_code, name, sort_order, status) VALUES (?, ?, ?, 0, 'ACTIVE') ON DUPLICATE KEY UPDATE name = VALUES(name), status = 'ACTIVE'",
            principal.tenantId(), categoryCode, required(command.categoryName(), "分类名称")
        );
        long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM mall_category WHERE tenant_id = ? AND category_code = ?",
            Long.class, principal.tenantId(), categoryCode
        );
        String productType = upper(command.productType());
        String deliveryMode = upper(command.deliveryMode());
        if (!List.of("PHYSICAL", "CARE_ENTITLEMENT").contains(productType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的商品类型");
        }
        if (!List.of("DELIVERY", "PICKUP", "BOTH", "DIGITAL").contains(deliveryMode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的履约方式");
        }
        if ("CARE_ENTITLEMENT".equals(productType) && command.careServiceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "护理兑换商品必须关联护理项目");
        }
        KeyHolder productKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO mall_product (
                    tenant_id, shop_id, category_id, product_code, product_type,
                    name, brand_name, description, cover_url, care_service_id, delivery_mode,
                    freight_template_code, separate_shipping, after_sale_policy,
                    status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ON_SALE', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, categoryId);
            statement.setString(4, required(command.productCode(), "商品编码"));
            statement.setString(5, productType);
            statement.setString(6, required(command.name(), "商品名称"));
            statement.setString(7, trim(command.brandName()));
            statement.setString(8, trim(command.description()));
            statement.setString(9, trim(command.coverUrl()));
            if (command.careServiceId() == null) statement.setNull(10, java.sql.Types.BIGINT);
            else statement.setLong(10, command.careServiceId());
            statement.setString(11, deliveryMode);
            statement.setString(12, trim(command.freightTemplateCode()));
            statement.setBoolean(13, command.separateShipping());
            statement.setString(14, trim(command.afterSalePolicy()));
            statement.setLong(15, principal.accountId());
            return statement;
        }, productKey);
        long productId = generated(productKey, "商品创建失败");
        KeyHolder skuKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO mall_sku (
                    tenant_id, product_id, sku_code, spec_json, image_url,
                    cash_price, points_price, combo_cash_price, combo_points_price,
                    cash_enabled, points_enabled, combo_enabled, purchase_limit, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, productId);
            statement.setString(3, required(command.skuCode(), "SKU 编码"));
            statement.setString(4, json(command.spec() == null ? Map.of() : command.spec()));
            statement.setString(5, trim(command.coverUrl()));
            statement.setBigDecimal(6, command.cashPrice());
            if (command.pointsPrice() == null) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, command.pointsPrice());
            statement.setBigDecimal(8, command.comboCashPrice());
            if (command.comboPointsPrice() == null) statement.setNull(9, java.sql.Types.BIGINT);
            else statement.setLong(9, command.comboPointsPrice());
            statement.setBoolean(10, command.cashEnabled());
            statement.setBoolean(11, command.pointsEnabled());
            statement.setBoolean(12, command.comboEnabled());
            if (command.purchaseLimit() == null) statement.setNull(13, java.sql.Types.INTEGER);
            else statement.setInt(13, command.purchaseLimit());
            return statement;
        }, skuKey);
        long skuId = generated(skuKey, "SKU 创建失败");
        jdbcTemplate.update(
            "INSERT INTO mall_sku_inventory (tenant_id, shop_id, sku_id, available_quantity, warning_threshold) VALUES (?, ?, ?, 0, ?)",
            principal.tenantId(), shopId, skuId, command.warningThreshold()
        );
        outboxEventService.append(
            principal, shopId, "MALL_PRODUCT", String.valueOf(productId),
            "MallProductPublished", Map.of("productId", productId, "skuId", skuId)
        );
        return Map.of("productId", productId, "skuId", skuId, "status", "ON_SALE");
    }

    @Transactional
    public Map<String, Object> updateProductImage(
        TenantPrincipal principal, long shopId, long productId, String coverUrl
    ) {
        accessService.requireShopPermission(principal, shopId, "mall:manage");
        String safeCoverUrl = trim(coverUrl);
        if (safeCoverUrl != null && safeCoverUrl.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "商品图片地址不能超过 500 个字符");
        }
        int changed = jdbcTemplate.update(
            "UPDATE mall_product SET cover_url = ?, version = version + 1 WHERE id = ? AND tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)",
            safeCoverUrl, productId, principal.tenantId(), shopId
        );
        requireChanged(changed, "商品不存在或已被修改");
        jdbcTemplate.update(
            "UPDATE mall_sku SET image_url = ?, version = version + 1 WHERE tenant_id = ? AND product_id = ?",
            safeCoverUrl, principal.tenantId(), productId
        );
        outboxEventService.append(
            principal, shopId, "MALL_PRODUCT", String.valueOf(productId),
            "MallProductImageUpdated", Map.of("productId", productId)
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", productId);
        result.put("coverUrl", safeCoverUrl);
        return result;
    }

    @Transactional
    public Map<String, Object> adjustInventory(
        TenantPrincipal principal,
        InventoryCommand command,
        String idempotencyKey
    ) {
        long shopId = accessService.requireShopPermission(principal, command.shopId(), "mall:manage");
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT inventory_id AS inventoryId, available_after AS availableAfter FROM mall_inventory_ledger WHERE tenant_id = ? AND business_key = ?",
            principal.tenantId(), "ADJUST:" + idempotencyKey
        );
        if (!replay.isEmpty()) return replay.getFirst();
        Map<String, Object> inventory = inventory(principal.tenantId(), shopId, command.skuId(), true);
        int available = integer(inventory.get("availableQuantity"));
        int after = available + command.delta();
        if (after < 0) throw new ApiException(HttpStatus.CONFLICT, "库存调整后不能小于零");
        long inventoryId = number(inventory.get("id"));
        requireChanged(jdbcTemplate.update(
            "UPDATE mall_sku_inventory SET available_quantity = ?, version = version + 1 WHERE id = ? AND version = ?",
            after, inventoryId, integer(inventory.get("version"))
        ), "库存已被其他操作更新");
        insertInventoryLedger(
            principal, shopId, inventoryId, command.delta() >= 0 ? "MANUAL_IN" : "ADJUST",
            command.delta(), 0, 0, after, integer(inventory.get("reservedQuantity")),
            integer(inventory.get("soldQuantity")), "SKU", command.skuId(),
            "ADJUST:" + idempotencyKey, command.reason()
        );
        return Map.of("inventoryId", inventoryId, "availableAfter", after);
    }

    @Transactional
    public Map<String, Object> putCart(TenantPrincipal principal, CartCommand command) {
        long memberId = accessService.requireMemberId(principal);
        String mode = upper(command.purchaseMode());
        String delivery = upper(command.deliveryMode());
        if (command.quantity() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "购买数量必须大于零");
        validateSkuMode(principal.tenantId(), command.skuId(), mode);
        jdbcTemplate.update(
            """
            INSERT INTO mall_cart_item (
                tenant_id, member_id, sku_id, purchase_mode, quantity,
                delivery_mode, pickup_shop_id, selected
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 1)
            ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), selected = 1, version = version + 1
            """,
            principal.tenantId(), memberId, command.skuId(), mode, command.quantity(),
            delivery, command.pickupShopId()
        );
        return cart(principal);
    }

    public Map<String, Object> cart(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            """
            SELECT c.id, c.sku_id AS skuId, p.name AS productName, p.product_type AS productType,
                   s.sku_code AS skuCode, s.spec_json AS spec, c.purchase_mode AS purchaseMode,
                   c.quantity, c.delivery_mode AS deliveryMode, c.pickup_shop_id AS pickupShopId,
                   c.selected, i.available_quantity AS availableQuantity
            FROM mall_cart_item c
            JOIN mall_sku s ON s.id = c.sku_id
            JOIN mall_product p ON p.id = s.product_id
            LEFT JOIN mall_sku_inventory i ON i.sku_id = s.id AND i.shop_id = ?
            WHERE c.tenant_id = ? AND c.member_id = ? ORDER BY c.id
            """,
            principal.homeShopId(), principal.tenantId(), memberId
        );
        return Map.of("items", items, "inventoryReserved", false);
    }

    @Transactional
    public Map<String, Object> removeCartItem(TenantPrincipal principal, long itemId) {
        long memberId = accessService.requireMemberId(principal);
        int changed = jdbcTemplate.update(
            "DELETE FROM mall_cart_item WHERE id = ? AND tenant_id = ? AND member_id = ?",
            itemId, principal.tenantId(), memberId
        );
        if (changed == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "购物车商品不存在");
        }
        return cart(principal);
    }

    @Transactional
    public Map<String, Object> updateCartItemQuantity(TenantPrincipal principal, long itemId, int quantity) {
        long memberId = accessService.requireMemberId(principal);
        if (quantity <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "购买数量必须大于零");
        int changed = jdbcTemplate.update(
            "UPDATE mall_cart_item SET quantity = ?, selected = 1, version = version + 1 WHERE id = ? AND tenant_id = ? AND member_id = ?",
            quantity, itemId, principal.tenantId(), memberId
        );
        if (changed == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "购物车商品不存在");
        }
        return cart(principal);
    }

    @Transactional
    public Map<String, Object> checkout(
        TenantPrincipal principal,
        CheckoutCommand command,
        String idempotencyKey,
        String requestHash
    ) {
        long memberId = accessService.requireMemberId(principal);
        long shopId = principal.homeShopId() == null
            ? 0 : principal.homeShopId();
        if (shopId == 0) throw new ApiException(HttpStatus.CONFLICT, "会员账号未绑定默认门店");
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id AS orderId, order_no AS orderNo, status, cash_amount AS cashAmount, points_amount AS pointsAmount, create_request_hash AS requestHash FROM mall_order WHERE tenant_id = ? AND create_idempotency_key = ?",
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) {
            Map<String, Object> result = replay.getFirst();
            if (!requestHash.equals(result.remove("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "商城结算幂等键已用于不同请求");
            }
            return result;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT c.id AS cartId, c.sku_id AS skuId, c.purchase_mode AS purchaseMode,
                   c.quantity, c.delivery_mode AS deliveryMode, c.pickup_shop_id AS pickupShopId,
                   p.id AS productId, p.name AS productName, p.product_type AS productType,
                   p.care_service_id AS careServiceId, p.freight_template_code AS freightTemplate,
                   p.separate_shipping AS separateShipping, s.spec_json AS spec,
                   s.cash_price AS cashPrice, s.points_price AS pointsPrice,
                   s.combo_cash_price AS comboCashPrice, s.combo_points_price AS comboPointsPrice,
                   s.cash_enabled AS cashEnabled, s.points_enabled AS pointsEnabled,
                   s.combo_enabled AS comboEnabled, s.purchase_limit AS purchaseLimit,
                   i.id AS inventoryId, i.available_quantity AS availableQuantity,
                   i.reserved_quantity AS reservedQuantity, i.sold_quantity AS soldQuantity,
                   i.version AS inventoryVersion
            FROM mall_cart_item c
            JOIN mall_sku s ON s.id = c.sku_id AND s.status = 'ACTIVE'
            JOIN mall_product p ON p.id = s.product_id AND p.status = 'ON_SALE'
            JOIN mall_sku_inventory i ON i.sku_id = s.id AND i.shop_id = ?
            WHERE c.tenant_id = ? AND c.member_id = ? AND c.selected = 1
            ORDER BY c.id FOR UPDATE
            """,
            shopId, principal.tenantId(), memberId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择购物车商品");
        BigDecimal totalCash = BigDecimal.ZERO.setScale(2);
        long totalPoints = 0;
        Map<String, List<Map<String, Object>>> split = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String mode = row.get("purchaseMode").toString();
            validateModeRow(row, mode);
            int quantity = integer(row.get("quantity"));
            if (row.get("purchaseLimit") != null && quantity > integer(row.get("purchaseLimit"))) {
                throw new ApiException(HttpStatus.CONFLICT, "商品超过限购数量");
            }
            try {
                MallPolicy.requireInventory(integer(row.get("availableQuantity")), quantity);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
            }
            MallPolicy.Price price = MallPolicy.price(
                mode,
                integer(row.get("cashEnabled")) == 1,
                integer(row.get("pointsEnabled")) == 1,
                integer(row.get("comboEnabled")) == 1,
                nullableDecimal(row.get("cashPrice")), nullableLong(row.get("pointsPrice")),
                nullableDecimal(row.get("comboCashPrice")), nullableLong(row.get("comboPointsPrice")),
                quantity
            );
            BigDecimal lineCash = price.cashAmount();
            long linePoints = price.pointsAmount();
            row.put("lineCash", lineCash);
            row.put("linePoints", linePoints);
            totalCash = totalCash.add(lineCash);
            totalPoints = Math.addExact(totalPoints, linePoints);
            String splitKey = MallPolicy.splitKey(
                mode, row.get("deliveryMode").toString(), nullableLong(row.get("pickupShopId")),
                row.get("freightTemplate") == null ? null : row.get("freightTemplate").toString(),
                Boolean.TRUE.equals(row.get("separateShipping")) || integer(row.get("separateShipping")) == 1,
                number(row.get("skuId"))
            );
            split.computeIfAbsent(splitKey, ignored -> new ArrayList<>()).add(row);
        }
        if (command.mallCouponId() != null) {
            totalCash = lockCoupon(
                principal, memberId, command.mallCouponId(), totalCash, rows,
                idempotencyKey, requestHash
            );
        }
        String paymentMethod = totalCash.signum() == 0 ? "ZERO_AMOUNT" : upper(command.paymentMethod());
        String paymentNo = businessNo("MP");
        PaymentChannelAdapter.PaymentInitiation initiation = null;
        if (totalCash.signum() > 0) {
            initiation = adapterRegistry.requireConfigured(paymentMethod)
                .initiate(paymentNo, totalCash, "CNY");
        }
        long orderId = insertOrder(
            principal, shopId, memberId, totalCash, totalPoints,
            totalCash.signum() == 0 ? "PAID" : "PENDING_PAYMENT",
            command.address(), idempotencyKey, requestHash
        );
        if (command.mallCouponId() != null) {
            requireChanged(jdbcTemplate.update(
                "UPDATE member_mall_coupon SET status = 'LOCKED', locked_order_id = ?, version = version + 1 WHERE id = ? AND tenant_id = ? AND member_id = ? AND status IN ('AVAILABLE', 'RETURNED')",
                orderId, command.mallCouponId(), principal.tenantId(), memberId
            ), "商城优惠券已被其他订单使用");
            insertCouponLedger(
                principal, command.mallCouponId(), orderId, "LOCK", "AVAILABLE", "LOCKED",
                "LOCK:" + orderId, "商城订单锁定"
            );
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : split.entrySet()) {
            long subOrderId = insertSubOrder(
                principal, orderId, entry.getKey(), entry.getValue(), totalCash.signum() == 0
            );
            for (Map<String, Object> row : entry.getValue()) {
                long itemId = insertOrderItem(principal, orderId, subOrderId, row);
                reserveStock(principal, shopId, orderId, itemId, row, idempotencyKey);
            }
        }
        if (totalPoints > 0) {
            pointsService.reserve(
                principal, shopId, memberId, "MALL_ORDER", orderId, totalPoints,
                idempotencyKey + ":points", requestHash
            );
        }
        String paymentStatus = totalCash.signum() == 0 ? "SUCCESS" : "PENDING";
        jdbcTemplate.update(
            """
            INSERT INTO mall_payment (
                tenant_id, mall_order_id, payment_no, payment_method, channel_code,
                cash_amount, points_amount, channel_request_no, channel_status, status,
                idempotency_key, request_hash, paid_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), orderId, paymentNo, paymentMethod,
            totalCash.signum() == 0 ? null : paymentMethod, totalCash, totalPoints,
            initiation == null ? null : initiation.channelRequestNo(),
            initiation == null ? "LOCAL_CONFIRMED" : initiation.channelStatus(), paymentStatus,
            idempotencyKey + ":payment", requestHash,
            totalCash.signum() == 0 ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()) : null
        );
        if (totalCash.signum() == 0) {
            pointsService.consumeReference(principal, shopId, "MALL_ORDER", orderId);
            useCoupon(principal, orderId);
            issueCareEntitlements(principal, shopId, memberId, orderId);
        }
        jdbcTemplate.update(
            "DELETE FROM mall_cart_item WHERE tenant_id = ? AND member_id = ? AND selected = 1",
            principal.tenantId(), memberId
        );
        outboxEventService.append(
            principal, shopId, "MALL_ORDER", String.valueOf(orderId),
            "MallOrderCreated", Map.of("mallOrderId", orderId)
        );
        return detail(principal, orderId);
    }

    public Map<String, Object> detail(TenantPrincipal principal, long orderId) {
        long memberId = accessService.requireMemberId(principal);
        Map<String, Object> order = single(
            jdbcTemplate.queryForList(
                "SELECT id, order_no AS orderNo, cash_amount AS cashAmount, points_amount AS pointsAmount, status, address_snapshot_json AS address, created_at AS createdAt FROM mall_order WHERE id = ? AND tenant_id = ? AND member_id = ?",
                orderId, principal.tenantId(), memberId
            ),
            "商城订单不存在"
        );
        List<Map<String, Object>> subOrders = jdbcTemplate.queryForList(
            """
            SELECT so.id, so.sub_order_no AS subOrderNo, so.purchase_mode AS purchaseMode,
                   so.delivery_mode AS deliveryMode, so.cash_amount AS cashAmount,
                   so.points_amount AS pointsAmount, so.freight_amount AS freightAmount,
                   so.status, ps.package_id AS packageId, p.package_no AS packageNo,
                   p.logistics_company AS logisticsCompany, p.tracking_no AS trackingNo,
                   p.status AS packageStatus
            FROM mall_sub_order so
            LEFT JOIN mall_package_sub_order ps ON ps.sub_order_id = so.id
            LEFT JOIN mall_package p ON p.id = ps.package_id
            WHERE so.mall_order_id = ? ORDER BY so.id
            """,
            orderId
        );
        for (Map<String, Object> subOrder : subOrders) {
            subOrder.put("items", jdbcTemplate.queryForList(
                "SELECT id, product_name_snapshot AS productName, sku_snapshot_json AS sku, purchase_mode AS purchaseMode, quantity, cash_amount AS cashAmount, points_amount AS pointsAmount FROM mall_order_item WHERE sub_order_id = ? ORDER BY id",
                number(subOrder.get("id"))
            ));
        }
        order.put("subOrders", subOrders);
        List<Map<String, Object>> payments = jdbcTemplate.queryForList(
            "SELECT id, payment_no AS paymentNo, payment_method AS paymentMethod, channel_code AS channelCode, cash_amount AS cashAmount, points_amount AS pointsAmount, channel_request_no AS channelRequestNo, channel_status AS channelStatus, external_transaction_no AS externalTransactionNo, status, paid_at AS paidAt FROM mall_payment WHERE tenant_id = ? AND mall_order_id = ? ORDER BY id",
            principal.tenantId(), orderId
        );
        order.put("payment", payments.isEmpty() ? null : payments.getFirst());
        return order;
    }

    public boolean isMallPayment(String channel, String paymentNo) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mall_payment WHERE channel_code = ? AND payment_no = ?",
            Integer.class, channel, paymentNo
        );
        return count != null && count > 0;
    }

    @Transactional
    public Map<String, Object> handleVerifiedPaymentCallback(
        String channel, String eventId, String paymentNo, String status,
        BigDecimal amount, String externalTransactionNo, String channelStatus,
        String failureCode, String payloadHash
    ) {
        Map<String, Object> payment = single(jdbcTemplate.queryForList(
            """
            SELECT mp.id, mp.tenant_id AS tenantId, mo.shop_id AS shopId,
                   mp.mall_order_id AS orderId, mo.member_id AS memberId,
                   mo.created_by AS createdBy, mp.cash_amount AS cashAmount,
                   mp.status, mp.payment_no AS paymentNo
            FROM mall_payment mp JOIN mall_order mo ON mo.id = mp.mall_order_id
            WHERE mp.channel_code = ? AND mp.payment_no = ? FOR UPDATE
            """,
            channel, paymentNo
        ), "商城支付请求不存在");
        long paymentId = number(payment.get("id"));
        List<Map<String, Object>> prior = jdbcTemplate.queryForList(
            "SELECT payload_hash AS payloadHash FROM mall_payment_callback_event WHERE channel_code = ? AND channel_event_id = ?",
            channel, eventId
        );
        if (!prior.isEmpty()) {
            if (!payloadHash.equals(prior.getFirst().get("payloadHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "同一商城支付回调事件载荷不一致");
            }
            return mallPaymentResult(paymentId);
        }
        jdbcTemplate.update(
            "INSERT INTO mall_payment_callback_event (tenant_id, mall_payment_id, channel_code, channel_event_id, payload_hash, processing_status) VALUES (?, ?, ?, ?, ?, 'VERIFIED')",
            number(payment.get("tenantId")), paymentId, channel, eventId, payloadHash
        );
        if (!"PENDING".equals(payment.get("status"))) {
            markMallCallback(channel, eventId, "IGNORED", null);
            return mallPaymentResult(paymentId);
        }
        long tenantId = number(payment.get("tenantId"));
        long shopId = number(payment.get("shopId"));
        long orderId = number(payment.get("orderId"));
        TenantPrincipal principal = new TenantPrincipal(
            number(payment.get("createdBy")), tenantId, shopId, "mall-payment-channel",
            List.of("SYSTEM"), Set.of(), Set.of(shopId), false
        );
        if ("FAILED".equalsIgnoreCase(status)) {
            jdbcTemplate.update(
                "UPDATE mall_payment SET status = 'FAILED', channel_status = ?, channel_event_id = ? WHERE id = ? AND status = 'PENDING'",
                channelStatus, eventId, paymentId
            );
            releaseStock(principal, shopId, orderId, "PAYMENT_FAILED");
            pointsService.releaseReference(principal, shopId, "MALL_ORDER", orderId, "PAYMENT_FAILED");
            releaseCoupon(principal, orderId, "PAYMENT_FAILED");
            jdbcTemplate.update("UPDATE mall_sub_order SET status = 'CANCELLED', version = version + 1 WHERE mall_order_id = ? AND status = 'PENDING_PAYMENT'", orderId);
            jdbcTemplate.update("UPDATE mall_order SET status = 'CANCELLED', version = version + 1 WHERE id = ? AND status = 'PENDING_PAYMENT'", orderId);
            markMallCallback(channel, eventId, "PROCESSED", failureCode);
            return mallPaymentResult(paymentId);
        }
        if (!"SUCCESS".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的商城支付回调状态");
        }
        if (amount.setScale(2).compareTo(decimal(payment.get("cashAmount"))) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "商城支付回调金额不一致");
        }
        requireChanged(jdbcTemplate.update(
            "UPDATE mall_payment SET status = 'SUCCESS', channel_status = ?, external_transaction_no = ?, channel_event_id = ?, paid_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND status = 'PENDING'",
            channelStatus, required(externalTransactionNo, "通道交易号"), eventId, paymentId
        ), "商城支付状态已被其他回调处理");
        jdbcTemplate.update("UPDATE mall_sub_order SET status = 'PAID', version = version + 1 WHERE mall_order_id = ? AND status = 'PENDING_PAYMENT'", orderId);
        requireChanged(jdbcTemplate.update("UPDATE mall_order SET status = 'PAID', paid_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ? AND status = 'PENDING_PAYMENT'", orderId), "商城订单状态已变化");
        pointsService.consumeReference(principal, shopId, "MALL_ORDER", orderId);
        useCoupon(principal, orderId);
        issueCareEntitlements(principal, shopId, number(payment.get("memberId")), orderId);
        outboxEventService.append(principal, shopId, "MALL_PAYMENT", String.valueOf(paymentId), "MallPaymentSucceeded", Map.of("mallPaymentId", paymentId, "mallOrderId", orderId));
        markMallCallback(channel, eventId, "PROCESSED", null);
        return mallPaymentResult(paymentId);
    }

    private Map<String, Object> mallPaymentResult(long paymentId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, mall_order_id AS orderId, payment_no AS paymentNo, payment_method AS paymentMethod, cash_amount AS cashAmount, points_amount AS pointsAmount, channel_status AS channelStatus, external_transaction_no AS externalTransactionNo, status, paid_at AS paidAt FROM mall_payment WHERE id = ?",
            paymentId
        );
    }

    private void markMallCallback(String channel, String eventId, String status, String failureCode) {
        jdbcTemplate.update(
            "UPDATE mall_payment_callback_event SET processing_status = ?, failure_code = ?, processed_at = CURRENT_TIMESTAMP(3) WHERE channel_code = ? AND channel_event_id = ?",
            status, failureCode, channel, eventId
        );
    }

    @Transactional
    public Map<String, Object> cancelPending(TenantPrincipal principal, long orderId) {
        long memberId = accessService.requireMemberId(principal);
        Map<String, Object> order = single(
            jdbcTemplate.queryForList(
                "SELECT shop_id AS shopId, status FROM mall_order WHERE id = ? AND tenant_id = ? AND member_id = ? FOR UPDATE",
                orderId, principal.tenantId(), memberId
            ),
            "商城订单不存在"
        );
        if (!"PENDING_PAYMENT".equals(order.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "仅待支付订单可直接取消；已支付订单售后属于 SC6");
        }
        long shopId = number(order.get("shopId"));
        releaseStock(principal, shopId, orderId, "ORDER_CANCELLED");
        pointsService.releaseReference(principal, shopId, "MALL_ORDER", orderId, "ORDER_CANCELLED");
        releaseCoupon(principal, orderId, "ORDER_CANCELLED");
        jdbcTemplate.update(
            "UPDATE mall_payment SET status = 'CANCELLED' WHERE tenant_id = ? AND mall_order_id = ? AND status = 'PENDING'",
            principal.tenantId(), orderId
        );
        jdbcTemplate.update(
            "UPDATE mall_sub_order SET status = 'CANCELLED', version = version + 1 WHERE mall_order_id = ? AND status = 'PENDING_PAYMENT'",
            orderId
        );
        jdbcTemplate.update(
            "UPDATE mall_order SET status = 'CANCELLED', version = version + 1 WHERE id = ? AND status = 'PENDING_PAYMENT'",
            orderId
        );
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }

    @Transactional
    public Map<String, Object> createPackage(TenantPrincipal principal, PackageCommand command) {
        long shopId = accessService.requireShopPermission(principal, command.shopId(), "mall:manage");
        if (command.subOrderIds() == null || command.subOrderIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择至少一个子订单");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(command.subOrderIds().size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.add(shopId);
        args.addAll(command.subOrderIds());
        List<Map<String, Object>> subOrders = jdbcTemplate.queryForList(
            """
            SELECT so.id, so.delivery_mode AS deliveryMode,
                   COALESCE(so.pickup_shop_id, 0) AS pickupShopId,
                   COALESCE(so.freight_template_code, '') AS freightTemplate
            FROM mall_sub_order so
            JOIN mall_order o ON o.id = so.mall_order_id
            LEFT JOIN mall_package_sub_order ps ON ps.sub_order_id = so.id
            WHERE o.tenant_id = ? AND o.shop_id = ? AND so.id IN (""" + placeholders + ")" +
                " AND so.status = 'PAID' AND ps.sub_order_id IS NULL FOR UPDATE",
            args.toArray()
        );
        if (subOrders.size() != command.subOrderIds().size()) {
            throw new ApiException(HttpStatus.CONFLICT, "子订单不可发货或已关联包裹");
        }
        String compatibility = subOrders.getFirst().get("deliveryMode") + "|"
            + subOrders.getFirst().get("pickupShopId") + "|" + subOrders.getFirst().get("freightTemplate");
        boolean incompatible = subOrders.stream().anyMatch(row -> !compatibility.equals(
            row.get("deliveryMode") + "|" + row.get("pickupShopId") + "|" + row.get("freightTemplate")
        ));
        if (incompatible) throw new ApiException(HttpStatus.CONFLICT, "履约方式或运费模板不兼容，不能合包");
        String deliveryMode = subOrders.getFirst().get("deliveryMode").toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO mall_package (
                    tenant_id, shop_id, package_no, delivery_mode,
                    logistics_company, tracking_no, pickup_shop_id, status,
                    created_by, shipped_at, auto_confirm_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 DAY))
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, businessNo("PKG"));
            statement.setString(4, deliveryMode);
            statement.setString(5, trim(command.logisticsCompany()));
            statement.setString(6, trim(command.trackingNo()));
            Long pickup = nullableLong(subOrders.getFirst().get("pickupShopId"));
            if (pickup == null || pickup == 0) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, pickup);
            statement.setString(8, "PICKUP".equals(deliveryMode) ? "READY_FOR_PICKUP" : "SHIPPED");
            statement.setLong(9, principal.accountId());
            return statement;
        }, keyHolder);
        long packageId = generated(keyHolder, "包裹创建失败");
        for (Map<String, Object> subOrder : subOrders) {
            long subOrderId = number(subOrder.get("id"));
            jdbcTemplate.update(
                "INSERT INTO mall_package_sub_order (package_id, sub_order_id) VALUES (?, ?)",
                packageId, subOrderId
            );
            shipSubOrderStock(principal, shopId, subOrderId);
            jdbcTemplate.update(
                "UPDATE mall_sub_order SET status = 'SHIPPED', version = version + 1 WHERE id = ? AND status = 'PAID'",
                subOrderId
            );
        }
        jdbcTemplate.update(
            """
            UPDATE mall_order o SET status = CASE
              WHEN EXISTS (SELECT 1 FROM mall_sub_order so WHERE so.mall_order_id = o.id AND so.status = 'PAID')
              THEN 'PARTIALLY_SHIPPED' ELSE 'SHIPPED' END,
              version = version + 1
            WHERE id IN (SELECT mall_order_id FROM mall_sub_order WHERE id IN (""" + placeholders + "))",
            command.subOrderIds().toArray()
        );
        outboxEventService.append(
            principal, shopId, "MALL_PACKAGE", String.valueOf(packageId),
            "MallPackageShipped", Map.of("packageId", packageId)
        );
        return Map.of("packageId", packageId, "status", "PICKUP".equals(deliveryMode) ? "READY_FOR_PICKUP" : "SHIPPED");
    }

    @Transactional
    public Map<String, Object> receivePackage(TenantPrincipal principal, long packageId) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> affectedOrders = jdbcTemplate.queryForList(
            """
            SELECT DISTINCT o.id AS orderId, o.shop_id AS shopId
            FROM mall_package p
            JOIN mall_package_sub_order ps ON ps.package_id = p.id
            JOIN mall_sub_order so ON so.id = ps.sub_order_id
            JOIN mall_order o ON o.id = so.mall_order_id
            WHERE p.id = ? AND p.tenant_id = ? AND o.member_id = ?
            """,
            packageId, principal.tenantId(), memberId
        );
        Integer allowed = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM mall_package p
            JOIN mall_package_sub_order ps ON ps.package_id = p.id
            JOIN mall_sub_order so ON so.id = ps.sub_order_id
            JOIN mall_order o ON o.id = so.mall_order_id
            WHERE p.id = ? AND p.tenant_id = ? AND o.member_id = ?
              AND p.status IN ('SHIPPED', 'READY_FOR_PICKUP')
            """,
            Integer.class, packageId, principal.tenantId(), memberId
        );
        if (allowed == null || allowed == 0) throw new ApiException(HttpStatus.CONFLICT, "包裹不可确认收货");
        jdbcTemplate.update(
            "UPDATE mall_package SET status = 'RECEIVED', confirmed_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND tenant_id = ?",
            packageId, principal.tenantId()
        );
        jdbcTemplate.update(
            "UPDATE mall_sub_order SET status = 'COMPLETED', version = version + 1 WHERE id IN (SELECT sub_order_id FROM mall_package_sub_order WHERE package_id = ?)",
            packageId
        );
        for (Map<String, Object> affected : affectedOrders) {
            long orderId = number(affected.get("orderId"));
            long shopId = number(affected.get("shopId"));
            int completed = jdbcTemplate.update(
                "UPDATE mall_order o SET status = 'COMPLETED', version = version + 1 WHERE o.id = ? AND o.status IN ('SHIPPED', 'PARTIALLY_SHIPPED') AND NOT EXISTS (SELECT 1 FROM mall_sub_order so WHERE so.mall_order_id = o.id AND so.status <> 'COMPLETED')",
                orderId
            );
            if (completed == 1) pointsService.earnCompletedMallOrder(principal, shopId, orderId);
        }
        return Map.of("packageId", packageId, "status", "RECEIVED");
    }

    @Transactional
    public Map<String, Object> createCouponRule(TenantPrincipal principal, CouponRuleCommand command) {
        long shopId = accessService.requireShopPermission(principal, command.shopId(), "mall:manage");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO mall_coupon_rule (
                    tenant_id, shop_id, coupon_code, name, coupon_type,
                    fixed_amount, minimum_cash_amount, product_scope_json,
                    valid_days, status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, required(command.couponCode(), "券编码"));
            statement.setString(4, required(command.name(), "券名称"));
            statement.setString(5, upper(command.couponType()));
            statement.setBigDecimal(6, command.fixedAmount());
            statement.setBigDecimal(7, command.minimumCashAmount());
            statement.setString(8, json(command.productIds() == null ? List.of() : command.productIds()));
            statement.setInt(9, command.validDays());
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        return Map.of("couponRuleId", generated(keyHolder, "商城券规则创建失败"), "status", "ACTIVE");
    }

    @Transactional
    public Map<String, Object> issueCoupon(TenantPrincipal principal, CouponIssueCommand command) {
        long shopId = accessService.requireShopPermission(principal, command.shopId(), "mall:manage");
        Map<String, Object> rule = single(
            jdbcTemplate.queryForList(
                "SELECT id, valid_days AS validDays FROM mall_coupon_rule WHERE id = ? AND tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) AND status = 'ACTIVE'",
                command.couponRuleId(), principal.tenantId(), shopId
            ),
            "商城券规则不存在"
        );
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String couponNo = businessNo("MC");
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO member_mall_coupon (
                    tenant_id, member_id, coupon_rule_id, coupon_no,
                    valid_until, issued_by
                ) VALUES (?, ?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? DAY), ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, command.memberId());
            statement.setLong(3, command.couponRuleId());
            statement.setString(4, couponNo);
            statement.setInt(5, integer(rule.get("validDays")));
            statement.setLong(6, principal.accountId());
            return statement;
        }, keyHolder);
        long couponId = generated(keyHolder, "商城券发放失败");
        insertCouponLedger(principal, couponId, null, "ISSUE", null, "AVAILABLE", "ISSUE:" + couponId, "运营发放");
        return Map.of("memberCouponId", couponId, "couponNo", couponNo, "status", "AVAILABLE");
    }

    private long insertOrder(
        TenantPrincipal principal, long shopId, long memberId,
        BigDecimal cash, long points, String status, Map<String, Object> address,
        String idempotencyKey, String requestHash
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO mall_order (tenant_id, shop_id, member_id, order_no, cash_amount, points_amount, status, address_snapshot_json, create_idempotency_key, create_request_hash, created_by, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setString(4, businessNo("MO"));
            statement.setBigDecimal(5, cash);
            statement.setLong(6, points);
            statement.setString(7, status);
            statement.setString(8, address == null ? null : json(address));
            statement.setString(9, idempotencyKey);
            statement.setString(10, requestHash);
            statement.setLong(11, principal.accountId());
            statement.setTimestamp(12, "PAID".equals(status)
                ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()) : null);
            return statement;
        }, keyHolder);
        return generated(keyHolder, "商城订单创建失败");
    }

    private long insertSubOrder(
        TenantPrincipal principal,
        long orderId,
        String splitKey,
        List<Map<String, Object>> rows,
        boolean paid
    ) {
        BigDecimal cash = rows.stream().map(row -> decimal(row.get("lineCash")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long points = rows.stream().mapToLong(row -> number(row.get("linePoints"))).sum();
        Map<String, Object> first = rows.getFirst();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO mall_sub_order (tenant_id, mall_order_id, sub_order_no, split_key, purchase_mode, delivery_mode, pickup_shop_id, freight_template_code, cash_amount, points_amount, freight_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, orderId);
            statement.setString(3, businessNo("MS"));
            statement.setString(4, splitKey);
            statement.setString(5, first.get("purchaseMode").toString());
            statement.setString(6, first.get("deliveryMode").toString());
            Long pickup = nullableLong(first.get("pickupShopId"));
            if (pickup == null) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, pickup);
            statement.setString(8, first.get("freightTemplate") == null ? null : first.get("freightTemplate").toString());
            statement.setBigDecimal(9, cash);
            statement.setLong(10, points);
            statement.setString(11, paid ? "PAID" : "PENDING_PAYMENT");
            return statement;
        }, keyHolder);
        return generated(keyHolder, "商城子订单创建失败");
    }

    private long insertOrderItem(TenantPrincipal principal, long orderId, long subOrderId, Map<String, Object> row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO mall_order_item (tenant_id, mall_order_id, sub_order_id, product_id, sku_id, product_name_snapshot, sku_snapshot_json, purchase_mode, quantity, cash_unit_price, points_unit_price, cash_amount, points_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            int quantity = integer(row.get("quantity"));
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, orderId);
            statement.setLong(3, subOrderId);
            statement.setLong(4, number(row.get("productId")));
            statement.setLong(5, number(row.get("skuId")));
            statement.setString(6, row.get("productName").toString());
            statement.setString(7, row.get("spec").toString());
            statement.setString(8, row.get("purchaseMode").toString());
            statement.setInt(9, quantity);
            statement.setBigDecimal(10, decimal(row.get("lineCash")).divide(BigDecimal.valueOf(quantity)));
            statement.setLong(11, number(row.get("linePoints")) / quantity);
            statement.setBigDecimal(12, decimal(row.get("lineCash")));
            statement.setLong(13, number(row.get("linePoints")));
            return statement;
        }, keyHolder);
        return generated(keyHolder, "商城订单行创建失败");
    }

    private void reserveStock(
        TenantPrincipal principal, long shopId, long orderId, long itemId,
        Map<String, Object> row, String idempotencyKey
    ) {
        int quantity = integer(row.get("quantity"));
        long inventoryId = number(row.get("inventoryId"));
        requireChanged(jdbcTemplate.update(
            "UPDATE mall_sku_inventory SET available_quantity = available_quantity - ?, reserved_quantity = reserved_quantity + ?, version = version + 1 WHERE id = ? AND available_quantity >= ?",
            quantity, quantity, inventoryId, quantity
        ), "共享库存已不足");
        jdbcTemplate.update(
            "INSERT INTO mall_stock_reservation (tenant_id, mall_order_id, order_item_id, inventory_id, quantity, business_key) VALUES (?, ?, ?, ?, ?, ?)",
            principal.tenantId(), orderId, itemId, inventoryId, quantity, "RESERVE:" + itemId
        );
        Map<String, Object> after = inventoryById(inventoryId);
        insertInventoryLedger(
            principal, shopId, inventoryId, "RESERVE", -quantity, quantity, 0,
            integer(after.get("availableQuantity")), integer(after.get("reservedQuantity")), integer(after.get("soldQuantity")),
            "MALL_ORDER_ITEM", itemId, "RESERVE:" + idempotencyKey + ":" + itemId, "商城下单冻结"
        );
    }

    private void releaseStock(TenantPrincipal principal, long shopId, long orderId, String reason) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, inventory_id AS inventoryId, quantity FROM mall_stock_reservation WHERE tenant_id = ? AND mall_order_id = ? AND status = 'RESERVED' FOR UPDATE",
            principal.tenantId(), orderId
        );
        for (Map<String, Object> row : rows) {
            int quantity = integer(row.get("quantity"));
            long inventoryId = number(row.get("inventoryId"));
            jdbcTemplate.update(
                "UPDATE mall_sku_inventory SET available_quantity = available_quantity + ?, reserved_quantity = reserved_quantity - ?, version = version + 1 WHERE id = ? AND reserved_quantity >= ?",
                quantity, quantity, inventoryId, quantity
            );
            jdbcTemplate.update(
                "UPDATE mall_stock_reservation SET status = 'RELEASED', completed_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                number(row.get("id"))
            );
            Map<String, Object> after = inventoryById(inventoryId);
            insertInventoryLedger(
                principal, shopId, inventoryId, "RELEASE", quantity, -quantity, 0,
                integer(after.get("availableQuantity")), integer(after.get("reservedQuantity")), integer(after.get("soldQuantity")),
                "MALL_ORDER", orderId, "RELEASE:" + orderId + ":" + row.get("id"), reason
            );
        }
    }

    private void shipSubOrderStock(TenantPrincipal principal, long shopId, long subOrderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT r.id, r.inventory_id AS inventoryId, r.quantity, r.order_item_id AS orderItemId
            FROM mall_stock_reservation r
            JOIN mall_order_item oi ON oi.id = r.order_item_id
            WHERE oi.sub_order_id = ? AND r.status = 'RESERVED' FOR UPDATE
            """,
            subOrderId
        );
        for (Map<String, Object> row : rows) {
            int quantity = integer(row.get("quantity"));
            long inventoryId = number(row.get("inventoryId"));
            requireChanged(jdbcTemplate.update(
                "UPDATE mall_sku_inventory SET reserved_quantity = reserved_quantity - ?, sold_quantity = sold_quantity + ?, version = version + 1 WHERE id = ? AND reserved_quantity >= ?",
                quantity, quantity, inventoryId, quantity
            ), "发货库存冻结已变化");
            jdbcTemplate.update(
                "UPDATE mall_stock_reservation SET status = 'SHIPPED', completed_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                number(row.get("id"))
            );
            Map<String, Object> after = inventoryById(inventoryId);
            insertInventoryLedger(
                principal, shopId, inventoryId, "SHIP", 0, -quantity, quantity,
                integer(after.get("availableQuantity")), integer(after.get("reservedQuantity")), integer(after.get("soldQuantity")),
                "MALL_SUB_ORDER", subOrderId, "SHIP:" + row.get("orderItemId"), "商城发货"
            );
        }
    }

    private BigDecimal lockCoupon(
        TenantPrincipal principal, long memberId, long couponId, BigDecimal cash,
        List<Map<String, Object>> rows, String idempotencyKey, String requestHash
    ) {
        if (rows.stream().anyMatch(row -> !"CASH".equals(row.get("purchaseMode")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分或积分加现金商品不可使用商城优惠券");
        }
        Map<String, Object> coupon = single(
            jdbcTemplate.queryForList(
                """
                SELECT mc.id, mc.status, mc.version, r.coupon_type AS couponType,
                       r.fixed_amount AS fixedAmount, r.minimum_cash_amount AS minimumCashAmount,
                       r.product_scope_json AS productScope
                FROM member_mall_coupon mc
                JOIN mall_coupon_rule r ON r.id = mc.coupon_rule_id AND r.status = 'ACTIVE'
                WHERE mc.id = ? AND mc.tenant_id = ? AND mc.member_id = ?
                  AND mc.status IN ('AVAILABLE', 'RETURNED')
                  AND mc.valid_from <= CURRENT_TIMESTAMP(3) AND mc.valid_until > CURRENT_TIMESTAMP(3)
                FOR UPDATE
                """,
                couponId, principal.tenantId(), memberId
            ),
            "商城优惠券不可用"
        );
        if ("FREIGHT".equals(coupon.get("couponType"))) {
            throw new ApiException(HttpStatus.CONFLICT, "当前订单无可抵扣运费");
        }
        Set<Long> productScope = longSet(coupon.get("productScope"));
        List<Map<String, Object>> eligibleRows = productScope.isEmpty()
            ? rows
            : rows.stream().filter(row -> productScope.contains(number(row.get("productId")))).toList();
        BigDecimal eligibleCash = eligibleRows.stream()
            .map(row -> decimal(row.get("lineCash")))
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        if (eligibleCash.compareTo(decimal(coupon.get("minimumCashAmount"))) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "订单未达到商城券使用门槛");
        }
        // 订单尚未插入，先只验证；锁定动作在订单创建后补写。
        BigDecimal reduction = eligibleCash.min(decimal(coupon.get("fixedAmount")));
        BigDecimal discounted = cash.subtract(reduction).setScale(2);
        for (Map<String, Object> row : eligibleRows) {
            if (reduction.signum() == 0) break;
            BigDecimal line = decimal(row.get("lineCash"));
            BigDecimal applied = line.min(reduction);
            row.put("lineCash", line.subtract(applied));
            reduction = reduction.subtract(applied);
        }
        return discounted;
    }

    private Set<Long> longSet(Object json) {
        try {
            var values = new java.util.LinkedHashSet<Long>();
            objectMapper.readTree(json.toString()).forEach(value -> values.add(value.asLong()));
            return values;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "商城券商品范围配置不可读");
        }
    }

    private void useCoupon(TenantPrincipal principal, long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, status FROM member_mall_coupon WHERE tenant_id = ? AND locked_order_id = ? FOR UPDATE",
            principal.tenantId(), orderId
        );
        for (Map<String, Object> row : rows) {
            jdbcTemplate.update(
                "UPDATE member_mall_coupon SET status = 'USED', version = version + 1 WHERE id = ? AND status = 'LOCKED'",
                number(row.get("id"))
            );
            insertCouponLedger(principal, number(row.get("id")), orderId, "USE", "LOCKED", "USED", "USE:" + orderId, "订单支付成功");
        }
    }

    private void releaseCoupon(TenantPrincipal principal, long orderId, String reason) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id FROM member_mall_coupon WHERE tenant_id = ? AND locked_order_id = ? AND status = 'LOCKED' FOR UPDATE",
            principal.tenantId(), orderId
        );
        for (Map<String, Object> row : rows) {
            long couponId = number(row.get("id"));
            jdbcTemplate.update(
                "UPDATE member_mall_coupon SET status = 'RETURNED', locked_order_id = NULL, version = version + 1 WHERE id = ? AND status = 'LOCKED'",
                couponId
            );
            insertCouponLedger(principal, couponId, orderId, "RELEASE", "LOCKED", "RETURNED", "RELEASE:" + orderId, reason);
        }
    }

    private void issueCareEntitlements(TenantPrincipal principal, long shopId, long memberId, long orderId) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            """
            SELECT oi.id, p.care_service_id AS careServiceId
            FROM mall_order_item oi JOIN mall_product p ON p.id = oi.product_id
            WHERE oi.mall_order_id = ? AND p.product_type = 'CARE_ENTITLEMENT'
            """,
            orderId
        );
        for (Map<String, Object> item : items) {
            jdbcTemplate.update(
                "INSERT IGNORE INTO care_redemption_entitlement (tenant_id, shop_id, member_id, service_id, mall_order_item_id, entitlement_no, valid_from, valid_until) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY))",
                principal.tenantId(), shopId, memberId, number(item.get("careServiceId")),
                number(item.get("id")), businessNo("CARE")
            );
        }
    }

    private Map<String, Object> inventory(long tenantId, long shopId, long skuId, boolean lock) {
        return single(jdbcTemplate.queryForList(
            "SELECT id, available_quantity AS availableQuantity, reserved_quantity AS reservedQuantity, sold_quantity AS soldQuantity, version FROM mall_sku_inventory WHERE tenant_id = ? AND shop_id = ? AND sku_id = ?" + (lock ? " FOR UPDATE" : ""),
            tenantId, shopId, skuId
        ), "SKU 库存不存在");
    }

    private Map<String, Object> inventoryById(long inventoryId) {
        return jdbcTemplate.queryForMap(
            "SELECT available_quantity AS availableQuantity, reserved_quantity AS reservedQuantity, sold_quantity AS soldQuantity FROM mall_sku_inventory WHERE id = ?",
            inventoryId
        );
    }

    private void validateSkuMode(long tenantId, long skuId, String mode) {
        Map<String, Object> row = single(jdbcTemplate.queryForList(
            "SELECT cash_enabled AS cashEnabled, points_enabled AS pointsEnabled, combo_enabled AS comboEnabled FROM mall_sku WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            skuId, tenantId
        ), "SKU 不存在");
        validateModeRow(row, mode);
    }

    private void validateModeRow(Map<String, Object> row, String mode) {
        boolean enabled = switch (mode) {
            case "CASH" -> integer(row.get("cashEnabled")) == 1;
            case "POINTS" -> integer(row.get("pointsEnabled")) == 1;
            case "COMBINATION" -> integer(row.get("comboEnabled")) == 1;
            default -> false;
        };
        if (!enabled) throw new ApiException(HttpStatus.CONFLICT, "SKU 未开放所选购买模式");
    }

    private void insertInventoryLedger(
        TenantPrincipal principal, long shopId, long inventoryId, String movement,
        int availableDelta, int reservedDelta, int soldDelta,
        int availableAfter, int reservedAfter, int soldAfter,
        String referenceType, Long referenceId, String businessKey, String reason
    ) {
        jdbcTemplate.update(
            "INSERT INTO mall_inventory_ledger (tenant_id, shop_id, inventory_id, movement_type, available_delta, reserved_delta, sold_delta, available_after, reserved_after, sold_after, reference_type, reference_id, audit_no, business_key, reason, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            principal.tenantId(), shopId, inventoryId, movement, availableDelta,
            reservedDelta, soldDelta, availableAfter, reservedAfter, soldAfter,
            referenceType, referenceId, businessNo("INV"), businessKey,
            required(reason, "库存调整原因"), principal.accountId()
        );
    }

    private void insertCouponLedger(
        TenantPrincipal principal, long couponId, Long orderId, String action,
        String from, String to, String key, String reason
    ) {
        jdbcTemplate.update(
            "INSERT INTO mall_coupon_ledger (tenant_id, member_coupon_id, mall_order_id, action_type, from_status, to_status, business_key, reason, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            principal.tenantId(), couponId, orderId, action, from, to, key, reason, principal.accountId()
        );
    }

    private Map<String, Object> single(List<Map<String, Object>> rows, String message) {
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, message);
        return rows.getFirst();
    }

    private long generated(KeyHolder keyHolder, String message) {
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        return key.longValue();
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "商城快照生成失败");
        }
    }

    private String upper(String value) {
        return required(value, "类型").toUpperCase(Locale.ROOT);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能为空");
        return value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String businessNo(String prefix) {
        return prefix + LocalDate.now().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private int integer(Object value) {
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private BigDecimal nullableDecimal(Object value) {
        return value == null ? null : decimal(value);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : number(value);
    }

    public static String requestHash(CheckoutCommand command) {
        return SessionTokenCodec.sha256(command.toString());
    }

    public record ProductCommand(
        long shopId, String categoryCode, String categoryName,
        String productCode, String productType, String name, String brandName,
        String description, String coverUrl, Long careServiceId, String deliveryMode,
        String freightTemplateCode, boolean separateShipping, String afterSalePolicy,
        String skuCode, Map<String, Object> spec, BigDecimal cashPrice, Long pointsPrice,
        BigDecimal comboCashPrice, Long comboPointsPrice, boolean cashEnabled,
        boolean pointsEnabled, boolean comboEnabled, Integer purchaseLimit,
        int warningThreshold
    ) {
    }

    public record InventoryCommand(long shopId, long skuId, int delta, String reason) {
    }

    public record CartCommand(
        long skuId, String purchaseMode, int quantity,
        String deliveryMode, Long pickupShopId
    ) {
    }

    public record CheckoutCommand(
        String paymentMethod, Long mallCouponId, Map<String, Object> address
    ) {
    }

    public record PackageCommand(
        long shopId, List<Long> subOrderIds, String logisticsCompany, String trackingNo
    ) {
    }

    public record CouponRuleCommand(
        long shopId, String couponCode, String name, String couponType,
        BigDecimal fixedAmount, BigDecimal minimumCashAmount,
        List<Long> productIds, int validDays
    ) {
    }

    public record CouponIssueCommand(
        long shopId, long couponRuleId, long memberId
    ) {
    }
}
