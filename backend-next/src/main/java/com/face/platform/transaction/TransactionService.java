package com.face.platform.transaction;

import com.face.platform.api.ApiException;
import com.face.platform.inventory.InventoryService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.payment.PaymentChannelPolicy;
import com.face.platform.packageaccount.MemberAccountApplicationService;
import com.face.platform.packageaccount.PackageCatalogService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TransactionService {

    private static final List<String> ORDER_STATUSES = List.of(
        "UNPAID", "PARTIALLY_PAID", "PAID", "PARTIALLY_REFUNDED", "REFUNDED", "VOID"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final InventoryService inventoryService;
    private final OutboxEventService outboxEventService;
    private final MemberAccountApplicationService memberAccountService;
    private final PackageCatalogService packageCatalogService;

    public TransactionService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        InventoryService inventoryService,
        OutboxEventService outboxEventService,
        MemberAccountApplicationService memberAccountService,
        PackageCatalogService packageCatalogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.inventoryService = inventoryService;
        this.outboxEventService = outboxEventService;
        this.memberAccountService = memberAccountService;
        this.packageCatalogService = packageCatalogService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        String keyword,
        int page,
        int pageSize
    ) {
        List<Long> scope = tenantAccessService.accessibleShopIds(principal, "order:view");
        if (shopId != null) {
            tenantAccessService.requireShopPermission(principal, shopId, "order:view");
            scope = List.of(shopId);
        }
        if (scope.isEmpty()) {
            return pageResult(List.of(), 0L, Map.of(), Math.max(page, 1), safePageSize(pageSize));
        }

        LocalDate safeTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate safeFrom = fromDate == null ? safeTo.minusDays(30) : fromDate;
        if (safeTo.isBefore(safeFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (safeFrom.plusDays(92).isBefore(safeTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单次最多查询93天交易");
        }

        String normalizedStatus = normalizeOrderStatus(status);
        String normalizedKeyword = trimToNull(keyword);
        int safePage = Math.max(page, 1);
        int safeLimit = safePageSize(pageSize);
        List<Object> args = new ArrayList<>();
        String where = buildWhere(
            principal, scope, shopId, safeFrom, safeTo, normalizedStatus, normalizedKeyword, args
        );
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeLimit);
        listArgs.add((safePage - 1) * safeLimit);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT so.id,
                   so.order_no AS orderNo,
                   so.shop_id AS shopId,
                   sh.name AS shopName,
                   so.business_date AS businessDate,
                   so.member_id AS memberId,
                   m.member_no AS memberNo,
                   m.name AS memberName,
                   m.phone AS memberPhone,
                   so.appointment_id AS appointmentId,
                   a.appointment_no AS appointmentNo,
                   so.service_record_id AS serviceRecordId,
                   so.subtotal_amount AS subtotalAmount,
                   so.discount_amount AS discountAmount,
                   so.payable_amount AS payableAmount,
                   so.paid_amount AS paidAmount,
                   so.refunded_amount AS refundedAmount,
                   so.payment_method AS paymentMethod,
                   so.status,
                   so.version,
                   so.notes,
                   so.paid_at AS paidAt,
                   so.created_at AS createdAt,
                   (SELECT GROUP_CONCAT(soi.item_name_snapshot ORDER BY soi.id SEPARATOR '、')
                    FROM sales_order_item soi
                    WHERE soi.order_id = so.id) AS itemNames
            FROM sales_order so
            JOIN shop sh ON sh.id = so.shop_id
            LEFT JOIN member m ON m.id = so.member_id
            LEFT JOIN appointment a ON a.id = so.appointment_id
            %s
            ORDER BY so.business_date DESC, so.created_at DESC, so.id DESC
            LIMIT ? OFFSET ?
            """.formatted(where),
            listArgs.toArray()
        );
        for (Map<String, Object> record : records) {
            long orderId = number(record.get("id"));
            record.put("payments", paymentRows(orderId));
            record.put("refunds", refundRows(orderId));
        }

        long total = count(where, args);
        Map<String, Long> summary = statusSummary(
            principal, scope, shopId, safeFrom, safeTo, normalizedKeyword
        );
        return pageResult(records, total, summary, safePage, safeLimit);
    }

    public Map<String, Object> resources(TenantPrincipal principal, long shopId) {
        tenantAccessService.requireShopPermission(principal, shopId, "order:view");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("members", jdbcTemplate.queryForList(
            """
            SELECT m.id, m.member_no AS memberNo, m.name, m.phone,
                   COALESCE(MAX(CASE WHEN ma.account_type = 'BALANCE' THEN ma.balance END), 0) AS balance,
                   COALESCE(MAX(CASE WHEN ma.account_type = 'GIFT_BALANCE' THEN ma.balance END), 0) AS giftBalance,
                   COALESCE(MAX(CASE WHEN ma.account_type = 'POINTS' THEN ma.balance END), 0) AS points
            FROM member m
            JOIN member_shop_profile msp
              ON msp.member_id = m.id
             AND msp.tenant_id = m.tenant_id
             AND msp.shop_id = ?
             AND msp.status = 'ACTIVE'
            LEFT JOIN member_account ma
              ON ma.member_id = m.id
             AND ma.tenant_id = m.tenant_id
             AND ma.status = 'ACTIVE'
            WHERE m.tenant_id = ? AND m.status = 'ACTIVE'
            GROUP BY m.id, m.member_no, m.name, m.phone
            ORDER BY m.name, m.id
            """,
            shopId,
            principal.tenantId()
        ));
        result.put("appointments", jdbcTemplate.queryForList(
            """
            SELECT a.id, a.appointment_no AS appointmentNo, a.member_id AS memberId,
                   m.name AS memberName, m.phone AS memberPhone,
                   a.start_at AS startAt,
                   GROUP_CONCAT(ai.service_name_snapshot ORDER BY ai.sort_order SEPARATOR '、') AS serviceNames,
                   COALESCE(SUM(ai.price_snapshot), 0) AS totalAmount
            FROM appointment a
            JOIN member m ON m.id = a.member_id
            JOIN appointment_item ai ON ai.appointment_id = a.id
            LEFT JOIN sales_order so ON so.appointment_id = a.id
            WHERE a.tenant_id = ?
              AND a.shop_id = ?
              AND a.status = 'COMPLETED'
              AND so.id IS NULL
            GROUP BY a.id, a.appointment_no, a.member_id, m.name, m.phone, a.start_at
            ORDER BY a.start_at DESC, a.id DESC
            """,
            principal.tenantId(),
            shopId
        ));
        result.put("services", jdbcTemplate.queryForList(
            """
            SELECT id, service_code AS serviceCode, name,
                   COALESCE(member_price, list_price) AS price
            FROM service_item
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            ORDER BY name, id
            """,
            principal.tenantId(),
            shopId
        ));
        result.put("products", jdbcTemplate.queryForList(
            """
            SELECT id, sku, name, brand_name AS brandName, unit_name AS unitName,
                   sale_price AS price, stock_quantity AS stockQuantity
            FROM product
            WHERE tenant_id = ? AND shop_id = ? AND status = 'ACTIVE'
            ORDER BY name, id
            """,
            principal.tenantId(),
            shopId
        ));
        return result;
    }

    public Map<String, Object> memberAccounts(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "order:view");
        validateMember(principal, shopId, memberId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", jdbcTemplate.queryForList(
            """
            SELECT id, account_type AS accountType, currency_code AS currencyCode,
                   balance, version, status, updated_at AS updatedAt
            FROM member_account
            WHERE tenant_id = ? AND member_id = ?
            ORDER BY FIELD(account_type, 'BALANCE', 'GIFT_BALANCE', 'POINTS')
            """,
            principal.tenantId(),
            memberId
        ));
        result.put("ledger", jdbcTemplate.queryForList(
            """
            SELECT mal.id, ma.account_type AS accountType, mal.entry_type AS entryType,
                   mal.amount_delta AS amountDelta, mal.balance_after AS balanceAfter,
                   mal.reference_type AS referenceType, mal.reference_id AS referenceId,
                   mal.remark, mal.created_at AS createdAt
            FROM member_account_ledger mal
            JOIN member_account ma ON ma.id = mal.account_id
            WHERE mal.tenant_id = ? AND ma.member_id = ?
            ORDER BY mal.created_at DESC, mal.id DESC
            LIMIT 100
            """,
            principal.tenantId(),
            memberId
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> createOrder(
        TenantPrincipal principal,
        OrderCreateRequest request
    ) {
        return createOrder(principal, request, null);
    }

    @Transactional
    public Map<String, Object> createOrder(
        TenantPrincipal principal,
        OrderCreateRequest request,
        String idempotencyKey
    ) {
        return createOrder(principal, request, idempotencyKey, "order:manage");
    }

    @Transactional
    public Map<String, Object> ensureOrderForCompletedService(
        TenantPrincipal principal,
        long shopId,
        long appointmentId,
        long memberId,
        long serviceRecordId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "service_record:manage");
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT id, order_no AS orderNo, status,
                   payable_amount AS payableAmount, version
            FROM sales_order
            WHERE tenant_id = ? AND shop_id = ?
              AND (appointment_id = ? OR service_record_id = ?)
            LIMIT 1
            """,
            principal.tenantId(),
            shopId,
            appointmentId,
            serviceRecordId
        );
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        return createOrder(
            principal,
            new OrderCreateRequest(
                shopId,
                memberId,
                appointmentId,
                List.of(),
                "服务完成后自动生成待收款订单"
            ),
            "service-completion-order:" + serviceRecordId,
            "service_record:manage"
        );
    }

    private Map<String, Object> createOrder(
        TenantPrincipal principal,
        OrderCreateRequest request,
        String idempotencyKey,
        String requiredPermission
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            requiredPermission
        );
        String normalizedIdempotencyKey = trimToNull(idempotencyKey);
        String createRequestHash = normalizedIdempotencyKey == null
            ? null
            : orderCreateRequestHash(request);
        if (normalizedIdempotencyKey != null) {
            List<Map<String, Object>> replay = jdbcTemplate.queryForList(
                """
                SELECT id, order_no AS orderNo, status,
                       payable_amount AS payableAmount, version,
                       create_request_hash AS requestHash
                FROM sales_order
                WHERE tenant_id = ? AND create_idempotency_key = ?
                """,
                principal.tenantId(),
                normalizedIdempotencyKey
            );
            if (!replay.isEmpty()) {
                Map<String, Object> existing = replay.getFirst();
                if (!createRequestHash.equals(existing.get("requestHash"))) {
                    throw new ApiException(
                        HttpStatus.CONFLICT,
                        "订单创建幂等键已用于不同请求"
                    );
                }
                Map<String, Object> result = new LinkedHashMap<>(existing);
                result.remove("requestHash");
                return result;
            }
        }
        validateMember(principal, shopId, request.memberId());

        List<LineItem> items = new ArrayList<>();
        if (request.appointmentId() != null) {
            items.addAll(loadAppointmentItems(
                principal, shopId, request.memberId(), request.appointmentId()
            ));
        }
        if (request.items() != null) {
            for (OrderItemRequest item : request.items()) {
                items.add(loadRequestedItem(principal, shopId, item));
            }
        }
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少选择一个消费项目");
        }
        Long serviceRecordId = request.appointmentId() == null
            ? null
            : jdbcTemplate.query(
                """
                SELECT id
                FROM service_record
                WHERE tenant_id = ? AND shop_id = ? AND appointment_id = ?
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"),
                principal.tenantId(),
                shopId,
                request.appointmentId()
            ).stream().findFirst().orElse(null);

        BigDecimal subtotal = items.stream()
            .map(LineItem::grossAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = items.stream()
            .map(LineItem::discountAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal payable = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (payable.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "订单应付金额必须大于0");
        }

        String orderNo = createBusinessNo("SO");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO sales_order (
                    tenant_id, shop_id, order_no,
                    create_idempotency_key, create_request_hash,
                    business_date, member_id,
                    appointment_id, service_record_id,
                    subtotal_amount, discount_amount, payable_amount,
                    paid_amount, refunded_amount, currency_code, status, version,
                    notes, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0,
                          'CNY', 'UNPAID', 0, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, orderNo);
            statement.setString(4, normalizedIdempotencyKey);
            statement.setString(5, createRequestHash);
            statement.setObject(6, LocalDate.now());
            statement.setLong(7, request.memberId());
            if (request.appointmentId() == null) {
                statement.setNull(8, java.sql.Types.BIGINT);
            } else {
                statement.setLong(8, request.appointmentId());
            }
            if (serviceRecordId == null) {
                statement.setNull(9, java.sql.Types.BIGINT);
            } else {
                statement.setLong(9, serviceRecordId);
            }
            statement.setBigDecimal(10, subtotal);
            statement.setBigDecimal(11, discount);
            statement.setBigDecimal(12, payable);
            statement.setString(13, trimToNull(request.notes()));
            statement.setLong(14, principal.accountId());
            statement.setLong(15, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "订单编号生成失败");
        }
        long orderId = key.longValue();
        for (LineItem item : items) {
            jdbcTemplate.update(
                """
                INSERT INTO sales_order_item (
                    order_id, item_type, service_id, product_id, package_product_id,
                    item_name_snapshot,
                    category_id_snapshot, category_name_snapshot, brand_name_snapshot,
                    dimension_snapshot_quality,
                    quantity, unit_price, discount_amount, line_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                item.itemType(),
                item.serviceId(),
                item.productId(),
                item.packageProductId(),
                item.name(),
                item.categoryId(),
                item.categoryName(),
                item.brandName(),
                item.dimensionSnapshotQuality(),
                item.quantity(),
                item.unitPrice(),
                item.discountAmount(),
                item.lineAmount()
            );
        }
        audit(
            principal, shopId, "ORDER_CREATE", "SALES_ORDER", orderId,
            orderNo, payable, null
        );
        return Map.of(
            "id", orderId,
            "orderNo", orderNo,
            "status", "UNPAID",
            "payableAmount", payable,
            "version", 0
        );
    }

    @Transactional
    public Map<String, Object> pay(
        TenantPrincipal principal,
        long orderId,
        PaymentRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "order:manage"
        );
        String idempotencyKey = request.idempotencyKey().trim();
        String requestHash = paymentRequestHash(orderId, request);
        Map<String, Object> existing = findPayment(principal, idempotencyKey);
        if (existing != null) {
            if (number(existing.get("orderId")) != orderId) {
                throw new ApiException(HttpStatus.CONFLICT, "收款幂等键已用于其他订单");
            }
            Object existingHash = existing.remove("requestHash");
            if (existingHash != null && !requestHash.equals(existingHash.toString())) {
                throw new ApiException(HttpStatus.CONFLICT, "收款幂等键已用于不同请求");
            }
            return existing;
        }

        Map<String, Object> order = lockOrder(principal, shopId, orderId);
        requireVersion(order, request.version(), "订单");
        String status = order.get("status").toString();
        if (!TransactionStatusPolicy.canAcceptPayment(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "当前订单状态不能收款");
        }
        BigDecimal payable = decimal(order.get("payableAmount"));
        BigDecimal paid = decimal(order.get("paidAmount"));
        BigDecimal amount = money(request.amount());
        BigDecimal outstanding = payable.subtract(paid);
        if (amount.compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收款金额不能超过剩余应付金额");
        }

        String method = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        final String executionMode;
        try {
            executionMode = PaymentChannelPolicy.executionMode(method);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if ("EXTERNAL_ADAPTER".equals(executionMode)) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "外部支付必须使用 V3 支付适配器，未改变订单或支付状态"
            );
        }
        if ("BALANCE".equals(method)) {
            memberAccountService.debitBalanceForOrder(
                principal,
                shopId,
                number(order.get("memberId")),
                amount,
                idempotencyKey,
                requestHash,
                orderId
            );
        }

        String paymentNo = createBusinessNo("PAY");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO payment_transaction (
                    tenant_id, shop_id, order_id, payment_no, payment_method,
                    amount, refunded_amount, currency_code, external_transaction_no,
                    idempotency_key, request_hash, status, paid_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, 0, 'CNY', ?, ?, ?, 'SUCCESS',
                          CURRENT_TIMESTAMP(3), ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, orderId);
            statement.setString(4, paymentNo);
            statement.setString(5, method);
            statement.setBigDecimal(6, amount);
            statement.setString(7, trimToNull(request.externalTransactionNo()));
            statement.setString(8, idempotencyKey);
            statement.setString(9, requestHash);
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "收款流水生成失败");
        }
        long paymentId = key.longValue();
        BigDecimal newPaid = paid.add(amount);
        String newStatus = TransactionStatusPolicy.paymentStatus(payable, newPaid);
        String currentMethod = order.get("paymentMethod") == null
            ? null
            : order.get("paymentMethod").toString();
        String orderMethod = currentMethod == null || currentMethod.equals(method)
            ? method
            : "MIXED";
        int changed = jdbcTemplate.update(
            """
            UPDATE sales_order
            SET paid_amount = ?, payment_method = ?, status = ?,
                paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP(3) ELSE paid_at END,
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            newPaid,
            orderMethod,
            newStatus,
            newStatus,
            principal.accountId(),
            orderId,
            principal.tenantId(),
            shopId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "订单已被其他人修改，请刷新后重试");
        }
        if ("PAID".equals(newStatus)) {
            inventoryService.deductPaidOrder(principal, shopId, orderId);
        }
        audit(
            principal, shopId, "ORDER_PAYMENT_SUCCESS", "PAYMENT_TRANSACTION",
            paymentId, paymentNo, amount, method
        );
        outboxEventService.append(
            principal,
            shopId,
            "PAYMENT_TRANSACTION",
            String.valueOf(paymentId),
            "PaymentSucceeded",
            Map.of("paymentId", paymentId, "orderId", orderId)
        );
        return paymentResult(paymentId);
    }

    @Transactional
    public Map<String, Object> createRefund(
        TenantPrincipal principal,
        long orderId,
        RefundCreateRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "order:manage"
        );
        String idempotencyKey = request.idempotencyKey().trim();
        Map<String, Object> existing = findRefund(principal, idempotencyKey);
        if (existing != null) {
            if (number(existing.get("orderId")) != orderId) {
                throw new ApiException(HttpStatus.CONFLICT, "退款幂等键已用于其他订单");
            }
            return existing;
        }

        Map<String, Object> payment = lockPayment(
            principal, shopId, orderId, request.paymentId()
        );
        if (!"SUCCESS".equals(payment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有成功收款可以申请退款");
        }
        BigDecimal amount = money(request.amount());
        BigDecimal reserved = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM refund_transaction
            WHERE tenant_id = ? AND payment_id = ?
              AND status IN ('PENDING', 'APPROVED', 'SUCCESS')
            """,
            BigDecimal.class,
            principal.tenantId(),
            request.paymentId()
        );
        BigDecimal refundable = decimal(payment.get("amount")).subtract(
            reserved == null ? BigDecimal.ZERO : reserved
        );
        if (amount.compareTo(refundable) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "退款金额超过该收款的可退余额");
        }

        String refundNo = createBusinessNo("RF");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO refund_transaction (
                    tenant_id, shop_id, order_id, payment_id, refund_no,
                    amount, reason, idempotency_key, status, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, orderId);
            statement.setLong(4, request.paymentId());
            statement.setString(5, refundNo);
            statement.setBigDecimal(6, amount);
            statement.setString(7, request.reason().trim());
            statement.setString(8, idempotencyKey);
            statement.setLong(9, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "退款流水生成失败");
        }
        long refundId = key.longValue();
        audit(
            principal, shopId, "REFUND_REQUEST", "REFUND_TRANSACTION",
            refundId, refundNo, amount, request.reason().trim()
        );
        return refundResult(refundId);
    }

    @Transactional
    public Map<String, Object> decideRefund(
        TenantPrincipal principal,
        long refundId,
        RefundDecisionRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "refund:approve"
        );
        Map<String, Object> refund = lockRefund(principal, shopId, refundId);
        requireVersion(refund, request.version(), "退款申请");
        if (!"PENDING".equals(refund.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "该退款申请已经处理");
        }

        String action = request.action().trim().toUpperCase(Locale.ROOT);
        String decisionNote = trimToNull(request.decisionNote());
        if ("REJECT".equals(action)) {
            if (decisionNote == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "拒绝退款必须填写审核说明");
            }
            int changed = jdbcTemplate.update(
                """
                UPDATE refund_transaction
                SET status = 'REJECTED', decision_note = ?, approved_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'PENDING'
                """,
                decisionNote,
                principal.accountId(),
                refundId,
                principal.tenantId(),
                shopId,
                request.version()
            );
            if (changed != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "退款申请已被其他人处理");
            }
            audit(
                principal, shopId, "REFUND_REJECT", "REFUND_TRANSACTION",
                refundId, refund.get("refundNo").toString(),
                decimal(refund.get("amount")), decisionNote
            );
            return refundResult(refundId);
        }

        long orderId = number(refund.get("orderId"));
        long paymentId = number(refund.get("paymentId"));
        Map<String, Object> payment = lockPayment(principal, shopId, orderId, paymentId);
        Map<String, Object> order = lockOrder(principal, shopId, orderId);
        BigDecimal refundAmount = decimal(refund.get("amount"));
        BigDecimal paymentRefundable = decimal(payment.get("amount"))
            .subtract(decimal(payment.get("refundedAmount")));
        if (refundAmount.compareTo(paymentRefundable) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "原收款可退金额已发生变化");
        }

        if ("BALANCE".equals(payment.get("paymentMethod"))) {
            memberAccountService.creditBalanceForRefund(
                principal,
                shopId,
                number(order.get("memberId")),
                refundAmount,
                refundId,
                orderId
            );
        }
        jdbcTemplate.update(
            """
            UPDATE payment_transaction
            SET refunded_amount = refunded_amount + ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            """,
            refundAmount,
            paymentId,
            principal.tenantId(),
            shopId
        );
        BigDecimal newRefunded = decimal(order.get("refundedAmount")).add(refundAmount);
        BigDecimal paidAmount = decimal(order.get("paidAmount"));
        String orderStatus = TransactionStatusPolicy.refundStatus(paidAmount, newRefunded);
        int orderChanged = jdbcTemplate.update(
            """
            UPDATE sales_order
            SET refunded_amount = ?, status = ?, version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            newRefunded,
            orderStatus,
            principal.accountId(),
            orderId,
            principal.tenantId(),
            shopId,
            number(order.get("version"))
        );
        if (orderChanged != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "订单已被其他退款修改，请刷新后重试");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE refund_transaction
            SET status = 'SUCCESS', decision_note = ?, approved_by = ?,
                reviewed_at = CURRENT_TIMESTAMP(3), refunded_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'PENDING'
            """,
            decisionNote,
            principal.accountId(),
            refundId,
            principal.tenantId(),
            shopId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "退款申请已被其他人处理");
        }
        audit(
            principal, shopId, "REFUND_SUCCESS", "REFUND_TRANSACTION",
            refundId, refund.get("refundNo").toString(), refundAmount, decisionNote
        );
        return refundResult(refundId);
    }

    @Transactional
    public Map<String, Object> voidOrder(
        TenantPrincipal principal,
        long orderId,
        OrderVoidRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "order:manage"
        );
        Map<String, Object> order = lockOrder(principal, shopId, orderId);
        requireVersion(order, request.version(), "订单");
        if (!"UNPAID".equals(order.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有未收款订单可以作废");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE sales_order
            SET status = 'VOID', notes = CONCAT_WS('；', notes, ?),
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ?
            """,
            trimToNull(request.reason()) == null ? "订单作废" : "作废：" + request.reason().trim(),
            principal.accountId(),
            orderId,
            principal.tenantId(),
            shopId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "订单已被其他人修改，请刷新后重试");
        }
        audit(
            principal, shopId, "ORDER_VOID", "SALES_ORDER", orderId,
            order.get("orderNo").toString(), decimal(order.get("payableAmount")),
            trimToNull(request.reason())
        );
        return Map.of("id", orderId, "status", "VOID", "version", request.version() + 1);
    }

    private String buildWhere(
        TenantPrincipal principal,
        List<Long> scope,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        String keyword,
        List<Object> args
    ) {
        StringBuilder where = new StringBuilder(
            "WHERE so.tenant_id = ? AND so.business_date >= ? AND so.business_date <= ?"
        );
        args.add(principal.tenantId());
        args.add(fromDate);
        args.add(toDate);
        if (scope.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号没有该功能的可管理门店");
        }
        where.append(" AND so.shop_id IN (").append(placeholders(scope.size())).append(")");
        args.addAll(scope);
        if (status != null) {
            where.append(" AND so.status = ?");
            args.add(status);
        }
        if (keyword != null) {
            where.append(
                " AND (so.order_no LIKE ? OR m.name LIKE ? OR m.phone LIKE ? OR a.appointment_no LIKE ?)"
            );
            String pattern = "%" + keyword + "%";
            Collections.addAll(args, pattern, pattern, pattern, pattern);
        }
        return where.toString();
    }

    private long count(String where, List<Object> args) {
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM sales_order so
            LEFT JOIN member m ON m.id = so.member_id
            LEFT JOIN appointment a ON a.id = so.appointment_id
            %s
            """.formatted(where),
            Long.class,
            args.toArray()
        );
        return total == null ? 0L : total;
    }

    private Map<String, Long> statusSummary(
        TenantPrincipal principal,
        List<Long> scope,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String keyword
    ) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(
            principal, scope, shopId, fromDate, toDate, null, keyword, args
        );
        Map<String, Long> summary = new LinkedHashMap<>();
        for (String value : ORDER_STATUSES) summary.put(value, 0L);
        jdbcTemplate.queryForList(
            """
            SELECT so.status, COUNT(*) AS total
            FROM sales_order so
            LEFT JOIN member m ON m.id = so.member_id
            LEFT JOIN appointment a ON a.id = so.appointment_id
            %s
            GROUP BY so.status
            """.formatted(where),
            args.toArray()
        ).forEach(row -> summary.put(
            row.get("status").toString(),
            number(row.get("total"))
        ));
        return summary;
    }

    private List<Map<String, Object>> paymentRows(long orderId) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, payment_no AS paymentNo, payment_method AS paymentMethod,
                   amount, refunded_amount AS refundedAmount,
                   external_transaction_no AS externalTransactionNo,
                   status, paid_at AS paidAt
            FROM payment_transaction
            WHERE order_id = ?
            ORDER BY paid_at, id
            """,
            orderId
        );
    }

    private List<Map<String, Object>> refundRows(long orderId) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, payment_id AS paymentId, refund_no AS refundNo,
                   amount, reason, decision_note AS decisionNote,
                   status, version, created_by AS createdBy,
                   approved_by AS approvedBy, executed_by AS executedBy,
                   execution_mode AS executionMode,
                   channel_status AS channelStatus, failure_code AS failureCode,
                   reviewed_at AS reviewedAt,
                   refunded_at AS refundedAt, created_at AS createdAt
            FROM refund_transaction
            WHERE order_id = ?
            ORDER BY created_at DESC, id DESC
            """,
            orderId
        );
    }

    private List<LineItem> loadAppointmentItems(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long appointmentId
    ) {
        List<Map<String, Object>> appointments = jdbcTemplate.queryForList(
            """
            SELECT id, status
            FROM appointment
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND member_id = ?
            FOR UPDATE
            """,
            appointmentId,
            principal.tenantId(),
            shopId,
            memberId
        );
        if (appointments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "预约不存在或不属于该会员");
        }
        if (!"COMPLETED".equals(appointments.getFirst().get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已完成服务的预约可以结算");
        }
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_order WHERE tenant_id = ? AND appointment_id = ?",
            Integer.class,
            principal.tenantId(),
            appointmentId
        );
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该预约已经生成订单");
        }
        return jdbcTemplate.queryForList(
            """
            SELECT ai.service_id, ai.service_name_snapshot, ai.price_snapshot,
                   si.category_id, sc.name AS category_name
            FROM appointment_item ai
            LEFT JOIN service_item si ON si.id = ai.service_id
            LEFT JOIN service_category sc ON sc.id = si.category_id
            WHERE ai.appointment_id = ?
            ORDER BY ai.sort_order, ai.id
            """,
            appointmentId
        ).stream().map(row -> {
            BigDecimal price = money(decimal(row.get("price_snapshot")));
            return new LineItem(
                "SERVICE",
                number(row.get("service_id")),
                null,
                null,
                row.get("service_name_snapshot").toString(),
                nullableNumber(row.get("category_id")),
                row.get("category_name") == null ? null : row.get("category_name").toString(),
                null,
                row.get("category_id") == null ? "MISSING" : "TRANSACTION_TIME",
                BigDecimal.ONE.setScale(3),
                price,
                BigDecimal.ZERO.setScale(2),
                price
            );
        }).toList();
    }

    private LineItem loadRequestedItem(
        TenantPrincipal principal,
        long shopId,
        OrderItemRequest request
    ) {
        String itemType = request.itemType().trim().toUpperCase(Locale.ROOT);
        BigDecimal quantity = request.quantity().setScale(3, RoundingMode.HALF_UP);
        BigDecimal discount = request.discountAmount() == null
            ? BigDecimal.ZERO.setScale(2)
            : money(request.discountAmount());
        Map<String, Object> row;
        if ("SERVICE".equals(itemType)) {
            row = singleRow(
                """
                SELECT si.id, si.name, COALESCE(si.member_price, si.list_price) AS price,
                       si.category_id, sc.name AS category_name,
                       NULL AS brand_name
                FROM service_item si
                JOIN service_category sc
                  ON sc.id = si.category_id
                 AND sc.tenant_id = si.tenant_id
                WHERE si.id = ? AND si.tenant_id = ? AND si.shop_id = ?
                  AND si.status = 'ACTIVE'
                """,
                "美容项目不存在或已下架",
                request.referenceId(),
                principal.tenantId(),
                shopId
            );
        } else if ("PRODUCT".equals(itemType)) {
            row = singleRow(
                """
                SELECT p.id, p.name, p.sale_price AS price,
                       p.category_id, pc.name AS category_name,
                       p.brand_name
                FROM product p
                JOIN product_category pc
                  ON pc.id = p.category_id
                 AND pc.tenant_id = p.tenant_id
                WHERE p.id = ? AND p.tenant_id = ? AND p.shop_id = ?
                  AND p.status = 'ACTIVE'
                """,
                "零售产品不存在或已下架",
                request.referenceId(),
                principal.tenantId(),
                shopId
            );
        } else {
            row = new LinkedHashMap<>(
                packageCatalogService.orderLine(
                    principal,
                    shopId,
                    request.referenceId()
                )
            );
            row.put("category_id", null);
            row.put("category_name", null);
            row.put("brand_name", null);
        }
        BigDecimal unitPrice = money(decimal(row.get("price")));
        BigDecimal gross = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(gross) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单项优惠不能超过该项金额");
        }
        return new LineItem(
            itemType,
            "SERVICE".equals(itemType) ? number(row.get("id")) : null,
            "PRODUCT".equals(itemType) ? number(row.get("id")) : null,
            "PACKAGE".equals(itemType) ? number(row.get("id")) : null,
            row.get("name").toString(),
            nullableNumber(row.get("category_id")),
            row.get("category_name") == null ? null : row.get("category_name").toString(),
            row.get("brand_name") == null ? null : row.get("brand_name").toString(),
            "TRANSACTION_TIME",
            quantity,
            unitPrice,
            discount,
            gross.subtract(discount)
        );
    }

    private void validateMember(TenantPrincipal principal, long shopId, long memberId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM member m
            JOIN member_shop_profile msp
              ON msp.member_id = m.id
             AND msp.tenant_id = m.tenant_id
            WHERE m.id = ? AND m.tenant_id = ? AND m.status = 'ACTIVE'
              AND msp.shop_id = ? AND msp.status = 'ACTIVE'
            """,
            Integer.class,
            memberId,
            principal.tenantId(),
            shopId
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在或未在该门店启用");
        }
    }

    private Map<String, Object> lockOrder(
        TenantPrincipal principal,
        long shopId,
        long orderId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_no AS orderNo, member_id AS memberId,
                   payable_amount AS payableAmount, paid_amount AS paidAmount,
                   refunded_amount AS refundedAmount, payment_method AS paymentMethod,
                   status, version
            FROM sales_order
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            orderId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockPayment(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        long paymentId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_no AS paymentNo,
                   payment_method AS paymentMethod, amount,
                   refunded_amount AS refundedAmount, status
            FROM payment_transaction
            WHERE id = ? AND order_id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            paymentId,
            orderId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "原收款记录不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockRefund(
        TenantPrincipal principal,
        long shopId,
        long refundId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_id AS paymentId,
                   refund_no AS refundNo, amount, status, version
            FROM refund_transaction
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            refundId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "退款申请不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> findPayment(
        TenantPrincipal principal,
        String idempotencyKey
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_no AS paymentNo,
                   payment_method AS paymentMethod, amount,
                   refunded_amount AS refundedAmount, request_hash AS requestHash,
                   status, paid_at AS paidAt
            FROM payment_transaction
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String orderCreateRequestHash(OrderCreateRequest request) {
        return SessionTokenCodec.sha256(
            request.shopId() + "|"
                + request.memberId() + "|"
                + String.valueOf(request.appointmentId()) + "|"
                + String.valueOf(request.items()) + "|"
                + String.valueOf(request.notes())
        );
    }

    private String paymentRequestHash(long orderId, PaymentRequest request) {
        return SessionTokenCodec.sha256(
            orderId + "|"
                + request.shopId() + "|"
                + request.paymentMethod().trim().toUpperCase(Locale.ROOT) + "|"
                + money(request.amount()).toPlainString() + "|"
                + request.version() + "|"
                + String.valueOf(trimToNull(request.externalTransactionNo()))
        );
    }

    private Map<String, Object> findRefund(
        TenantPrincipal principal,
        String idempotencyKey
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, order_id AS orderId, payment_id AS paymentId,
                   refund_no AS refundNo, amount, reason,
                   decision_note AS decisionNote, status, version,
                   reviewed_at AS reviewedAt, refunded_at AS refundedAt
            FROM refund_transaction
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> paymentResult(long paymentId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT id, order_id AS orderId, payment_no AS paymentNo,
                       payment_method AS paymentMethod, amount,
                       refunded_amount AS refundedAmount, status, paid_at AS paidAt
                FROM payment_transaction
                WHERE id = ?
                """,
                paymentId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "收款记录不存在");
        }
    }

    private Map<String, Object> refundResult(long refundId) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT id, order_id AS orderId, payment_id AS paymentId,
                       refund_no AS refundNo, amount, reason,
                       decision_note AS decisionNote, status, version,
                       reviewed_at AS reviewedAt, refunded_at AS refundedAt
                FROM refund_transaction
                WHERE id = ?
                """,
                refundId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "退款申请不存在");
        }
    }

    private Map<String, Object> singleRow(
        String sql,
        String notFoundMessage,
        Object... args
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return rows.getFirst();
    }

    private void requireVersion(
        Map<String, Object> row,
        int expectedVersion,
        String entityName
    ) {
        if (number(row.get("version")) != expectedVersion) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                entityName + "已被其他人修改，请刷新后重试"
            );
        }
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId,
        String businessNo,
        BigDecimal amount,
        String note
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id,
                after_data, metadata
            ) VALUES (?, ?, ?, ?, ?, ?,
                      JSON_OBJECT('businessNo', ?, 'amount', ?),
                      JSON_OBJECT('note', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityType,
            entityId,
            businessNo,
            amount,
            note
        );
    }

    private Map<String, Object> pageResult(
        List<Map<String, Object>> records,
        long total,
        Map<String, Long> summary,
        int page,
        int pageSize
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("summary", summary);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return result;
    }

    private String normalizeOrderStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ORDER_STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的订单状态");
        }
        return normalized;
    }

    private int safePageSize(int value) {
        return Math.max(1, Math.min(value, 100));
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String createBusinessNo(String prefix) {
        return prefix
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("交易数据不完整");
        }
        return number.longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record LineItem(
        String itemType,
        Long serviceId,
        Long productId,
        Long packageProductId,
        String name,
        Long categoryId,
        String categoryName,
        String brandName,
        String dimensionSnapshotQuality,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal lineAmount
    ) {
        BigDecimal grossAmount() {
            return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
