package com.face.platform.packageaccount;

import com.face.platform.api.ApiException;
import com.face.platform.member.MemberService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MemberAccountApplicationService {

    private static final Set<String> MANUAL_ENTRY_TYPES = Set.of(
        "RECHARGE", "GIFT", "MANUAL_CREDIT", "MANUAL_DEBIT", "POINTS_EARN", "POINTS_SPEND"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final MemberService memberService;
    private final OutboxEventService outboxEventService;

    public MemberAccountApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        MemberService memberService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.memberService = memberService;
        this.outboxEventService = outboxEventService;
    }

    public List<Map<String, Object>> accounts(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        requireMemberRead(principal, shopId, memberId);
        return jdbcTemplate.queryForList(
            """
            SELECT id, account_type AS accountType, currency_code AS currencyCode,
                   balance, version, status, updated_at AS updatedAt
            FROM member_account
            WHERE tenant_id = ? AND member_id = ?
            ORDER BY FIELD(account_type, 'BALANCE', 'GIFT_BALANCE', 'POINTS')
            """,
            principal.tenantId(),
            memberId
        );
    }

    public List<Map<String, Object>> ledger(
        TenantPrincipal principal,
        long shopId,
        long accountId
    ) {
        Map<String, Object> account = requireAccount(principal, accountId, false);
        requireMemberRead(principal, shopId, number(account.get("memberId")));
        return jdbcTemplate.queryForList(
            """
            SELECT id, entry_type AS entryType, amount_delta AS amountDelta,
                   balance_after AS balanceAfter, reference_type AS referenceType,
                   reference_id AS referenceId,
                   reversal_of_ledger_id AS reversalOfLedgerId,
                   remark, created_at AS createdAt
            FROM member_account_ledger
            WHERE tenant_id = ? AND account_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 200
            """,
            principal.tenantId(),
            accountId
        );
    }

    @Transactional
    public Map<String, Object> postManualEntry(
        TenantPrincipal principal,
        long shopId,
        long accountId,
        boolean credit,
        String entryType,
        BigDecimal amount,
        String referenceType,
        Long referenceId,
        String idempotencyKey,
        String requestHash,
        String remark,
        int version
    ) {
        BigDecimal safeAmount = requirePositiveAmount(amount);
        accessService.requireShopPermission(principal, shopId, "account:manage");
        String normalizedEntryType = required(entryType, "账户流水类型")
            .toUpperCase(Locale.ROOT);
        if (!MANUAL_ENTRY_TYPES.contains(normalizedEntryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的账户流水类型");
        }
        Map<String, Object> replay = replay(
            principal, idempotencyKey, requestHash, accountId
        );
        if (replay != null) return replay;

        Map<String, Object> account = requireAccount(principal, accountId, true);
        long memberId = number(account.get("memberId"));
        memberService.requireActiveMemberInShop(principal, shopId, memberId);
        requireVersion(account, version);
        BigDecimal after = applyBalance(account, safeAmount, credit);
        updateBalance(principal, account, after, version);
        long ledgerId = insertLedger(
            principal,
            shopId,
            accountId,
            normalizedEntryType,
            credit ? safeAmount : safeAmount.negate(),
            after,
            trimToNull(referenceType),
            referenceId,
            idempotencyKey,
            requestHash,
            null,
            trimToNull(remark)
        );
        auditEntry(principal, shopId, accountId, ledgerId, normalizedEntryType);
        appendEntryEvent(principal, shopId, accountId, ledgerId);
        return ledgerResult(ledgerId);
    }

    @Transactional
    public Map<String, Object> reverse(
        TenantPrincipal principal,
        long shopId,
        long originalLedgerId,
        int accountVersion,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        accessService.requireShopPermission(principal, shopId, "account:manage");
        String safeReason = required(reason, "账户冲正原因");
        Map<String, Object> replay = replay(
            principal, idempotencyKey, requestHash, null
        );
        if (replay != null) return replay;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT mal.id AS ledgerId, ma.id AS id,
                   mal.account_id AS accountId, mal.entry_type AS entryType,
                   mal.amount_delta AS amountDelta,
                   ma.member_id AS memberId, ma.account_type AS accountType,
                   ma.balance, ma.version, ma.status
            FROM member_account_ledger mal
            JOIN member_account ma ON ma.id = mal.account_id
            WHERE mal.id = ? AND mal.tenant_id = ? AND ma.tenant_id = ?
            FOR UPDATE
            """,
            originalLedgerId,
            principal.tenantId(),
            principal.tenantId()
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "原账户流水不存在");
        }
        Map<String, Object> original = rows.getFirst();
        memberService.requireActiveMemberInShop(
            principal, shopId, number(original.get("memberId"))
        );
        requireVersion(original, accountVersion);
        Integer reversed = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM member_account_ledger
            WHERE tenant_id = ? AND reversal_of_ledger_id = ?
            """,
            Integer.class,
            principal.tenantId(),
            originalLedgerId
        );
        if (reversed != null && reversed > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该账户流水已经冲正");
        }
        BigDecimal originalDelta = decimal(original.get("amountDelta"));
        BigDecimal reversalDelta = originalDelta.negate();
        BigDecimal after = applyBalance(
            original,
            reversalDelta.abs(),
            reversalDelta.signum() > 0
        );
        long accountId = number(original.get("accountId"));
        updateBalance(principal, original, after, accountVersion);
        long ledgerId = insertLedger(
            principal,
            shopId,
            accountId,
            "REVERSAL",
            reversalDelta,
            after,
            "MEMBER_ACCOUNT_LEDGER",
            originalLedgerId,
            idempotencyKey,
            requestHash,
            originalLedgerId,
            safeReason
        );
        auditEntry(principal, shopId, accountId, ledgerId, "REVERSAL");
        appendEntryEvent(principal, shopId, accountId, ledgerId);
        return ledgerResult(ledgerId);
    }

    @Transactional
    public Map<String, Object> changeStatus(
        TenantPrincipal principal,
        long shopId,
        long accountId,
        int version,
        String targetStatus
    ) {
        accessService.requireShopPermission(principal, shopId, "account:manage");
        String status = required(targetStatus, "账户状态").toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "FROZEN").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "账户只能冻结或解冻");
        }
        Map<String, Object> account = requireAccount(principal, accountId, true);
        memberService.requireActiveMemberInShop(
            principal, shopId, number(account.get("memberId"))
        );
        requireVersion(account, version);
        int changed = jdbcTemplate.update(
            """
            UPDATE member_account
            SET status = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ?
            """,
            status,
            accountId,
            principal.tenantId(),
            version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "会员账户已被其他操作修改");
        }
        auditStatus(principal, shopId, accountId, status, version + 1);
        return accountResult(accountId);
    }

    @Transactional
    public void debitBalanceForOrder(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        BigDecimal amount,
        String paymentIdempotencyKey,
        String requestHash,
        long orderId
    ) {
        postInternal(
            principal,
            shopId,
            memberId,
            false,
            amount,
            "ORDER_PAYMENT",
            "SALES_ORDER",
            orderId,
            boundedKey("PAYMENT-" + paymentIdempotencyKey),
            requestHash,
            "订单余额支付"
        );
    }

    @Transactional
    public void creditBalanceForRefund(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        BigDecimal amount,
        long refundId,
        long orderId
    ) {
        postInternal(
            principal,
            shopId,
            memberId,
            true,
            amount,
            "ORDER_REFUND",
            "REFUND_TRANSACTION",
            refundId,
            "REFUND-" + refundId,
            "REFUND-" + refundId,
            "订单退款退回余额，订单ID：" + orderId
        );
    }

    private void postInternal(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        boolean credit,
        BigDecimal amount,
        String entryType,
        String referenceType,
        long referenceId,
        String idempotencyKey,
        String requestHash,
        String remark
    ) {
        BigDecimal safeAmount = requirePositiveAmount(amount);
        Map<String, Object> replay = replay(
            principal, idempotencyKey, requestHash, null
        );
        if (replay != null) return;
        memberService.requireActiveMemberInShop(principal, shopId, memberId);
        Map<String, Object> account = lockMemberAccount(principal, memberId, "BALANCE");
        int version = Math.toIntExact(number(account.get("version")));
        BigDecimal after = applyBalance(account, safeAmount, credit);
        updateBalance(principal, account, after, version);
        long accountId = number(account.get("id"));
        long ledgerId = insertLedger(
            principal,
            shopId,
            accountId,
            entryType,
            credit ? safeAmount : safeAmount.negate(),
            after,
            referenceType,
            referenceId,
            idempotencyKey,
            requestHash,
            null,
            remark
        );
        auditEntry(principal, shopId, accountId, ledgerId, entryType);
        appendEntryEvent(principal, shopId, accountId, ledgerId);
    }

    private BigDecimal applyBalance(
        Map<String, Object> account,
        BigDecimal amount,
        boolean credit
    ) {
        try {
            if (credit) {
                return MemberAccountPolicy.balanceAfterCredit(
                    account.get("accountType").toString(),
                    account.get("status").toString(),
                    decimal(account.get("balance")),
                    amount.abs()
                );
            }
            return MemberAccountPolicy.balanceAfterDebit(
                account.get("accountType").toString(),
                account.get("status").toString(),
                decimal(account.get("balance")),
                amount.abs()
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private Map<String, Object> requireAccount(
        TenantPrincipal principal,
        long accountId,
        boolean lock
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, account_type AS accountType,
                   balance, version, status
            FROM member_account
            WHERE id = ? AND tenant_id = ?
            %s
            """.formatted(lock ? "FOR UPDATE" : ""),
            accountId,
            principal.tenantId()
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "会员账户不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockMemberAccount(
        TenantPrincipal principal,
        long memberId,
        String accountType
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, account_type AS accountType,
                   balance, version, status
            FROM member_account
            WHERE tenant_id = ? AND member_id = ? AND account_type = ?
            FOR UPDATE
            """,
            principal.tenantId(),
            memberId,
            accountType
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "会员余额账户不可用");
        }
        return rows.getFirst();
    }

    private void updateBalance(
        TenantPrincipal principal,
        Map<String, Object> account,
        BigDecimal after,
        int version
    ) {
        int changed = jdbcTemplate.update(
            """
            UPDATE member_account
            SET balance = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ?
            """,
            after,
            number(account.get("id")),
            principal.tenantId(),
            version
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "会员账户已被其他交易修改");
        }
    }

    private long insertLedger(
        TenantPrincipal principal,
        long shopId,
        long accountId,
        String entryType,
        BigDecimal delta,
        BigDecimal after,
        String referenceType,
        Long referenceId,
        String idempotencyKey,
        String requestHash,
        Long reversalOfLedgerId,
        String remark
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO member_account_ledger (
                tenant_id, account_id, shop_id, entry_type, amount_delta,
                balance_after, reference_type, reference_id, idempotency_key,
                request_hash, reversal_of_ledger_id, remark, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(),
            accountId,
            shopId,
            entryType,
            delta,
            after,
            referenceType,
            referenceId,
            required(idempotencyKey, "幂等键"),
            required(requestHash, "请求摘要"),
            reversalOfLedgerId,
            remark,
            principal.accountId()
        );
        Long id = jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM member_account_ledger
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            Long.class,
            principal.tenantId(),
            idempotencyKey
        );
        if (id == null) throw new IllegalStateException("账户流水创建失败");
        return id;
    }

    private Map<String, Object> replay(
        TenantPrincipal principal,
        String idempotencyKey,
        String requestHash,
        Long expectedAccountId
    ) {
        String key = required(idempotencyKey, "幂等键");
        String hash = required(requestHash, "请求摘要");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, account_id AS accountId, request_hash AS requestHash
            FROM member_account_ledger
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            principal.tenantId(),
            key
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> existing = rows.getFirst();
        if (!hash.equals(existing.get("requestHash"))
            || (expectedAccountId != null
                && number(existing.get("accountId")) != expectedAccountId.longValue())) {
            throw new ApiException(HttpStatus.CONFLICT, "账户幂等键已用于不同请求");
        }
        return ledgerResult(number(existing.get("id")));
    }

    private Map<String, Object> ledgerResult(long ledgerId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT id, account_id AS accountId, entry_type AS entryType,
                   amount_delta AS amountDelta, balance_after AS balanceAfter,
                   reference_type AS referenceType, reference_id AS referenceId,
                   reversal_of_ledger_id AS reversalOfLedgerId, created_at AS createdAt
            FROM member_account_ledger
            WHERE id = ?
            """,
            ledgerId
        );
    }

    private Map<String, Object> accountResult(long accountId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT id, member_id AS memberId, account_type AS accountType,
                   balance, version, status, updated_at AS updatedAt
            FROM member_account
            WHERE id = ?
            """,
            accountId
        );
    }

    private void requireMemberRead(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        if (principal.roles().contains("MEMBER")) {
            if (accessService.requireMemberId(principal) != memberId) {
                throw new ApiException(HttpStatus.FORBIDDEN, "只能查看本人账户");
            }
        } else {
            accessService.requireShopPermission(principal, shopId, "account:view");
        }
        memberService.requireActiveMemberInShop(principal, shopId, memberId);
    }

    private void requireVersion(Map<String, Object> account, int version) {
        if (number(account.get("version")) != version) {
            throw new ApiException(HttpStatus.CONFLICT, "会员账户版本已变化，请刷新后重试");
        }
    }

    private void auditEntry(
        TenantPrincipal principal,
        long shopId,
        long accountId,
        long ledgerId,
        String entryType
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (
                ?, ?, ?, 'MEMBER_ACCOUNT_ENTRY', 'MEMBER_ACCOUNT', ?,
                JSON_OBJECT('ledgerId', ?, 'entryType', ?)
            )
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            accountId,
            ledgerId,
            entryType
        );
    }

    private void auditStatus(
        TenantPrincipal principal,
        long shopId,
        long accountId,
        String status,
        int version
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (
                ?, ?, ?, 'MEMBER_ACCOUNT_STATUS', 'MEMBER_ACCOUNT', ?,
                JSON_OBJECT('status', ?, 'version', ?)
            )
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            accountId,
            status,
            version
        );
    }

    private void appendEntryEvent(
        TenantPrincipal principal,
        long shopId,
        long accountId,
        long ledgerId
    ) {
        outboxEventService.append(
            principal,
            shopId,
            "MEMBER_ACCOUNT",
            Long.toString(accountId),
            "MemberAccountEntryPosted",
            Map.of("accountId", accountId, "ledgerId", ledgerId)
        );
    }

    private String required(String value, String label) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return safe;
    }

    private BigDecimal requirePositiveAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "账户金额必须大于零");
        }
        return value;
    }

    private String boundedKey(String value) {
        String key = required(value, "幂等键");
        return key.length() <= 80 ? key : key.substring(0, 80);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("会员账户数据不完整");
        }
        return number.longValue();
    }
}
