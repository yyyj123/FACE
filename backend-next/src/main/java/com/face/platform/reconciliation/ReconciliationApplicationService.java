package com.face.platform.reconciliation;

import com.face.platform.api.ApiException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReconciliationApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;

    public ReconciliationApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
    }

    public List<Map<String, Object>> list(
        TenantPrincipal principal,
        long shopId,
        String status
    ) {
        accessService.requireShopPermission(principal, shopId, "reconciliation:view");
        String normalized = status == null || status.isBlank()
            ? "ALL"
            : status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return jdbcTemplate.queryForList(
                selectBatchSql()
                    + " WHERE tenant_id = ? AND shop_id = ?"
                    + " ORDER BY accounting_date DESC, id DESC LIMIT 100",
                principal.tenantId(),
                shopId
            );
        }
        return jdbcTemplate.queryForList(
            selectBatchSql()
                + " WHERE tenant_id = ? AND shop_id = ? AND status = ?"
                + " ORDER BY accounting_date DESC, id DESC LIMIT 100",
            principal.tenantId(),
            shopId,
            normalized
        );
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long batchId
    ) {
        accessService.requireShopPermission(principal, shopId, "reconciliation:view");
        Map<String, Object> result = new LinkedHashMap<>(
            requireBatch(principal.tenantId(), shopId, batchId, false)
        );
        result.put("items", jdbcTemplate.queryForList(
            """
            SELECT id, business_type AS businessType, business_no AS businessNo,
                   system_amount AS systemAmount, channel_amount AS channelAmount,
                   difference_type AS differenceType, created_at AS createdAt
            FROM reconciliation_item
            WHERE batch_id = ?
            ORDER BY business_type, business_no, id
            """,
            batchId
        ));
        result.put("resolutions", jdbcTemplate.queryForList(
            """
            SELECT rr.id, rr.resolution_note AS resolutionNote,
                   rr.evidence_reference AS evidenceReference,
                   rr.created_by AS createdBy, a.username AS createdByName,
                   rr.created_at AS createdAt
            FROM reconciliation_resolution rr
            JOIN account a ON a.id = rr.created_by
            WHERE rr.batch_id = ?
            ORDER BY rr.created_at, rr.id
            """,
            batchId
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> run(
        TenantPrincipal principal,
        long shopId,
        String channelCode,
        LocalDate accountingDate,
        long channelPaymentCount,
        BigDecimal channelPaymentAmount,
        long channelRefundCount,
        BigDecimal channelRefundAmount,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "reconciliation:manage");
        String channel = required(channelCode, "支付通道").toUpperCase(Locale.ROOT);
        if (accountingDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "账务日期不能为空");
        }
        if (accountingDate.isAfter(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能对未来日期执行对账");
        }
        String key = required(idempotencyKey, "对账幂等键");
        String hash = required(requestHash, "请求摘要");
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, request_hash AS requestHash
            FROM reconciliation_batch
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            key
        );
        if (!replay.isEmpty()) {
            Map<String, Object> existing = replay.getFirst();
            if (!hash.equals(existing.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "对账幂等键已用于不同请求");
            }
            return detail(principal, shopId, number(existing.get("id")));
        }

        Map<String, Object> paymentSummary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS transactionCount, COALESCE(SUM(amount), 0) AS totalAmount
            FROM payment_transaction
            WHERE tenant_id = ? AND shop_id = ?
              AND COALESCE(channel_code, payment_method) = ?
              AND status = 'SUCCESS' AND DATE(paid_at) = ?
            """,
            principal.tenantId(),
            shopId,
            channel,
            accountingDate
        );
        Map<String, Object> refundSummary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS transactionCount, COALESCE(SUM(rt.amount), 0) AS totalAmount
            FROM refund_transaction rt
            JOIN payment_transaction pt
              ON pt.id = rt.payment_id
             AND pt.tenant_id = rt.tenant_id
             AND pt.shop_id = rt.shop_id
            WHERE rt.tenant_id = ? AND rt.shop_id = ?
              AND COALESCE(pt.channel_code, pt.payment_method) = ?
              AND rt.status = 'SUCCESS' AND DATE(rt.refunded_at) = ?
            """,
            principal.tenantId(),
            shopId,
            channel,
            accountingDate
        );
        long systemPaymentCount = number(paymentSummary.get("transactionCount"));
        BigDecimal systemPaymentAmount = money(paymentSummary.get("totalAmount"));
        long systemRefundCount = number(refundSummary.get("transactionCount"));
        BigDecimal systemRefundAmount = money(refundSummary.get("totalAmount"));
        BigDecimal safeChannelPayment = nonNegativeMoney(channelPaymentAmount, "通道支付金额");
        BigDecimal safeChannelRefund = nonNegativeMoney(channelRefundAmount, "通道退款金额");
        if (channelPaymentCount < 0 || channelRefundCount < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "通道笔数不能小于零");
        }
        boolean countsMatch = systemPaymentCount == channelPaymentCount
            && systemRefundCount == channelRefundCount;
        String calculatedStatus = ReconciliationPolicy.resultStatus(
            systemPaymentCount + systemRefundCount,
            systemPaymentAmount,
            systemRefundAmount,
            channelPaymentCount + channelRefundCount,
            safeChannelPayment,
            safeChannelRefund
        );
        final String resultStatus = countsMatch ? calculatedStatus : "DIFFERENT";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO reconciliation_batch (
                    tenant_id, shop_id, channel_code, accounting_date,
                    system_payment_count, system_payment_amount,
                    system_refund_count, system_refund_amount,
                    channel_payment_count, channel_payment_amount,
                    channel_refund_count, channel_refund_amount,
                    status, idempotency_key, request_hash, version,
                    created_by, started_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 2, ?,
                          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, channel);
            statement.setObject(4, accountingDate);
            statement.setLong(5, systemPaymentCount);
            statement.setBigDecimal(6, systemPaymentAmount);
            statement.setLong(7, systemRefundCount);
            statement.setBigDecimal(8, systemRefundAmount);
            statement.setLong(9, channelPaymentCount);
            statement.setBigDecimal(10, safeChannelPayment);
            statement.setLong(11, channelRefundCount);
            statement.setBigDecimal(12, safeChannelRefund);
            statement.setString(13, resultStatus);
            statement.setString(14, key);
            statement.setString(15, hash);
            statement.setLong(16, principal.accountId());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "对账批次创建失败");
        }
        long batchId = generated.longValue();
        appendSummaryDifference(
            batchId,
            "PAYMENT",
            systemPaymentCount,
            systemPaymentAmount,
            channelPaymentCount,
            safeChannelPayment
        );
        appendSummaryDifference(
            batchId,
            "REFUND",
            systemRefundCount,
            systemRefundAmount,
            channelRefundCount,
            safeChannelRefund
        );
        audit(principal, shopId, "RECONCILIATION_RUN", batchId, accountingDate.toString());
        outboxEventService.append(
            principal,
            shopId,
            "RECONCILIATION_BATCH",
            String.valueOf(batchId),
            "ReconciliationCompleted",
            Map.of("reconciliationBatchId", batchId)
        );
        return detail(principal, shopId, batchId);
    }

    @Transactional
    public Map<String, Object> resolve(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version,
        String note,
        String evidenceReference
    ) {
        accessService.requireShopPermission(principal, shopId, "reconciliation:manage");
        Map<String, Object> batch = requireBatch(principal.tenantId(), shopId, batchId, true);
        requireVersion(batch, version);
        if (!ReconciliationLifecyclePolicy.canTransition(
            batch.get("status").toString(),
            "RESOLVED"
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "只有存在差异的批次可以处理");
        }
        String safeNote = required(note, "差异处理说明");
        jdbcTemplate.update(
            """
            INSERT INTO reconciliation_resolution (
                batch_id, resolution_note, evidence_reference, created_by
            ) VALUES (?, ?, ?, ?)
            """,
            batchId,
            safeNote,
            trimToNull(evidenceReference),
            principal.accountId()
        );
        int changed = jdbcTemplate.update(
            """
            UPDATE reconciliation_batch
            SET status = 'RESOLVED', version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'DIFFERENT'
            """,
            batchId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed);
        audit(principal, shopId, "RECONCILIATION_RESOLVE", batchId, safeNote);
        return detail(principal, shopId, batchId);
    }

    @Transactional
    public Map<String, Object> close(
        TenantPrincipal principal,
        long shopId,
        long batchId,
        int version
    ) {
        accessService.requireShopPermission(principal, shopId, "reconciliation:manage");
        Map<String, Object> batch = requireBatch(principal.tenantId(), shopId, batchId, true);
        requireVersion(batch, version);
        if (!ReconciliationLifecyclePolicy.canTransition(
            batch.get("status").toString(),
            "CLOSED"
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "批次尚未匹配或完成差异处理");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE reconciliation_batch
            SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status IN ('MATCHED', 'RESOLVED')
            """,
            batchId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed);
        audit(principal, shopId, "RECONCILIATION_CLOSE", batchId, "closed");
        return detail(principal, shopId, batchId);
    }

    private void appendSummaryDifference(
        long batchId,
        String type,
        long systemCount,
        BigDecimal systemAmount,
        long channelCount,
        BigDecimal channelAmount
    ) {
        if (systemCount == channelCount && systemAmount.compareTo(channelAmount) == 0) return;
        String difference = systemCount > channelCount
            ? "MISSING_CHANNEL"
            : systemCount < channelCount
                ? "MISSING_SYSTEM"
                : "AMOUNT_MISMATCH";
        jdbcTemplate.update(
            """
            INSERT INTO reconciliation_item (
                batch_id, business_type, business_no,
                system_amount, channel_amount, difference_type
            ) VALUES (?, ?, 'SUMMARY', ?, ?, ?)
            """,
            batchId,
            type,
            systemAmount,
            channelAmount,
            difference
        );
    }

    private Map<String, Object> requireBatch(
        long tenantId,
        long shopId,
        long batchId,
        boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectBatchSql()
                + " WHERE id = ? AND tenant_id = ? AND shop_id = ?"
                + (lock ? " FOR UPDATE" : ""),
            batchId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "对账批次不存在");
        return rows.getFirst();
    }

    private String selectBatchSql() {
        return """
            SELECT id, shop_id AS shopId, channel_code AS channelCode,
                   accounting_date AS accountingDate,
                   system_payment_count AS systemPaymentCount,
                   system_payment_amount AS systemPaymentAmount,
                   system_refund_count AS systemRefundCount,
                   system_refund_amount AS systemRefundAmount,
                   channel_payment_count AS channelPaymentCount,
                   channel_payment_amount AS channelPaymentAmount,
                   channel_refund_count AS channelRefundCount,
                   channel_refund_amount AS channelRefundAmount,
                   status, version, created_by AS createdBy,
                   started_at AS startedAt, completed_at AS completedAt,
                   closed_at AS closedAt, created_at AS createdAt
            FROM reconciliation_batch
            """;
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        long batchId,
        String note
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, metadata
            ) VALUES (?, ?, ?, ?, 'RECONCILIATION_BATCH', ?,
                      JSON_OBJECT('note', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            batchId,
            note
        );
    }

    private BigDecimal money(Object value) {
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能为空");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能小于零");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + "最多两位小数");
        }
    }

    private long number(Object value) {
        return value instanceof Number number
            ? number.longValue()
            : Long.parseLong(value.toString());
    }

    private void requireVersion(Map<String, Object> batch, int expected) {
        if (number(batch.get("version")) != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "对账批次版本已变化");
        }
    }

    private void requireChanged(int changed) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "对账批次已被其他人修改");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + "不能为空");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
