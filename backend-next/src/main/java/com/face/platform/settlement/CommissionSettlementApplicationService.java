package com.face.platform.settlement;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CommissionSettlementApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;

    public CommissionSettlementApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        long shopId,
        String status,
        int page,
        int pageSize
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:view");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String normalizedStatus = optionalStatus(status);
        List<Object> args = new ArrayList<>(List.of(principal.tenantId(), shopId));
        String statusClause = "";
        if (normalizedStatus != null) {
            statusClause = " AND status = ?";
            args.add(normalizedStatus);
        }
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT id, settlement_no AS settlementNo, period_start AS periodStart,
                   period_end AS periodEnd, currency_code AS currencyCode,
                   status, item_count AS itemCount, total_amount AS totalAmount,
                   version, created_by AS createdBy, calculated_by AS calculatedBy,
                   confirmed_by AS confirmedBy, paid_at AS paidAt,
                   closed_at AS closedAt, created_at AS createdAt
            FROM commission_settlement_batch
            WHERE tenant_id = ? AND shop_id = ?%s
            ORDER BY id DESC LIMIT ? OFFSET ?
            """.formatted(statusClause),
            args.toArray()
        );
        records.forEach(this::moneyFields);
        return Map.of("records", records, "page", safePage, "pageSize", safeSize);
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long batchId
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:view");
        Map<String, Object> batch = requireBatch(principal, shopId, batchId, false);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            """
            SELECT si.id, si.commission_entry_id AS commissionEntryId,
                   si.staff_id AS staffId, s.name AS staffName,
                   si.settlement_amount AS settlementAmount,
                   si.entry_occurred_at AS entryOccurredAt,
                   si.active_flag AS active,
                   ce.entry_no AS commissionEntryNo, ce.entry_type AS entryType
            FROM commission_settlement_item si
            JOIN commission_entry ce
              ON ce.id = si.commission_entry_id AND ce.tenant_id = si.tenant_id
            JOIN staff s ON s.id = si.staff_id AND s.tenant_id = si.tenant_id
            WHERE si.tenant_id = ? AND si.shop_id = ? AND si.batch_id = ?
            ORDER BY si.staff_id, si.id
            """,
            principal.tenantId(),
            shopId,
            batchId
        );
        items.forEach(this::moneyFields);
        Map<String, Object> result = new LinkedHashMap<>(batch);
        result.put("items", items);
        return result;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        long shopId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currencyCode,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:manage");
        if (!CommissionSettlementPolicy.validPeriod(periodStart, periodEnd)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结算周期不正确");
        }
        String currency = requiredCurrency(currencyCode);
        String settlementNo = "CS" + System.currentTimeMillis()
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO commission_settlement_batch (
                    tenant_id, shop_id, settlement_no, period_start, period_end,
                    currency_code, create_idempotency_key, create_request_hash,
                    status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, settlementNo);
            statement.setObject(4, periodStart);
            statement.setObject(5, periodEnd);
            statement.setString(6, currency);
            statement.setString(7, idempotencyKey);
            statement.setString(8, requestHash);
            statement.setLong(9, principal.accountId());
            return statement;
        }, holder);
        long batchId = holder.getKey().longValue();
        audit(principal, shopId, batchId, "COMMISSION_SETTLEMENT_CREATED");
        outboxEventService.append(
            principal, shopId, "COMMISSION_SETTLEMENT", Long.toString(batchId),
            "CommissionSettlementCreated", Map.of("batchId", batchId)
        );
        return detail(principal, shopId, batchId);
    }

    public Map<String, Object> detailByCreateKey(
        TenantPrincipal principal,
        long shopId,
        String idempotencyKey
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:manage");
        List<Long> ids = jdbcTemplate.queryForList(
            """
            SELECT id FROM commission_settlement_batch
            WHERE tenant_id = ? AND shop_id = ? AND create_idempotency_key = ?
            """,
            Long.class,
            principal.tenantId(), shopId, idempotencyKey
        );
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "幂等结算批次结果暂不可用");
        }
        return detail(principal, shopId, ids.getFirst());
    }

    @Transactional
    public Map<String, Object> calculate(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:manage");
        Map<String, Object> batch = requireBatch(principal, shopId, batchId, true);
        requireVersionAndTransition(batch, version, "CALCULATED");
        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
            """
            SELECT ce.id, ce.staff_id AS staffId, ce.amount, ce.created_at AS occurredAt
            FROM commission_entry ce
            WHERE ce.tenant_id = ? AND ce.shop_id = ?
              AND ce.status = 'FROZEN'
              AND ce.created_at >= ?
              AND ce.created_at < DATE_ADD(?, INTERVAL 1 DAY)
              AND NOT EXISTS (
                SELECT 1 FROM commission_settlement_item si
                WHERE si.tenant_id = ce.tenant_id
                  AND si.commission_entry_id = ce.id
                  AND si.active_flag = 1
              )
            ORDER BY ce.staff_id, ce.id
            FOR UPDATE
            """,
            principal.tenantId(),
            shopId,
            batch.get("periodStart"),
            batch.get("periodEnd")
        );
        if (entries.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "结算周期内没有可结算的冻结提成");
        }
        BigDecimal total = BigDecimal.ZERO;
        try {
            for (Map<String, Object> entry : entries) {
                BigDecimal amount = decimal(entry.get("amount"));
                jdbcTemplate.update(
                    """
                    INSERT INTO commission_settlement_item (
                        tenant_id, shop_id, batch_id, commission_entry_id,
                        staff_id, settlement_amount, entry_occurred_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    principal.tenantId(), shopId, batchId, number(entry.get("id")),
                    number(entry.get("staffId")), amount, entry.get("occurredAt")
                );
                total = total.add(amount);
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "提成流水已被其他有效结算批次占用");
        }
        int updated = jdbcTemplate.update(
            """
            UPDATE commission_settlement_batch
            SET status = 'CALCULATED', item_count = ?, total_amount = ?,
                calculated_by = ?, calculated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'DRAFT' AND version = ?
            """,
            entries.size(), total, principal.accountId(), batchId,
            principal.tenantId(), shopId, version
        );
        if (updated != 1) throw versionConflict();
        audit(principal, shopId, batchId, "COMMISSION_SETTLEMENT_CALCULATED");
        return detail(principal, shopId, batchId);
    }

    @Transactional
    public Map<String, Object> confirm(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:approve");
        Map<String, Object> batch = requireBatch(principal, shopId, batchId, true);
        requireVersionAndTransition(batch, version, "CONFIRMED");
        if (!CommissionSettlementPolicy.canApprove(
            principal.accountId(),
            number(batch.get("createdBy")),
            nullableNumber(batch.get("calculatedBy"))
        )) {
            throw new ApiException(HttpStatus.FORBIDDEN, "创建或计算批次的账号不能确认本人批次");
        }
        List<Map<String, Object>> items = activeEntries(principal, shopId, batchId);
        if (items.isEmpty() || items.stream().anyMatch(row -> !"FROZEN".equals(row.get("status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "结算明细已变化，请整批作废后重新创建");
        }
        for (Map<String, Object> item : items) {
            long entryId = number(item.get("entryId"));
            int entryVersion = ((Number) item.get("entryVersion")).intValue();
            int updated = jdbcTemplate.update(
                """
                UPDATE commission_entry
                SET status = 'SETTLED', version = version + 1
                WHERE id = ? AND tenant_id = ? AND shop_id = ?
                  AND status = 'FROZEN' AND version = ?
                """,
                entryId, principal.tenantId(), shopId, entryVersion
            );
            if (updated != 1) throw versionConflict();
            history(
                principal, shopId, entryId, "SETTLED", "FROZEN", "SETTLED",
                "结算批次确认", idempotencyKey + ":" + entryId, requestHash
            );
        }
        transitionBatch(principal, shopId, batchId, version, "CALCULATED", "CONFIRMED",
            "confirmed_by", "confirmed_at", null);
        audit(principal, shopId, batchId, "COMMISSION_SETTLEMENT_CONFIRMED");
        outboxEventService.append(
            principal, shopId, "COMMISSION_SETTLEMENT", Long.toString(batchId),
            "CommissionSettlementConfirmed", Map.of("batchId", batchId)
        );
        return detail(principal, shopId, batchId);
    }

    @Transactional
    public Map<String, Object> markPaid(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version,
        String paymentReference
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:approve");
        Map<String, Object> batch = requireBatch(principal, shopId, batchId, true);
        requireVersionAndTransition(batch, version, "PAID");
        String reference = required(paymentReference, "支付参考", 100);
        transitionBatch(principal, shopId, batchId, version, "CONFIRMED", "PAID",
            "paid_by", "paid_at", reference);
        audit(principal, shopId, batchId, "COMMISSION_SETTLEMENT_PAID");
        outboxEventService.append(
            principal, shopId, "COMMISSION_SETTLEMENT", Long.toString(batchId),
            "CommissionSettlementPaid", Map.of("batchId", batchId)
        );
        return detail(principal, shopId, batchId);
    }

    @Transactional
    public Map<String, Object> close(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:approve");
        Map<String, Object> batch = requireBatch(principal, shopId, batchId, true);
        requireVersionAndTransition(batch, version, "CLOSED");
        transitionBatch(principal, shopId, batchId, version, "PAID", "CLOSED",
            "closed_by", "closed_at", null);
        audit(principal, shopId, batchId, "COMMISSION_SETTLEMENT_CLOSED");
        return detail(principal, shopId, batchId);
    }

    @Transactional
    public Map<String, Object> voidBatch(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version,
        String reason,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "commission:settlement:approve");
        Map<String, Object> batch = requireBatch(principal, shopId, batchId, true);
        String from = batch.get("status").toString();
        requireVersionAndTransition(batch, version, "VOIDED");
        String safeReason = required(reason, "作废原因", 500);
        if ("CONFIRMED".equals(from)) {
            for (Map<String, Object> item : activeEntries(principal, shopId, batchId)) {
                long entryId = number(item.get("entryId"));
                int updated = jdbcTemplate.update(
                    """
                    UPDATE commission_entry
                    SET status = 'FROZEN', version = version + 1
                    WHERE id = ? AND tenant_id = ? AND shop_id = ?
                      AND status = 'SETTLED'
                    """,
                    entryId, principal.tenantId(), shopId
                );
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "结算流水状态已变化，不能作废批次");
                }
                history(
                    principal, shopId, entryId, "SETTLEMENT_VOIDED", "SETTLED", "FROZEN",
                    safeReason, idempotencyKey + ":" + entryId, requestHash
                );
            }
        }
        jdbcTemplate.update(
            """
            UPDATE commission_settlement_item
            SET active_flag = 0
            WHERE tenant_id = ? AND shop_id = ? AND batch_id = ? AND active_flag = 1
            """,
            principal.tenantId(), shopId, batchId
        );
        int updated = jdbcTemplate.update(
            """
            UPDATE commission_settlement_batch
            SET status = 'VOIDED', voided_by = ?, voided_at = CURRENT_TIMESTAMP(3),
                void_reason = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = ? AND version = ?
            """,
            principal.accountId(), safeReason, batchId, principal.tenantId(),
            shopId, from, version
        );
        if (updated != 1) throw versionConflict();
        audit(principal, shopId, batchId, "COMMISSION_SETTLEMENT_VOIDED");
        return detail(principal, shopId, batchId);
    }

    public Map<String, Object> technicianSummary(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "commission:entry:view");
        long staffId = accessService.requireStaffId(principal);
        Map<String, Object> totals = jdbcTemplate.queryForMap(
            """
            SELECT
              COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END), 0) AS pendingAmount,
              COALESCE(SUM(CASE WHEN status = 'FROZEN' THEN amount ELSE 0 END), 0) AS frozenAmount,
              COALESCE(SUM(CASE WHEN status = 'SETTLED' THEN amount ELSE 0 END), 0) AS settledAmount
            FROM commission_entry
            WHERE tenant_id = ? AND shop_id = ? AND staff_id = ?
            """,
            principal.tenantId(), shopId, staffId
        );
        BigDecimal paid = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(si.settlement_amount), 0)
            FROM commission_settlement_item si
            JOIN commission_settlement_batch sb
              ON sb.id = si.batch_id AND sb.tenant_id = si.tenant_id
            WHERE si.tenant_id = ? AND si.shop_id = ? AND si.staff_id = ?
              AND si.active_flag = 1 AND sb.status IN ('PAID', 'CLOSED')
            """,
            BigDecimal.class,
            principal.tenantId(), shopId, staffId
        );
        totals = new LinkedHashMap<>(totals);
        totals.put("staffId", staffId);
        totals.put("paidAmount", paid == null ? "0.00" : paid.setScale(2).toPlainString());
        moneyFields(totals);
        return totals;
    }

    private Map<String, Object> requireBatch(
        TenantPrincipal principal, long shopId, long batchId, boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, settlement_no AS settlementNo, period_start AS periodStart,
                   period_end AS periodEnd, currency_code AS currencyCode,
                   status, item_count AS itemCount, total_amount AS totalAmount,
                   version, created_by AS createdBy, calculated_by AS calculatedBy,
                   confirmed_by AS confirmedBy, paid_by AS paidBy,
                   payment_reference AS paymentReference, paid_at AS paidAt,
                   closed_at AS closedAt, void_reason AS voidReason,
                   created_at AS createdAt
            FROM commission_settlement_batch
            WHERE id = ? AND tenant_id = ? AND shop_id = ?%s
            """.formatted(lock ? " FOR UPDATE" : ""),
            batchId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提成结算批次不存在");
        Map<String, Object> batch = new LinkedHashMap<>(rows.getFirst());
        moneyFields(batch);
        return batch;
    }

    private List<Map<String, Object>> activeEntries(
        TenantPrincipal principal, long shopId, long batchId
    ) {
        return jdbcTemplate.queryForList(
            """
            SELECT ce.id AS entryId, ce.status, ce.version AS entryVersion
            FROM commission_settlement_item si
            JOIN commission_entry ce
              ON ce.id = si.commission_entry_id AND ce.tenant_id = si.tenant_id
            WHERE si.tenant_id = ? AND si.shop_id = ? AND si.batch_id = ?
              AND si.active_flag = 1
            ORDER BY ce.id
            FOR UPDATE
            """,
            principal.tenantId(), shopId, batchId
        );
    }

    private void requireVersionAndTransition(
        Map<String, Object> batch, int version, String target
    ) {
        if (((Number) batch.get("version")).intValue() != version) throw versionConflict();
        if (!CommissionSettlementPolicy.canTransition(batch.get("status").toString(), target)) {
            throw new ApiException(HttpStatus.CONFLICT, "结算批次状态不允许执行该操作");
        }
    }

    private void transitionBatch(
        TenantPrincipal principal, long shopId, long batchId, int version,
        String from, String to, String actorColumn, String timeColumn, String paymentReference
    ) {
        String paymentSql = paymentReference == null ? "" : ", payment_reference = ?";
        List<Object> args = new ArrayList<>();
        args.add(to);
        args.add(principal.accountId());
        if (paymentReference != null) args.add(paymentReference);
        args.add(batchId);
        args.add(principal.tenantId());
        args.add(shopId);
        args.add(from);
        args.add(version);
        int updated = jdbcTemplate.update(
            """
            UPDATE commission_settlement_batch
            SET status = ?, %s = ?, %s = CURRENT_TIMESTAMP(3)%s,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = ? AND version = ?
            """.formatted(actorColumn, timeColumn, paymentSql),
            args.toArray()
        );
        if (updated != 1) throw versionConflict();
    }

    private void history(
        TenantPrincipal principal, long shopId, long entryId, String action,
        String from, String to, String reason, String key, String requestHash
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO commission_entry_history (
                tenant_id, shop_id, entry_id, action, from_status, to_status,
                amount_delta, reason, idempotency_key, request_hash, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, 0.00, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, entryId, action, from, to,
            reason, key, requestHash, principal.accountId()
        );
    }

    private void audit(TenantPrincipal principal, long shopId, long batchId, String action) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id
            ) VALUES (?, ?, ?, ?, 'COMMISSION_SETTLEMENT', ?)
            """,
            principal.tenantId(), shopId, principal.accountId(), action, batchId
        );
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "CALCULATED", "CONFIRMED", "PAID", "CLOSED", "VOIDED")
            .contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结算批次状态不正确");
        }
        return normalized;
    }

    private String requiredCurrency(String value) {
        String result = value == null || value.isBlank()
            ? "CNY" : value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "币种代码不正确");
        }
        return result;
    }

    private String required(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
        }
        return value.trim();
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "结算批次版本冲突");
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private void moneyFields(Map<String, Object> row) {
        for (String field : List.of(
            "totalAmount", "settlementAmount", "pendingAmount",
            "frozenAmount", "settledAmount"
        )) {
            if (row.get(field) != null) {
                row.put(field, decimal(row.get(field)).setScale(2).toPlainString());
            }
        }
    }
}
