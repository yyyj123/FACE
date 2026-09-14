package com.face.platform.checkout;

import com.face.platform.api.ApiException;
import com.face.platform.benefit.BenefitApplicationService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.payment.PaymentAdapterRegistry;
import com.face.platform.payment.PaymentChannelAdapter;
import com.face.platform.payment.PaymentChannelPolicy;
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
import java.math.RoundingMode;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutApplicationService {

    private static final List<String> CLIENT_CHANNELS =
        List.of("DEMO_MOCK", "WECHAT", "ALIPAY", "AGGREGATOR");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final BenefitApplicationService benefitService;
    private final PointsApplicationService pointsService;
    private final PaymentAdapterRegistry adapterRegistry;
    private final PaymentCompletionApplicationService completionService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public CheckoutApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        BenefitApplicationService benefitService,
        PointsApplicationService pointsService,
        PaymentAdapterRegistry adapterRegistry,
        PaymentCompletionApplicationService completionService,
        OutboxEventService outboxEventService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.benefitService = benefitService;
        this.pointsService = pointsService;
        this.adapterRegistry = adapterRegistry;
        this.completionService = completionService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> purchasableCards(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        Long shopId = principal.homeShopId();
        if (shopId == null) throw new ApiException(HttpStatus.CONFLICT, "会员账号未绑定默认门店");
        return jdbcTemplate.queryForList(
            """
            SELECT pp.id, pp.package_code AS packageCode, pp.name,
                   pp.card_type AS cardType, pp.description,
                   pp.sale_price AS salePrice, pp.principal_amount AS principalAmount,
                   pp.gift_amount AS giftAmount, pp.discount_percent AS discountPercent,
                   pp.minimum_spend AS minimumSpend, pp.maximum_savings AS maximumSavings,
                   pp.validity_days AS validityDays
            FROM package_product pp
            WHERE pp.tenant_id = ? AND pp.shop_id = ? AND pp.status = 'ACTIVE'
            ORDER BY pp.card_type, pp.name, pp.id
            """,
            principal.tenantId(), shopId
        );
    }

    public Map<String, Object> quote(TenantPrincipal principal, QuoteCommand command) {
        long memberId = accessService.requireMemberId(principal);
        CheckoutTarget target = target(principal, memberId, command.lockToken(), command.packageProductId(), false);
        List<Map<String, Object>> candidates = new ArrayList<>(target.booking()
            ? benefitService.discountCandidates(
                principal.tenantId(), target.shopId(), memberId, target.referenceId(), target.subtotal()
            )
            : List.of(noDiscount(target.subtotal())));
        Map<String, Object> points = pointsService.redemptionCandidate(
            principal.tenantId(), target.shopId(), memberId,
            target.booking() ? "SERVICE" : "PACKAGE", target.referenceId(), target.subtotal()
        );
        if (Boolean.TRUE.equals(points.get("available"))) candidates.add(points);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetType", target.booking() ? "BOOKING" : "CARD_PURCHASE");
        result.put("shopId", target.shopId());
        result.put("memberId", memberId);
        result.put("item", Map.of(
            "id", target.referenceId(),
            "name", target.name(),
            "subtotalAmount", target.subtotal()
        ));
        result.put("discountOptions", candidates);
        result.put("comboCards", target.booking()
            ? benefitService.comboCandidates(
                principal.tenantId(), target.shopId(), memberId, target.referenceId()
            )
            : List.of());
        result.put("defaultSelection", "NONE");
        result.put("points", points);
        result.put("paymentChannels", paymentChannels());
        result.put("expiresAt", target.expiresAt());
        return result;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        CheckoutCommand command,
        String idempotencyKey,
        String requestHash
    ) {
        long memberId = accessService.requireMemberId(principal);
        String safeKey = required(idempotencyKey, "幂等键");
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT so.id AS orderId, so.order_no AS orderNo, so.status,
                   so.payable_amount AS payableAmount, opd.request_hash AS requestHash,
                   pt.id AS paymentId, pt.payment_no AS paymentNo,
                   pt.payment_method AS paymentMethod, pt.status AS paymentStatus
            FROM order_pricing_decision opd
            JOIN sales_order so ON so.id = opd.order_id
            LEFT JOIN payment_transaction pt ON pt.order_id = so.id
            WHERE opd.tenant_id = ? AND opd.idempotency_key = ?
            ORDER BY pt.id DESC LIMIT 1
            """,
            principal.tenantId(), safeKey
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().remove("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "结算幂等键已用于不同请求");
            }
            return replay.getFirst();
        }

        CheckoutTarget target = target(
            principal, memberId, command.lockToken(), command.packageProductId(), true
        );
        List<Map<String, Object>> candidates = new ArrayList<>(target.booking()
            ? benefitService.discountCandidates(
                principal.tenantId(), target.shopId(), memberId, target.referenceId(), target.subtotal()
            )
            : List.of(noDiscount(target.subtotal())));
        Map<String, Object> points = pointsService.redemptionCandidate(
            principal.tenantId(), target.shopId(), memberId,
            target.booking() ? "SERVICE" : "PACKAGE", target.referenceId(), target.subtotal()
        );
        if (Boolean.TRUE.equals(points.get("available"))) candidates.add(points);
        Map<String, Object> selected = benefitService.requireSelectedCandidate(
            candidates, command.selectionType(), command.selectionReferenceId()
        );
        if (command.comboCardId() != null) {
            if (!target.booking()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "次卡只能用于护理预约");
            }
            if (!"NONE".equals(selected.get("selectionType"))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "次卡权益不能与活动、优惠券或折扣卡叠加");
            }
            Map<String, Object> entitlement = new LinkedHashMap<>(selected);
            entitlement.put("discountAmount", target.subtotal());
            entitlement.put("payableAmount", BigDecimal.ZERO.setScale(2));
            entitlement.put("entitlementType", "COMBO_TIMES");
            entitlement.put("comboCardInstanceId", command.comboCardId());
            selected = entitlement;
        }
        BigDecimal discount = decimal(selected.get("discountAmount"));
        BigDecimal payable = decimal(selected.get("payableAmount"));
        String selectionType = selected.get("selectionType").toString();
        Long selectionReferenceId = selected.get("referenceId") == null
            ? null : number(selected.get("referenceId"));
        String paymentMethod = normalizePayment(command.paymentMethod(), payable);

        long orderId = insertOrder(
            principal, target, memberId, discount, payable, command.memberNote(), safeKey, requestHash
        );
        insertOrderItem(orderId, target, discount, payable);
        insertPricingDecision(
            principal, target, orderId, selectionType, selectionReferenceId,
            candidates, selected, command.memberNote(), safeKey, requestHash
        );
        benefitService.lockPricingBenefit(
            principal, target.shopId(), memberId, orderId, selectionType,
            selectionReferenceId, safeKey, requestHash
        );

        Long reservationId = null;
        if ("POINTS".equals(selectionType)) {
            reservationId = pointsService.reserve(
                principal, target.shopId(), memberId, "SALES_ORDER", orderId,
                number(selected.get("pointsUsed")), safeKey + ":points", requestHash
            );
        } else if (command.comboCardId() != null) {
            reservationId = benefitService.reserveComboTime(
                principal, target.shopId(), memberId, orderId, target.referenceId(),
                command.comboCardId(), safeKey + ":combo-time", requestHash
            );
        } else if ("STORED_VALUE".equals(paymentMethod)) {
            if (command.storedValueCardId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "请选择一张储值卡");
            }
            reservationId = benefitService.reserveStoredValue(
                principal, target.shopId(), memberId, orderId,
                command.storedValueCardId(), payable,
                safeKey + ":stored-value", requestHash
            );
        }

        Map<String, Object> payment = createPayment(
            principal, target.shopId(), orderId, payable, paymentMethod,
            safeKey + ":payment", requestHash
        );
        long paymentId = number(payment.get("id"));
        Map<String, Object> fulfillment = Map.of();
        if ("SUCCESS".equals(payment.get("status"))) {
            fulfillment = completionService.complete(paymentId);
        }
        outboxEventService.append(
            principal, target.shopId(), "SALES_ORDER", String.valueOf(orderId),
            "CheckoutCreated", Map.of(
                "orderId", orderId,
                "paymentId", paymentId
            )
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", orderNo(orderId));
        result.put("orderStatus", "SUCCESS".equals(payment.get("status")) ? "PAID" : "UNPAID");
        result.put("subtotalAmount", target.subtotal());
        result.put("discountAmount", discount);
        result.put("payableAmount", payable);
        result.put("payment", payment);
        result.put("reservationId", reservationId);
        result.put("fulfillment", fulfillment);
        return result;
    }

    public Map<String, Object> detail(TenantPrincipal principal, long orderId) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT so.id AS orderId, so.order_no AS orderNo, so.status AS orderStatus,
                   so.subtotal_amount AS subtotalAmount, so.discount_amount AS discountAmount,
                   so.payable_amount AS payableAmount, so.paid_amount AS paidAmount,
                   so.appointment_id AS appointmentId, opd.selection_type AS selectionType,
                   pt.id AS paymentId, pt.payment_no AS paymentNo,
                   pt.payment_method AS paymentMethod, pt.status AS paymentStatus,
                   pt.channel_status AS channelStatus
            FROM sales_order so
            JOIN order_pricing_decision opd ON opd.order_id = so.id
            LEFT JOIN payment_transaction pt ON pt.order_id = so.id
            WHERE so.id = ? AND so.tenant_id = ? AND so.member_id = ?
            ORDER BY pt.id DESC LIMIT 1
            """,
            orderId, principal.tenantId(), memberId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        return rows.getFirst();
    }

    private CheckoutTarget target(
        TenantPrincipal principal,
        long memberId,
        String lockToken,
        Long packageProductId,
        boolean forUpdate
    ) {
        boolean booking = lockToken != null && !lockToken.isBlank();
        boolean card = packageProductId != null;
        if (booking == card) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "预约结算与卡项购买必须且只能选择一种");
        }
        if (booking) {
            String suffix = forUpdate ? " FOR UPDATE" : "";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT btl.id AS lockId, btl.shop_id AS shopId, btl.service_id AS serviceId,
                       btl.status, btl.expires_at AS expiresAt, si.name,
                       COALESCE(si.member_price, si.list_price) AS subtotal
                FROM booking_time_lock btl
                JOIN service_item si ON si.id = btl.service_id AND si.tenant_id = btl.tenant_id
                WHERE btl.tenant_id = ? AND btl.lock_token = ? AND btl.member_id = ?
                  AND btl.created_by = ?
                """ + suffix,
                principal.tenantId(), lockToken.trim(), memberId, principal.accountId()
            );
            if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "预约时间锁不存在");
            Map<String, Object> row = rows.getFirst();
            if (!"HELD".equals(row.get("status"))) {
                throw new ApiException(HttpStatus.CONFLICT, "预约时间锁已释放或已使用");
            }
            java.time.LocalDateTime expires = localDateTime(row.get("expiresAt"));
            if (!expires.isAfter(java.time.LocalDateTime.now())) {
                throw new ApiException(HttpStatus.CONFLICT, "预约时间锁已超时，请重新选择时段");
            }
            return new CheckoutTarget(
                true, number(row.get("shopId")), number(row.get("serviceId")),
                number(row.get("lockId")), row.get("name").toString(),
                money(decimal(row.get("subtotal"))), expires
            );
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, name, sale_price AS subtotal, status
            FROM package_product
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            """ + (forUpdate ? " FOR UPDATE" : ""),
            packageProductId, principal.tenantId(), principal.homeShopId()
        );
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "卡产品不存在或未上架");
        }
        Map<String, Object> row = rows.getFirst();
        return new CheckoutTarget(
            false, number(row.get("shopId")), packageProductId, null,
            row.get("name").toString(), money(decimal(row.get("subtotal"))), null
        );
    }

    private long insertOrder(
        TenantPrincipal principal,
        CheckoutTarget target,
        long memberId,
        BigDecimal discount,
        BigDecimal payable,
        String note,
        String idempotencyKey,
        String requestHash
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO sales_order (
                    tenant_id, shop_id, order_no, create_idempotency_key,
                    create_request_hash, business_date, member_id,
                    subtotal_amount, discount_amount, payable_amount,
                    paid_amount, refunded_amount, currency_code, status,
                    version, notes, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'CNY', 'UNPAID', 0, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, target.shopId());
            statement.setString(3, businessNo("SO"));
            statement.setString(4, idempotencyKey);
            statement.setString(5, requestHash);
            statement.setObject(6, LocalDate.now());
            statement.setLong(7, memberId);
            statement.setBigDecimal(8, target.subtotal());
            statement.setBigDecimal(9, discount);
            statement.setBigDecimal(10, payable);
            statement.setString(11, trim(note));
            statement.setLong(12, principal.accountId());
            statement.setLong(13, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "订单创建失败");
        return key.longValue();
    }

    private void insertOrderItem(
        long orderId,
        CheckoutTarget target,
        BigDecimal discount,
        BigDecimal payable
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO sales_order_item (
                order_id, item_type, service_id, product_id, package_product_id,
                item_name_snapshot, dimension_snapshot_quality,
                quantity, unit_price, discount_amount, line_amount
            ) VALUES (?, ?, ?, NULL, ?, ?, 'TRANSACTION_TIME', 1, ?, ?, ?)
            """,
            orderId,
            target.booking() ? "SERVICE" : "PACKAGE",
            target.booking() ? target.referenceId() : null,
            target.booking() ? null : target.referenceId(),
            target.name(), target.subtotal(), discount, payable
        );
    }

    private void insertPricingDecision(
        TenantPrincipal principal,
        CheckoutTarget target,
        long orderId,
        String selectionType,
        Long selectionReferenceId,
        List<Map<String, Object>> candidates,
        Map<String, Object> selected,
        String memberNote,
        String idempotencyKey,
        String requestHash
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>(selected);
        snapshot.put("member_note", trim(memberNote));
        String candidatesJson = json(candidates);
        String selectionJson = json(snapshot);
        Long activityId = "ACTIVITY".equals(selectionType) ? selectionReferenceId : null;
        Long couponId = "COUPON".equals(selectionType) ? selectionReferenceId : null;
        Long discountCardId = "DISCOUNT_CARD".equals(selectionType) ? selectionReferenceId : null;
        jdbcTemplate.update(
            """
            INSERT INTO order_pricing_decision (
                tenant_id, shop_id, order_id, booking_time_lock_id, selection_type,
                activity_id, member_coupon_id, discount_card_instance_id,
                subtotal_amount, discount_amount, payable_amount,
                candidate_snapshot_json, selection_snapshot_json,
                idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?)
            """,
            principal.tenantId(), target.shopId(), orderId, target.lockId(), selectionType,
            activityId, couponId, discountCardId, target.subtotal(),
            decimal(selected.get("discountAmount")), decimal(selected.get("payableAmount")),
            candidatesJson, selectionJson, idempotencyKey, requestHash, principal.accountId()
        );
    }

    private Map<String, Object> createPayment(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        BigDecimal amount,
        String paymentMethod,
        String idempotencyKey,
        String requestHash
    ) {
        String paymentNo = businessNo("PAY");
        String status;
        String channelCode = null;
        String channelStatus = null;
        String channelRequestNo = null;
        if ("ZERO_AMOUNT".equals(paymentMethod) || "STORED_VALUE".equals(paymentMethod)) {
            status = "SUCCESS";
            channelStatus = "INTERNAL_CONFIRMED";
        } else {
            PaymentChannelAdapter adapter = adapterRegistry.requireConfigured(paymentMethod);
            PaymentChannelAdapter.PaymentInitiation initiation =
                adapter.initiate(paymentNo, amount, "CNY");
            status = "PENDING";
            channelCode = paymentMethod;
            channelStatus = initiation.channelStatus();
            channelRequestNo = initiation.channelRequestNo();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String finalStatus = status;
        String finalChannelCode = channelCode;
        String finalChannelStatus = channelStatus;
        String finalChannelRequestNo = channelRequestNo;
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO payment_transaction (
                    tenant_id, shop_id, order_id, payment_no, payment_method,
                    channel_code, amount, refunded_amount, currency_code,
                    channel_status, channel_request_no, idempotency_key,
                    request_hash, status, paid_at, confirmed_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'CNY', ?, ?, ?, ?, ?,
                          CASE WHEN ? = 'SUCCESS' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                          CASE WHEN ? = 'SUCCESS' THEN CURRENT_TIMESTAMP(3) ELSE NULL END, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, orderId);
            statement.setString(4, paymentNo);
            statement.setString(5, paymentMethod);
            statement.setString(6, finalChannelCode);
            statement.setBigDecimal(7, amount);
            statement.setString(8, finalChannelStatus);
            statement.setString(9, finalChannelRequestNo);
            statement.setString(10, idempotencyKey);
            statement.setString(11, requestHash);
            statement.setString(12, finalStatus);
            statement.setString(13, finalStatus);
            statement.setString(14, finalStatus);
            statement.setLong(15, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "支付记录创建失败");
        long paymentId = key.longValue();
        if ("SUCCESS".equals(status)) {
            int changed = jdbcTemplate.update(
                """
                UPDATE sales_order
                SET paid_amount = payable_amount, payment_method = ?, status = 'PAID',
                    paid_at = CURRENT_TIMESTAMP(3), version = version + 1, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'UNPAID'
                """,
                paymentMethod, principal.accountId(), orderId, principal.tenantId(), shopId
            );
            if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "订单状态已变化");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", paymentId);
        result.put("paymentNo", paymentNo);
        result.put("paymentMethod", paymentMethod);
        result.put("channelCode", channelCode);
        result.put("channelStatus", channelStatus);
        result.put("channelRequestNo", channelRequestNo);
        result.put("amount", amount);
        result.put("status", status);
        return result;
    }

    private List<Map<String, Object>> paymentChannels() {
        List<Map<String, Object>> channels = new java.util.ArrayList<>();
        channels.add(Map.of(
            "code", "STORED_VALUE",
            "configured", true,
            "message", "使用一张储值卡支付"
        ));
        for (String code : CLIENT_CHANNELS) {
            boolean configured = adapterRegistry.configured(code);
            channels.add(Map.of(
                "code", code,
                "configured", configured,
                "message", configured ? "支付通道可用" : "支付渠道暂未开通"
            ));
        }
        // 报价时尚未选择优惠；始终返回零元通道，由客户端仅在应付为零时展示。
        channels.add(Map.of("code", "ZERO_AMOUNT", "configured", true, "message", "零元订单直接确认"));
        return channels;
    }

    private String normalizePayment(String value, BigDecimal payable) {
        String method = required(value, "支付方式").toUpperCase(Locale.ROOT);
        if (payable.signum() == 0) {
            if (!"ZERO_AMOUNT".equals(method)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "零元订单必须使用 ZERO_AMOUNT");
            }
            return method;
        }
        if ("ZERO_AMOUNT".equals(method)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "非零订单不能使用 ZERO_AMOUNT");
        }
        if ("STORED_VALUE".equals(method)) return method;
        if (!CLIENT_CHANNELS.contains(method)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户端不支持该支付方式");
        }
        try {
            if (!"EXTERNAL_ADAPTER".equals(PaymentChannelPolicy.executionMode(method))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "用户端不支持该支付方式");
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return method;
    }

    private Map<String, Object> noDiscount(BigDecimal subtotal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectionType", "NONE");
        result.put("referenceId", null);
        result.put("name", "不使用优惠");
        result.put("discountAmount", BigDecimal.ZERO.setScale(2));
        result.put("payableAmount", subtotal);
        result.put("recommended", false);
        return result;
    }

    private String orderNo(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT order_no FROM sales_order WHERE id = ?", String.class, orderId
        );
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "金额不能为空");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "金额不能小于零");
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "金额最多两位小数");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "结算快照生成失败");
        }
    }

    private String businessNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private java.time.LocalDateTime localDateTime(Object value) {
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "预约时间锁失效时间格式不正确");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能为空");
        return value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String requestHash(CheckoutCommand command) {
        return SessionTokenCodec.sha256(
            String.valueOf(command.lockToken()) + "|" + command.packageProductId() + "|"
                + command.selectionType() + "|" + command.selectionReferenceId() + "|"
                + command.paymentMethod() + "|" + command.storedValueCardId() + "|"
                + command.comboCardId() + "|"
                + command.memberNote()
        );
    }

    public record QuoteCommand(String lockToken, Long packageProductId) {
    }

    public record CheckoutCommand(
        String lockToken,
        Long packageProductId,
        String selectionType,
        Long selectionReferenceId,
        String paymentMethod,
        Long storedValueCardId,
        Long comboCardId,
        String memberNote
    ) {
    }

    private record CheckoutTarget(
        boolean booking,
        long shopId,
        long referenceId,
        Long lockId,
        String name,
        BigDecimal subtotal,
        java.time.LocalDateTime expiresAt
    ) {
    }
}
