package com.face.platform.benefit;

import com.face.platform.api.ApiException;
import com.face.platform.checkout.DiscountSelectionPolicy;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BenefitApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;

    public BenefitApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
    }

    public Map<String, Object> myBenefits(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> cards = jdbcTemplate.queryForList(
            """
            SELECT pi.id, pi.instance_no AS instanceNo, pi.card_type AS cardType,
                   pp.name, pi.source_type AS sourceType, pi.valid_from AS validFrom,
                   pi.valid_until AS validUntil, pi.total_quantity AS totalQuantity,
                   pi.remaining_quantity AS remainingQuantity,
                   pi.frozen_quantity AS frozenQuantity, pi.usage_count AS usageCount,
                   pi.status, svb.principal_remaining AS principalRemaining,
                   svb.gift_remaining AS giftRemaining,
                   svb.principal_frozen AS principalFrozen,
                   svb.gift_frozen AS giftFrozen,
                   pp.discount_percent AS discountPercent,
                   pp.minimum_spend AS minimumSpend,
                   pp.maximum_savings AS maximumSavings
            FROM package_instance pi
            JOIN package_product pp ON pp.id = pi.package_product_id
            LEFT JOIN stored_value_batch svb ON svb.package_instance_id = pi.id
            WHERE pi.tenant_id = ? AND pi.member_id = ?
            ORDER BY FIELD(pi.status, 'ACTIVE', 'FROZEN', 'EXHAUSTED', 'EXPIRED', 'CANCELLED'),
                     pi.valid_until DESC, pi.id DESC
            """,
            principal.tenantId(), memberId
        );
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
            """
            SELECT mc.id, mc.coupon_no AS couponNo, ct.name,
                   ct.coupon_type AS couponType, ct.threshold_amount AS thresholdAmount,
                   ct.benefit_value AS benefitValue, ct.service_id AS serviceId,
                   mc.source_type AS sourceType, mc.valid_from AS validFrom,
                   mc.valid_until AS validUntil, mc.status
            FROM member_coupon mc
            JOIN coupon_template ct ON ct.id = mc.template_id
            WHERE mc.tenant_id = ? AND mc.member_id = ?
            ORDER BY FIELD(mc.status, 'AVAILABLE', 'LOCKED', 'USED', 'RETURNED', 'EXPIRED', 'CANCELLED'),
                     mc.valid_until DESC, mc.id DESC
            """,
            principal.tenantId(), memberId
        );
        return Map.of("cards", cards, "coupons", coupons);
    }

    public Map<String, Object> administration(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "benefit:view");
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            """
            SELECT id, package_code AS packageCode, name, card_type AS cardType,
                   description, sale_price AS salePrice,
                   principal_amount AS principalAmount, gift_amount AS giftAmount,
                   discount_percent AS discountPercent, minimum_spend AS minimumSpend,
                   maximum_savings AS maximumSavings, usage_limit AS usageLimit,
                   validity_days AS validityDays, status, version
            FROM package_product
            WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)
            ORDER BY card_type, name, id
            """,
            principal.tenantId(), shopId
        );
        List<Map<String, Object>> templates = jdbcTemplate.queryForList(
            """
            SELECT id, template_code AS templateCode, name, coupon_type AS couponType,
                   threshold_amount AS thresholdAmount, benefit_value AS benefitValue,
                   service_id AS serviceId, validity_days AS validityDays,
                   return_on_full_refund AS returnOnFullRefund, status, version
            FROM coupon_template
            WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)
            ORDER BY coupon_type, name, id
            """,
            principal.tenantId(), shopId
        );
        return Map.of("cardProducts", products, "couponTemplates", templates);
    }

    @Transactional
    public Map<String, Object> createCardProduct(
        TenantPrincipal principal,
        CardProductCommand command
    ) {
        long shopId = accessService.requireShopPermission(
            principal, command.shopId(), "benefit:manage"
        );
        String cardType = cardType(command.cardType());
        BigDecimal salePrice = money(command.salePrice(), true);
        BigDecimal principalAmount = money(command.principalAmount(), true);
        BigDecimal giftAmount = money(command.giftAmount(), true);
        if ("STORED_VALUE".equals(cardType) && principalAmount.add(giftAmount).signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "储值卡必须配置本金或赠送金");
        }
        if ("DISCOUNT".equals(cardType)
            && (command.discountPercent() == null || command.discountPercent().signum() <= 0
                || command.discountPercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "折扣卡折扣比例必须在 0 到 100 之间");
        }
        String code = required(command.packageCode(), "卡产品编码").toUpperCase(Locale.ROOT);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO package_product (
                    tenant_id, shop_id, package_code, card_type, name, description,
                    sale_price, principal_amount, gift_amount, discount_percent,
                    minimum_spend, maximum_savings, usage_limit, scope_json,
                    validity_days, status, version, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, 'ACTIVE', 1, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, code);
            statement.setString(4, cardType);
            statement.setString(5, required(command.name(), "卡产品名称"));
            statement.setString(6, trim(command.description()));
            statement.setBigDecimal(7, salePrice);
            statement.setBigDecimal(8, principalAmount);
            statement.setBigDecimal(9, giftAmount);
            statement.setBigDecimal(10, command.discountPercent());
            statement.setBigDecimal(11, money(command.minimumSpend(), true));
            statement.setBigDecimal(12, command.maximumSavings() == null
                ? null : money(command.maximumSavings(), true));
            if (command.usageLimit() == null) statement.setNull(13, java.sql.Types.INTEGER);
            else statement.setInt(13, command.usageLimit());
            statement.setString(14, command.scopeJson() == null ? "{}" : command.scopeJson());
            statement.setInt(15, command.validityDays());
            statement.setLong(16, principal.accountId());
            statement.setLong(17, principal.accountId());
            return statement;
        }, keyHolder);
        long productId = generated(keyHolder, "卡产品创建失败");
        if ("COMBO_TIMES".equals(cardType)) {
            if (command.items() == null || command.items().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "次卡必须配置至少一个护理项目");
            }
            for (CardProductItem item : command.items()) {
                Map<String, Object> service = requireService(
                    principal.tenantId(), shopId, item.serviceId()
                );
                jdbcTemplate.update(
                    """
                    INSERT INTO package_product_item (
                        tenant_id, package_product_id, service_id,
                        service_name_snapshot, quantity_total, sort_order
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    principal.tenantId(), productId, item.serviceId(), service.get("name"),
                    quantity(item.quantity()), command.items().indexOf(item)
                );
            }
        }
        audit(principal, shopId, "CARD_PRODUCT_CREATE", "PACKAGE_PRODUCT", productId, cardType);
        return cardProduct(principal.tenantId(), productId);
    }

    @Transactional
    public Map<String, Object> issueManualCard(
        TenantPrincipal principal,
        CardIssueCommand command,
        String idempotencyKey,
        String requestHash
    ) {
        long shopId = accessService.requireShopPermission(
            principal, command.shopId(), "benefit:manage"
        );
        String source = source(command.sourceType());
        if ("ONLINE_PURCHASE".equals(source) || "LEGACY_IMPORT".equals(source)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该来源不能通过人工发卡入口使用");
        }
        return issueCard(
            principal, shopId, command.memberId(), command.packageProductId(),
            source, null, command.sourceReference(), idempotencyKey, requestHash
        );
    }

    @Transactional
    public int issuePaidOrderCards(TenantPrincipal principal, long shopId, long orderId) {
        List<Map<String, Object>> order = jdbcTemplate.queryForList(
            "SELECT member_id AS memberId, status FROM sales_order WHERE id = ? AND tenant_id = ? AND shop_id = ? FOR UPDATE",
            orderId, principal.tenantId(), shopId
        );
        if (order.isEmpty() || !"PAID".equals(order.getFirst().get("status"))) return 0;
        long memberId = number(order.getFirst().get("memberId"));
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
            """
            SELECT package_product_id AS productId
            FROM sales_order_item
            WHERE order_id = ? AND item_type = 'PACKAGE'
            ORDER BY id
            """,
            orderId
        );
        int issued = 0;
        for (Map<String, Object> line : lines) {
            long productId = number(line.get("productId"));
            String key = "online-card:" + orderId + ":" + productId;
            issueCard(
                principal, shopId, memberId, productId, "ONLINE_PURCHASE", orderId,
                "ORDER:" + orderId, key, hash(key)
            );
            issued++;
        }
        return issued;
    }

    @Transactional
    public Map<String, Object> createCouponTemplate(
        TenantPrincipal principal,
        CouponTemplateCommand command
    ) {
        long shopId = accessService.requireShopPermission(
            principal, command.shopId(), "benefit:manage"
        );
        String type = couponType(command.couponType());
        if ("SERVICE_EXPERIENCE".equals(type) && command.serviceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "项目体验券必须指定护理项目");
        }
        if (command.serviceId() != null) {
            requireService(principal.tenantId(), shopId, command.serviceId());
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO coupon_template (
                    tenant_id, shop_id, template_code, name, coupon_type,
                    threshold_amount, benefit_value, service_id, validity_days,
                    return_on_full_refund, status, version, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, required(command.templateCode(), "优惠券编码").toUpperCase(Locale.ROOT));
            statement.setString(4, required(command.name(), "优惠券名称"));
            statement.setString(5, type);
            statement.setBigDecimal(6, money(command.thresholdAmount(), true));
            statement.setBigDecimal(7, money(command.benefitValue(), true));
            if (command.serviceId() == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, command.serviceId());
            statement.setInt(9, command.validityDays());
            statement.setBoolean(10, command.returnOnFullRefund());
            statement.setLong(11, principal.accountId());
            statement.setLong(12, principal.accountId());
            return statement;
        }, keyHolder);
        long id = generated(keyHolder, "优惠券模板创建失败");
        audit(principal, shopId, "COUPON_TEMPLATE_CREATE", "COUPON_TEMPLATE", id, type);
        return jdbcTemplate.queryForMap(
            "SELECT id, template_code AS templateCode, name, coupon_type AS couponType, status, version FROM coupon_template WHERE id = ?",
            id
        );
    }

    @Transactional
    public Map<String, Object> issueCoupon(
        TenantPrincipal principal,
        CouponIssueCommand command,
        String idempotencyKey,
        String requestHash
    ) {
        long shopId = accessService.requireShopPermission(
            principal, command.shopId(), "benefit:manage"
        );
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT mc.id, mc.coupon_no AS couponNo, mc.status, cl.request_hash AS requestHash
            FROM coupon_ledger cl JOIN member_coupon mc ON mc.id = cl.member_coupon_id
            WHERE cl.tenant_id = ? AND cl.idempotency_key = ?
            """,
            principal.tenantId(), required(idempotencyKey, "幂等键")
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().remove("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "发券幂等键已用于不同请求");
            }
            return replay.getFirst();
        }
        Map<String, Object> template = requireCouponTemplate(
            principal.tenantId(), shopId, command.templateId()
        );
        requireMember(principal.tenantId(), shopId, command.memberId());
        LocalDate today = LocalDate.now();
        LocalDate validUntil = today.plusDays(number(template.get("validityDays")) - 1);
        String couponNo = businessNo("CPN");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO member_coupon (
                    tenant_id, shop_id, member_id, template_id, coupon_no,
                    source_type, source_reference, valid_from, valid_until,
                    status, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', 1, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, command.memberId());
            statement.setLong(4, command.templateId());
            statement.setString(5, couponNo);
            statement.setString(6, couponSource(command.sourceType()));
            statement.setString(7, trim(command.sourceReference()));
            statement.setObject(8, today.atStartOfDay());
            statement.setObject(9, validUntil.atTime(23, 59, 59));
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        long couponId = generated(keyHolder, "优惠券发放失败");
        insertCouponLedger(
            principal, shopId, couponId, null, "ISSUE", null, "AVAILABLE",
            "ISSUE:" + couponId, idempotencyKey, requestHash, "发放优惠券"
        );
        audit(principal, shopId, "COUPON_ISSUE", "MEMBER_COUPON", couponId, couponNo);
        return Map.of("id", couponId, "couponNo", couponNo, "status", "AVAILABLE");
    }

    public List<Map<String, Object>> discountCandidates(
        long tenantId,
        long shopId,
        long memberId,
        long serviceId,
        BigDecimal subtotal
    ) {
        BigDecimal safeSubtotal = money(subtotal, false);
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(option("NONE", null, "不使用优惠", BigDecimal.ZERO, safeSubtotal));

        List<Map<String, Object>> activities = jdbcTemplate.queryForList(
            """
            SELECT id, name, reduction_type AS reductionType,
                   threshold_amount AS thresholdAmount, benefit_value AS benefitValue
            FROM promotion_activity
            WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)
              AND status = 'ACTIVE' AND starts_at <= CURRENT_TIMESTAMP(3)
              AND ends_at > CURRENT_TIMESTAMP(3) AND threshold_amount <= ?
            ORDER BY id
            """,
            tenantId, shopId, safeSubtotal
        );
        for (Map<String, Object> activity : activities) {
            BigDecimal discount = "PERCENTAGE".equals(activity.get("reductionType"))
                ? DiscountSelectionPolicy.percentageReduction(
                    safeSubtotal, decimal(activity.get("benefitValue")), null
                )
                : DiscountSelectionPolicy.fixedReduction(
                    safeSubtotal, decimal(activity.get("benefitValue"))
                );
            result.add(option(
                "ACTIVITY", number(activity.get("id")), activity.get("name").toString(),
                discount, DiscountSelectionPolicy.payable(safeSubtotal, discount)
            ));
        }

        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
            """
            SELECT mc.id, ct.name, ct.coupon_type AS couponType,
                   ct.threshold_amount AS thresholdAmount, ct.benefit_value AS benefitValue,
                   ct.service_id AS serviceId
            FROM member_coupon mc JOIN coupon_template ct ON ct.id = mc.template_id
            WHERE mc.tenant_id = ? AND mc.shop_id = ? AND mc.member_id = ?
              AND mc.status IN ('AVAILABLE', 'RETURNED')
              AND mc.valid_from <= CURRENT_TIMESTAMP(3) AND mc.valid_until > CURRENT_TIMESTAMP(3)
              AND ct.status = 'ACTIVE' AND ct.threshold_amount <= ?
              AND (ct.service_id IS NULL OR ct.service_id = ?)
            ORDER BY mc.valid_until, mc.id
            """,
            tenantId, shopId, memberId, safeSubtotal, serviceId
        );
        for (Map<String, Object> coupon : coupons) {
            BigDecimal discount = couponDiscount(safeSubtotal, coupon);
            result.add(option(
                "COUPON", number(coupon.get("id")), coupon.get("name").toString(),
                discount, DiscountSelectionPolicy.payable(safeSubtotal, discount)
            ));
        }

        List<Map<String, Object>> discountCards = jdbcTemplate.queryForList(
            """
            SELECT pi.id, pp.name, pp.discount_percent AS discountPercent,
                   pp.minimum_spend AS minimumSpend, pp.maximum_savings AS maximumSavings,
                   pp.usage_limit AS usageLimit, pi.usage_count AS usageCount
            FROM package_instance pi JOIN package_product pp ON pp.id = pi.package_product_id
            WHERE pi.tenant_id = ? AND pi.shop_id = ? AND pi.member_id = ?
              AND pi.card_type = 'DISCOUNT' AND pi.status = 'ACTIVE'
              AND pi.valid_from <= CURRENT_DATE AND pi.valid_until >= CURRENT_DATE
              AND pp.status = 'ACTIVE' AND pp.minimum_spend <= ?
              AND (pp.usage_limit IS NULL OR pi.usage_count < pp.usage_limit)
            ORDER BY pi.valid_until, pi.id
            """,
            tenantId, shopId, memberId, safeSubtotal
        );
        for (Map<String, Object> card : discountCards) {
            BigDecimal discount = DiscountSelectionPolicy.percentageReduction(
                safeSubtotal,
                decimal(card.get("discountPercent")),
                card.get("maximumSavings") == null ? null : decimal(card.get("maximumSavings"))
            );
            result.add(option(
                "DISCOUNT_CARD", number(card.get("id")), card.get("name").toString(),
                discount, DiscountSelectionPolicy.payable(safeSubtotal, discount)
            ));
        }
        BigDecimal best = result.stream()
            .map(item -> decimal(item.get("discountAmount")))
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        result.forEach(item -> item.put(
            "recommended", best.signum() > 0 && decimal(item.get("discountAmount")).compareTo(best) == 0
        ));
        return result;
    }

    public List<Map<String, Object>> comboCandidates(
        long tenantId,
        long shopId,
        long memberId,
        long serviceId
    ) {
        return jdbcTemplate.queryForList(
            """
            SELECT pi.id, pp.name, pi.instance_no AS instanceNo,
                   pii.remaining_quantity AS remainingQuantity,
                   pii.frozen_quantity AS frozenQuantity,
                   pii.remaining_quantity - pii.frozen_quantity AS availableQuantity,
                   pi.valid_until AS validUntil
            FROM package_instance pi
            JOIN package_product pp ON pp.id = pi.package_product_id
            JOIN package_instance_item pii ON pii.package_instance_id = pi.id
            WHERE pi.tenant_id = ? AND pi.shop_id = ? AND pi.member_id = ?
              AND pi.card_type = 'COMBO_TIMES' AND pi.status = 'ACTIVE'
              AND pi.valid_from <= CURRENT_DATE AND pi.valid_until >= CURRENT_DATE
              AND pii.service_id = ?
              AND pii.remaining_quantity - pii.frozen_quantity >= 1
            ORDER BY pi.valid_until, pi.id
            """,
            tenantId, shopId, memberId, serviceId
        );
    }

    public Map<String, Object> requireSelectedCandidate(
        List<Map<String, Object>> candidates,
        String selectionType,
        Long referenceId
    ) {
        String selected;
        try {
            selected = DiscountSelectionPolicy.select(List.of(
                selectionType == null ? "NONE" : selectionType
            ));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return candidates.stream()
            .filter(item -> selected.equals(item.get("selectionType")))
            .filter(item -> "NONE".equals(selected)
                || (referenceId != null && referenceId.equals(number(item.get("referenceId")))))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "所选优惠已不可用，请重新报价"));
    }

    @Transactional
    public Long lockPricingBenefit(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long orderId,
        String selectionType,
        Long referenceId,
        String idempotencyKey,
        String requestHash
    ) {
        if ("COUPON".equals(selectionType)) {
            Map<String, Object> coupon = lockCoupon(
                principal.tenantId(), shopId, memberId, referenceId
            );
            int changed = jdbcTemplate.update(
                """
                UPDATE member_coupon SET status = 'LOCKED', locked_order_id = ?, version = version + 1
                WHERE id = ? AND version = ? AND status IN ('AVAILABLE', 'RETURNED')
                """,
                orderId, referenceId, number(coupon.get("version"))
            );
            requireChanged(changed, "优惠券已被其他订单使用");
            insertCouponLedger(
                principal, shopId, referenceId, orderId, "LOCK",
                coupon.get("status").toString(), "LOCKED", "LOCK:" + orderId,
                idempotencyKey + ":coupon", requestHash, "订单优惠锁定"
            );
            return referenceId;
        }
        if ("DISCOUNT_CARD".equals(selectionType)) {
            Map<String, Object> card = lockDiscountCard(
                principal.tenantId(), shopId, memberId, referenceId
            );
            jdbcTemplate.update(
                """
                UPDATE package_instance SET usage_count = usage_count + 1, version = version + 1
                WHERE id = ? AND version = ? AND status = 'ACTIVE'
                """,
                referenceId, number(card.get("version"))
            );
            return referenceId;
        }
        return null;
    }

    @Transactional
    public long reserveStoredValue(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long orderId,
        long packageInstanceId,
        BigDecimal amount,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id FROM benefit_reservation WHERE tenant_id = ? AND idempotency_key = ?",
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("id"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT svb.id AS batchId, svb.principal_remaining AS principalRemaining,
                   svb.gift_remaining AS giftRemaining, svb.principal_frozen AS principalFrozen,
                   svb.gift_frozen AS giftFrozen
            FROM package_instance pi JOIN stored_value_batch svb ON svb.package_instance_id = pi.id
            WHERE pi.id = ? AND pi.tenant_id = ? AND pi.shop_id = ? AND pi.member_id = ?
              AND pi.card_type = 'STORED_VALUE' AND pi.status = 'ACTIVE'
              AND svb.status = 'ACTIVE' AND svb.valid_until >= CURRENT_DATE
            FOR UPDATE
            """,
            packageInstanceId, principal.tenantId(), shopId, memberId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "所选储值卡当前不可用");
        Map<String, Object> batch = rows.getFirst();
        BigDecimal principalAvailable = decimal(batch.get("principalRemaining"))
            .subtract(decimal(batch.get("principalFrozen")));
        BigDecimal giftAvailable = decimal(batch.get("giftRemaining"))
            .subtract(decimal(batch.get("giftFrozen")));
        BenefitReservationPolicy.Allocation allocation;
        try {
            allocation = BenefitReservationPolicy.allocateStoredValue(
                principalAvailable, giftAvailable, money(amount, false)
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
        long batchId = number(batch.get("batchId"));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO benefit_reservation (
                    tenant_id, shop_id, member_id, order_id, benefit_type,
                    package_instance_id, stored_value_batch_id,
                    principal_amount, gift_amount, status, business_key,
                    idempotency_key, request_hash, version, created_by
                ) VALUES (?, ?, ?, ?, 'STORED_VALUE', ?, ?, ?, ?, 'RESERVED', ?, ?, ?, 1, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setLong(4, orderId);
            statement.setLong(5, packageInstanceId);
            statement.setLong(6, batchId);
            statement.setBigDecimal(7, allocation.principal());
            statement.setBigDecimal(8, allocation.gift());
            statement.setString(9, "STORED_VALUE:" + orderId);
            statement.setString(10, idempotencyKey);
            statement.setString(11, requestHash);
            statement.setLong(12, principal.accountId());
            return statement;
        }, keyHolder);
        long reservationId = generated(keyHolder, "储值权益冻结失败");
        jdbcTemplate.update(
            """
            UPDATE stored_value_batch
            SET principal_frozen = principal_frozen + ?, gift_frozen = gift_frozen + ?
            WHERE id = ?
            """,
            allocation.principal(), allocation.gift(), batchId
        );
        insertStoredLedger(
            principal, shopId, batchId, orderId, reservationId, "RESERVE",
            allocation.principal().negate(), allocation.gift().negate(),
            principalAvailable.subtract(allocation.principal()),
            giftAvailable.subtract(allocation.gift()),
            "RESERVE:" + orderId, idempotencyKey + ":ledger", requestHash, "预约储值冻结"
        );
        return reservationId;
    }

    @Transactional
    public long reserveComboTime(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long orderId,
        long serviceId,
        long packageInstanceId,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id FROM benefit_reservation WHERE tenant_id = ? AND idempotency_key = ?",
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("id"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pi.id AS instanceId, pii.id AS itemId,
                   pii.remaining_quantity AS remainingQuantity,
                   pii.frozen_quantity AS frozenQuantity
            FROM package_instance pi
            JOIN package_instance_item pii ON pii.package_instance_id = pi.id
            WHERE pi.id = ? AND pi.tenant_id = ? AND pi.shop_id = ? AND pi.member_id = ?
              AND pi.card_type = 'COMBO_TIMES' AND pi.status = 'ACTIVE'
              AND pi.valid_from <= CURRENT_DATE AND pi.valid_until >= CURRENT_DATE
              AND pii.service_id = ?
            FOR UPDATE
            """,
            packageInstanceId, principal.tenantId(), shopId, memberId, serviceId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "所选次卡当前不可用于该护理项目");
        Map<String, Object> row = rows.getFirst();
        BigDecimal available = decimal(row.get("remainingQuantity"))
            .subtract(decimal(row.get("frozenQuantity")));
        if (available.compareTo(BigDecimal.ONE) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "所选次卡可用次数不足");
        }
        long itemId = number(row.get("itemId"));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO benefit_reservation (
                    tenant_id, shop_id, member_id, order_id, benefit_type,
                    package_instance_id, package_instance_item_id, quantity,
                    status, business_key, idempotency_key, request_hash, version, created_by
                ) VALUES (?, ?, ?, ?, 'COMBO_TIMES', ?, ?, 1, 'RESERVED', ?, ?, ?, 1, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setLong(4, orderId);
            statement.setLong(5, packageInstanceId);
            statement.setLong(6, itemId);
            statement.setString(7, "COMBO_TIMES:" + orderId);
            statement.setString(8, idempotencyKey);
            statement.setString(9, requestHash);
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        long reservationId = generated(keyHolder, "次卡权益冻结失败");
        requireChanged(jdbcTemplate.update(
            "UPDATE package_instance_item SET frozen_quantity = frozen_quantity + 1 WHERE id = ? AND remaining_quantity - frozen_quantity >= 1",
            itemId
        ), "次卡可用次数已变化");
        requireChanged(jdbcTemplate.update(
            "UPDATE package_instance SET frozen_quantity = frozen_quantity + 1, version = version + 1 WHERE id = ? AND status = 'ACTIVE'",
            packageInstanceId
        ), "次卡状态已变化");
        outboxEventService.append(
            principal, shopId, "PACKAGE_INSTANCE", String.valueOf(packageInstanceId),
            "ComboTimeReserved", Map.of("orderId", orderId, "reservationId", reservationId)
        );
        return reservationId;
    }

    @Transactional
    public int completeOrderBenefits(TenantPrincipal principal, long shopId, long orderId) {
        int consumed = 0;
        List<Map<String, Object>> couponRows = jdbcTemplate.queryForList(
            "SELECT id, version FROM member_coupon WHERE tenant_id = ? AND shop_id = ? AND locked_order_id = ? AND status = 'LOCKED' FOR UPDATE",
            principal.tenantId(), shopId, orderId
        );
        for (Map<String, Object> coupon : couponRows) {
            long couponId = number(coupon.get("id"));
            int changed = jdbcTemplate.update(
                "UPDATE member_coupon SET status = 'USED', used_order_id = ?, version = version + 1 WHERE id = ? AND version = ? AND status = 'LOCKED'",
                orderId, couponId, number(coupon.get("version"))
            );
            requireChanged(changed, "优惠券状态已变化");
            insertCouponLedger(
                principal, shopId, couponId, orderId, "USE", "LOCKED", "USED",
                "USE:" + orderId, "fulfill-coupon:" + orderId,
                hash("fulfill-coupon:" + orderId), "支付成功使用优惠券"
            );
            consumed++;
        }
        return consumed;
    }

    @Transactional
    public int consumeReservedBenefits(TenantPrincipal principal, long shopId, long orderId) {
        accessService.requireShopPermission(principal, shopId, "benefit:manage");
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList(
            """
            SELECT id, benefit_type AS benefitType, package_instance_id AS packageInstanceId,
                   package_instance_item_id AS packageItemId, stored_value_batch_id AS batchId,
                   quantity, principal_amount AS principalAmount, gift_amount AS giftAmount, version
            FROM benefit_reservation
            WHERE tenant_id = ? AND shop_id = ? AND order_id = ? AND status = 'RESERVED'
            ORDER BY id FOR UPDATE
            """,
            principal.tenantId(), shopId, orderId
        );
        for (Map<String, Object> reservation : reservations) {
            if ("STORED_VALUE".equals(reservation.get("benefitType"))) {
                consumeStoredValue(principal, shopId, orderId, reservation);
            } else if ("COMBO_TIMES".equals(reservation.get("benefitType"))) {
                consumeCombo(principal, shopId, orderId, reservation);
            }
            jdbcTemplate.update(
                "UPDATE benefit_reservation SET status = 'CONSUMED', version = version + 1, completed_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND version = ? AND status = 'RESERVED'",
                number(reservation.get("id")), number(reservation.get("version"))
            );
        }
        return reservations.size();
    }

    @Transactional
    public int releaseOrderBenefits(TenantPrincipal principal, long shopId, long orderId, String reason) {
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList(
            """
            SELECT id, benefit_type AS benefitType, package_instance_id AS packageInstanceId,
                   package_instance_item_id AS packageItemId, stored_value_batch_id AS batchId,
                   quantity,
                   principal_amount AS principalAmount, gift_amount AS giftAmount, version
            FROM benefit_reservation
            WHERE tenant_id = ? AND shop_id = ? AND order_id = ? AND status = 'RESERVED'
            ORDER BY id FOR UPDATE
            """,
            principal.tenantId(), shopId, orderId
        );
        for (Map<String, Object> reservation : reservations) {
            if ("STORED_VALUE".equals(reservation.get("benefitType"))) {
                BigDecimal principalAmount = decimal(reservation.get("principalAmount"));
                BigDecimal giftAmount = decimal(reservation.get("giftAmount"));
                long batchId = number(reservation.get("batchId"));
                jdbcTemplate.update(
                    "UPDATE stored_value_batch SET principal_frozen = principal_frozen - ?, gift_frozen = gift_frozen - ? WHERE id = ?",
                    principalAmount, giftAmount, batchId
                );
                Map<String, Object> balance = jdbcTemplate.queryForMap(
                    "SELECT principal_remaining - principal_frozen AS principalAfter, gift_remaining - gift_frozen AS giftAfter FROM stored_value_batch WHERE id = ?",
                    batchId
                );
                insertStoredLedger(
                    principal, shopId, batchId, orderId, number(reservation.get("id")), "RELEASE",
                    principalAmount, giftAmount, decimal(balance.get("principalAfter")),
                    decimal(balance.get("giftAfter")), "RELEASE:" + orderId,
                    "release-benefit:" + orderId, hash("release-benefit:" + orderId), reason
                );
            } else if ("COMBO_TIMES".equals(reservation.get("benefitType"))) {
                BigDecimal quantity = decimal(reservation.get("quantity"));
                jdbcTemplate.update(
                    "UPDATE package_instance_item SET frozen_quantity = frozen_quantity - ? WHERE id = ?",
                    quantity, number(reservation.get("packageItemId"))
                );
                jdbcTemplate.update(
                    "UPDATE package_instance SET frozen_quantity = frozen_quantity - ?, version = version + 1 WHERE id = ?",
                    quantity, number(reservation.get("packageInstanceId"))
                );
            }
            jdbcTemplate.update(
                "UPDATE benefit_reservation SET status = 'RELEASED', version = version + 1, completed_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND version = ? AND status = 'RESERVED'",
                number(reservation.get("id")), number(reservation.get("version"))
            );
        }
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
            "SELECT id, version FROM member_coupon WHERE tenant_id = ? AND shop_id = ? AND locked_order_id = ? AND status = 'LOCKED' FOR UPDATE",
            principal.tenantId(), shopId, orderId
        );
        for (Map<String, Object> coupon : coupons) {
            long couponId = number(coupon.get("id"));
            jdbcTemplate.update(
                "UPDATE member_coupon SET status = 'AVAILABLE', locked_order_id = NULL, version = version + 1 WHERE id = ? AND version = ? AND status = 'LOCKED'",
                couponId, number(coupon.get("version"))
            );
            insertCouponLedger(
                principal, shopId, couponId, orderId, "RELEASE", "LOCKED", "AVAILABLE",
                "RELEASE:" + orderId, "release-coupon:" + orderId,
                hash("release-coupon:" + orderId), reason
            );
        }
        int discountCards = jdbcTemplate.update(
            """
            UPDATE package_instance pi
            JOIN order_pricing_decision opd ON opd.discount_card_instance_id = pi.id
            SET pi.usage_count = GREATEST(pi.usage_count - 1, 0), pi.version = pi.version + 1
            WHERE opd.tenant_id = ? AND opd.shop_id = ? AND opd.order_id = ?
              AND opd.selection_type = 'DISCOUNT_CARD'
            """,
            principal.tenantId(), shopId, orderId
        );
        return reservations.size() + coupons.size() + discountCards;
    }

    private Map<String, Object> issueCard(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long productId,
        String sourceType,
        Long sourceOrderId,
        String sourceReference,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id, instance_no AS instanceNo, card_type AS cardType, status, issue_request_hash AS requestHash FROM package_instance WHERE tenant_id = ? AND issue_idempotency_key = ?",
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().remove("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "发卡幂等键已用于不同请求");
            }
            return replay.getFirst();
        }
        requireMember(principal.tenantId(), shopId, memberId);
        Map<String, Object> product = cardProduct(principal.tenantId(), productId);
        if (!"ACTIVE".equals(product.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "卡产品未上架");
        }
        long productShopId = number(product.get("shopId"));
        if (productShopId != shopId) throw new ApiException(HttpStatus.NOT_FOUND, "卡产品不属于当前门店");
        String cardType = product.get("cardType").toString();
        BigDecimal totalQuantity = "COMBO_TIMES".equals(cardType)
            ? jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_total), 0) FROM package_product_item WHERE package_product_id = ?",
                BigDecimal.class, productId
            )
            : BigDecimal.ZERO.setScale(4);
        LocalDate validFrom = LocalDate.now();
        LocalDate validUntil = validFrom.plusDays(number(product.get("validityDays")) - 1);
        String instanceNo = businessNo("CARD");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO package_instance (
                    tenant_id, shop_id, member_id, package_product_id, instance_no,
                    card_type, source_order_id, source_type, source_reference,
                    purchase_price, valid_from, valid_until, total_quantity,
                    remaining_quantity, frozen_quantity, usage_count, status, version,
                    issue_idempotency_key, issue_request_hash, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'ACTIVE', 1, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setLong(4, productId);
            statement.setString(5, instanceNo);
            statement.setString(6, cardType);
            if (sourceOrderId == null) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, sourceOrderId);
            statement.setString(8, sourceType);
            statement.setString(9, trim(sourceReference));
            statement.setBigDecimal(10, decimal(product.get("salePrice")));
            statement.setObject(11, validFrom);
            statement.setObject(12, validUntil);
            statement.setBigDecimal(13, totalQuantity);
            statement.setBigDecimal(14, totalQuantity);
            statement.setString(15, idempotencyKey);
            statement.setString(16, requestHash);
            statement.setLong(17, principal.accountId());
            statement.setLong(18, principal.accountId());
            return statement;
        }, keyHolder);
        long instanceId = generated(keyHolder, "卡项发放失败");
        if ("COMBO_TIMES".equals(cardType)) issueComboItems(
            principal, shopId, productId, instanceId, totalQuantity, idempotencyKey, requestHash
        );
        if ("STORED_VALUE".equals(cardType)) issueStoredValue(
            principal, shopId, instanceId, product, validFrom, validUntil, idempotencyKey, requestHash
        );
        audit(principal, shopId, "CARD_ISSUE", "PACKAGE_INSTANCE", instanceId, sourceType);
        outboxEventService.append(
            principal, shopId, "PACKAGE_INSTANCE", String.valueOf(instanceId),
            "CardIssued", Map.of("cardInstanceId", instanceId)
        );
        return Map.of("id", instanceId, "instanceNo", instanceNo, "cardType", cardType, "status", "ACTIVE");
    }

    private void issueComboItems(
        TenantPrincipal principal,
        long shopId,
        long productId,
        long instanceId,
        BigDecimal totalQuantity,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT service_id AS serviceId, service_name_snapshot AS serviceName, quantity_total AS quantity FROM package_product_item WHERE package_product_id = ? ORDER BY id",
            productId
        );
        for (Map<String, Object> item : items) {
            jdbcTemplate.update(
                """
                INSERT INTO package_instance_item (
                    tenant_id, package_instance_id, service_id, service_name_snapshot,
                    total_quantity, remaining_quantity, frozen_quantity
                ) VALUES (?, ?, ?, ?, ?, ?, 0)
                """,
                principal.tenantId(), instanceId, number(item.get("serviceId")),
                item.get("serviceName"), decimal(item.get("quantity")), decimal(item.get("quantity"))
            );
        }
        jdbcTemplate.update(
            """
            INSERT INTO package_ledger (
                tenant_id, shop_id, package_instance_id, entry_type,
                quantity_delta, balance_after, business_key, idempotency_key,
                request_hash, reason, created_by
            ) VALUES (?, ?, ?, 'ISSUE', ?, ?, ?, ?, ?, 'SC4 发放次卡', ?)
            """,
            principal.tenantId(), shopId, instanceId, totalQuantity, totalQuantity,
            "ISSUE:" + instanceId, idempotencyKey + ":ledger", requestHash, principal.accountId()
        );
    }

    private void issueStoredValue(
        TenantPrincipal principal,
        long shopId,
        long instanceId,
        Map<String, Object> product,
        LocalDate validFrom,
        LocalDate validUntil,
        String idempotencyKey,
        String requestHash
    ) {
        BigDecimal principalAmount = decimal(product.get("principalAmount"));
        BigDecimal giftAmount = decimal(product.get("giftAmount"));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO stored_value_batch (
                    tenant_id, shop_id, package_instance_id, batch_no,
                    principal_total, gift_total, principal_remaining, gift_remaining,
                    principal_frozen, gift_frozen, valid_from, valid_until, status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, 'ACTIVE', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, instanceId);
            statement.setString(4, businessNo("SVB"));
            statement.setBigDecimal(5, principalAmount);
            statement.setBigDecimal(6, giftAmount);
            statement.setBigDecimal(7, principalAmount);
            statement.setBigDecimal(8, giftAmount);
            statement.setObject(9, validFrom);
            statement.setObject(10, validUntil);
            statement.setLong(11, principal.accountId());
            return statement;
        }, keyHolder);
        long batchId = generated(keyHolder, "储值批次创建失败");
        insertStoredLedger(
            principal, shopId, batchId, null, null, "ISSUE",
            principalAmount, giftAmount, principalAmount, giftAmount,
            "ISSUE:" + batchId, idempotencyKey + ":ledger", requestHash, "SC4 发放储值卡"
        );
    }

    private void consumeStoredValue(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        Map<String, Object> reservation
    ) {
        long batchId = number(reservation.get("batchId"));
        BigDecimal principalAmount = decimal(reservation.get("principalAmount"));
        BigDecimal giftAmount = decimal(reservation.get("giftAmount"));
        jdbcTemplate.update(
            """
            UPDATE stored_value_batch
            SET principal_remaining = principal_remaining - ?, gift_remaining = gift_remaining - ?,
                principal_frozen = principal_frozen - ?, gift_frozen = gift_frozen - ?,
                status = CASE WHEN principal_remaining - ? + gift_remaining - ? = 0 THEN 'EXHAUSTED' ELSE status END
            WHERE id = ?
            """,
            principalAmount, giftAmount, principalAmount, giftAmount,
            principalAmount, giftAmount, batchId
        );
        Map<String, Object> balance = jdbcTemplate.queryForMap(
            "SELECT principal_remaining - principal_frozen AS principalAfter, gift_remaining - gift_frozen AS giftAfter FROM stored_value_batch WHERE id = ?",
            batchId
        );
        insertStoredLedger(
            principal, shopId, batchId, orderId, number(reservation.get("id")), "CONSUME",
            principalAmount.negate(), giftAmount.negate(), decimal(balance.get("principalAfter")),
            decimal(balance.get("giftAfter")), "CONSUME:" + orderId,
            "consume-benefit:" + orderId, hash("consume-benefit:" + orderId), "护理完成核销储值"
        );
    }

    private void consumeCombo(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        Map<String, Object> reservation
    ) {
        long instanceId = number(reservation.get("packageInstanceId"));
        long itemId = number(reservation.get("packageItemId"));
        BigDecimal quantity = decimal(reservation.get("quantity"));
        jdbcTemplate.update(
            "UPDATE package_instance_item SET remaining_quantity = remaining_quantity - ?, frozen_quantity = frozen_quantity - ? WHERE id = ?",
            quantity, quantity, itemId
        );
        jdbcTemplate.update(
            "UPDATE package_instance SET remaining_quantity = remaining_quantity - ?, frozen_quantity = frozen_quantity - ?, status = CASE WHEN remaining_quantity - ? = 0 THEN 'EXHAUSTED' ELSE status END, version = version + 1 WHERE id = ?",
            quantity, quantity, quantity, instanceId
        );
        BigDecimal after = jdbcTemplate.queryForObject(
            "SELECT remaining_quantity FROM package_instance WHERE id = ?", BigDecimal.class, instanceId
        );
        jdbcTemplate.update(
            """
            INSERT INTO package_ledger (
                tenant_id, shop_id, package_instance_id, package_instance_item_id,
                entry_type, quantity_delta, balance_after, business_key,
                idempotency_key, request_hash, reason, created_by
            ) VALUES (?, ?, ?, ?, 'WRITE_OFF', ?, ?, ?, ?, ?, '护理完成核销次卡', ?)
            """,
            principal.tenantId(), shopId, instanceId, itemId, quantity.negate(), after,
            "WRITE_OFF:ORDER:" + orderId, "consume-combo:" + orderId,
            hash("consume-combo:" + orderId), principal.accountId()
        );
    }

    private Map<String, Object> cardProduct(long tenantId, long productId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, tenant_id AS tenantId, shop_id AS shopId,
                   package_code AS packageCode, name, card_type AS cardType,
                   sale_price AS salePrice, principal_amount AS principalAmount,
                   gift_amount AS giftAmount, discount_percent AS discountPercent,
                   validity_days AS validityDays, status, version
            FROM package_product WHERE id = ? AND tenant_id = ?
            """,
            productId, tenantId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "卡产品不存在");
        return rows.getFirst();
    }

    private Map<String, Object> lockCoupon(long tenantId, long shopId, long memberId, Long couponId) {
        if (couponId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择优惠券");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, status, version FROM member_coupon WHERE id = ? AND tenant_id = ? AND shop_id = ? AND member_id = ? FOR UPDATE",
            couponId, tenantId, shopId, memberId
        );
        if (rows.isEmpty() || !List.of("AVAILABLE", "RETURNED").contains(rows.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "优惠券已不可用");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockDiscountCard(long tenantId, long shopId, long memberId, Long cardId) {
        if (cardId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择折扣卡");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pi.id, pi.version, pi.status, pp.usage_limit AS usageLimit, pi.usage_count AS usageCount
            FROM package_instance pi JOIN package_product pp ON pp.id = pi.package_product_id
            WHERE pi.id = ? AND pi.tenant_id = ? AND pi.shop_id = ? AND pi.member_id = ?
              AND pi.card_type = 'DISCOUNT' FOR UPDATE
            """,
            cardId, tenantId, shopId, memberId
        );
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "折扣卡已不可用");
        }
        return rows.getFirst();
    }

    private Map<String, Object> requireCouponTemplate(long tenantId, long shopId, long templateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, validity_days AS validityDays, status FROM coupon_template WHERE id = ? AND tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)",
            templateId, tenantId, shopId
        );
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "优惠券模板不存在或未启用");
        }
        return rows.getFirst();
    }

    private Map<String, Object> requireService(long tenantId, long shopId, long serviceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, name FROM service_item WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'",
            serviceId, tenantId, shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "护理项目不存在或未启用");
        return rows.getFirst();
    }

    private void requireMember(long tenantId, long shopId, long memberId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM member m JOIN member_shop_profile msp
              ON msp.member_id = m.id AND msp.tenant_id = m.tenant_id
            WHERE m.id = ? AND m.tenant_id = ? AND m.status = 'ACTIVE'
              AND msp.shop_id = ? AND msp.status = 'ACTIVE'
            """,
            Integer.class, memberId, tenantId, shopId
        );
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在或未在当前门店启用");
    }

    private BigDecimal couponDiscount(BigDecimal subtotal, Map<String, Object> coupon) {
        String type = coupon.get("couponType").toString();
        if ("DISCOUNT".equals(type)) {
            return DiscountSelectionPolicy.percentageReduction(
                subtotal, decimal(coupon.get("benefitValue")), null
            );
        }
        if ("SERVICE_EXPERIENCE".equals(type)) return subtotal;
        return DiscountSelectionPolicy.fixedReduction(subtotal, decimal(coupon.get("benefitValue")));
    }

    private Map<String, Object> option(
        String selectionType,
        Long referenceId,
        String name,
        BigDecimal discount,
        BigDecimal payable
    ) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("selectionType", selectionType);
        option.put("referenceId", referenceId);
        option.put("name", name);
        option.put("discountAmount", discount.setScale(2, RoundingMode.HALF_UP));
        option.put("payableAmount", payable.setScale(2, RoundingMode.HALF_UP));
        return option;
    }

    private void insertCouponLedger(
        TenantPrincipal principal,
        long shopId,
        long couponId,
        Long orderId,
        String entryType,
        String fromStatus,
        String toStatus,
        String businessKey,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO coupon_ledger (
                tenant_id, shop_id, member_coupon_id, order_id, entry_type,
                from_status, to_status, business_key, idempotency_key,
                request_hash, reason, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, couponId, orderId, entryType,
            fromStatus, toStatus, businessKey, idempotencyKey, requestHash, reason,
            principal.accountId()
        );
    }

    private void insertStoredLedger(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        Long orderId,
        Long reservationId,
        String entryType,
        BigDecimal principalDelta,
        BigDecimal giftDelta,
        BigDecimal principalAfter,
        BigDecimal giftAfter,
        String businessKey,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO stored_value_ledger (
                tenant_id, shop_id, batch_id, order_id, reservation_id, entry_type,
                principal_delta, gift_delta, principal_after, gift_after,
                business_key, idempotency_key, request_hash, reason, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, batchId, orderId, reservationId, entryType,
            principalDelta, giftDelta, principalAfter, giftAfter, businessKey,
            idempotencyKey, requestHash, reason, principal.accountId()
        );
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId,
        String note
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, metadata
            ) VALUES (?, ?, ?, ?, ?, ?, JSON_OBJECT('note', ?))
            """,
            principal.tenantId(), shopId, principal.accountId(), action, entityType, entityId, note
        );
    }

    private long generated(KeyHolder keyHolder, String message) {
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        return key.longValue();
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private String cardType(String value) {
        String normalized = required(value, "卡类型").toUpperCase(Locale.ROOT);
        if (!List.of("COMBO_TIMES", "STORED_VALUE", "DISCOUNT").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的卡类型");
        }
        return normalized;
    }

    private String source(String value) {
        String normalized = required(value, "发卡来源").toUpperCase(Locale.ROOT);
        if (!List.of("ONLINE_PURCHASE", "OFFLINE_SALE", "GIFT", "REISSUE", "LEGACY_IMPORT").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的发卡来源");
        }
        return normalized;
    }

    private String couponType(String value) {
        String normalized = required(value, "优惠券类型").toUpperCase(Locale.ROOT);
        if (!List.of("THRESHOLD_REDUCTION", "CASH", "DISCOUNT", "SERVICE_EXPERIENCE").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的优惠券类型");
        }
        return normalized;
    }

    private String couponSource(String value) {
        String normalized = required(value, "发券来源").toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN_DIRECT", "ACTIVITY_CLAIM", "AFTERSALE_COMPENSATION").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的发券来源");
        }
        return normalized;
    }

    private BigDecimal money(BigDecimal value, boolean allowZero) {
        if (value == null) value = BigDecimal.ZERO;
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0 || (!allowZero && normalized.signum() == 0)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, allowZero ? "金额不能小于零" : "金额必须大于零");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "金额最多两位小数");
        }
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "次数必须大于零");
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "次数最多四位小数");
        }
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能为空");
        return value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String businessNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String hash(String value) {
        return com.face.platform.v3.auth.SessionTokenCodec.sha256(value);
    }

    public record CardProductCommand(
        long shopId,
        String packageCode,
        String name,
        String description,
        String cardType,
        BigDecimal salePrice,
        BigDecimal principalAmount,
        BigDecimal giftAmount,
        BigDecimal discountPercent,
        BigDecimal minimumSpend,
        BigDecimal maximumSavings,
        Integer usageLimit,
        int validityDays,
        String scopeJson,
        List<CardProductItem> items
    ) {
    }

    public record CardProductItem(long serviceId, BigDecimal quantity) {
    }

    public record CardIssueCommand(
        long shopId,
        long memberId,
        long packageProductId,
        String sourceType,
        String sourceReference
    ) {
    }

    public record CouponTemplateCommand(
        long shopId,
        String templateCode,
        String name,
        String couponType,
        BigDecimal thresholdAmount,
        BigDecimal benefitValue,
        Long serviceId,
        int validityDays,
        boolean returnOnFullRefund
    ) {
    }

    public record CouponIssueCommand(
        long shopId,
        long memberId,
        long templateId,
        String sourceType,
        String sourceReference
    ) {
    }
}
