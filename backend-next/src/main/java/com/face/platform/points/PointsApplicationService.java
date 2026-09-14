package com.face.platform.points;

import com.face.platform.api.ApiException;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PointsApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public PointsApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> mine(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        long accountId = ensureAccount(principal.tenantId(), memberId);
        Map<String, Object> account = jdbcTemplate.queryForMap(
            """
            SELECT id, available_points AS availablePoints, frozen_points AS frozenPoints,
                   total_earned AS totalEarned, total_consumed AS totalConsumed, version
            FROM points_account WHERE id = ?
            """,
            accountId
        );
        List<Map<String, Object>> batches = jdbcTemplate.queryForList(
            """
            SELECT id, batch_no AS batchNo, source_type AS sourceType,
                   remaining_points AS remainingPoints, frozen_points AS frozenPoints,
                   earned_at AS earnedAt, expires_at AS expiresAt, status
            FROM points_batch WHERE account_id = ? AND status IN ('ACTIVE', 'EXHAUSTED')
            ORDER BY expires_at, id LIMIT 100
            """,
            accountId
        );
        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
            """
            SELECT id, entry_type AS entryType, points_delta AS pointsDelta,
                   available_after AS availableAfter, frozen_after AS frozenAfter,
                   reference_type AS referenceType, reference_id AS referenceId,
                   reason, created_at AS createdAt
            FROM points_ledger WHERE account_id = ? ORDER BY created_at DESC, id DESC LIMIT 100
            """,
            accountId
        );
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
            """
            SELECT id, task_code AS taskCode, task_type AS taskType, name,
                   reward_points AS rewardPoints, cycle_days AS cycleDays,
                   daily_rewards_json AS dailyRewards, cycle_bonus_points AS cycleBonusPoints,
                   repeat_cycle AS repeatCycle
            FROM points_task
            WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) AND status = 'ACTIVE'
            ORDER BY task_type, id
            """,
            principal.tenantId(), principal.homeShopId()
        );
        Map<String, Object> checkin = jdbcTemplate.queryForList(
            """
            SELECT checkin_date AS checkinDate, streak_day AS streakDay,
                   cycle_day AS cycleDay, reward_points AS rewardPoints
            FROM member_checkin WHERE tenant_id = ? AND member_id = ?
            ORDER BY checkin_date DESC LIMIT 1
            """,
            principal.tenantId(), memberId
        ).stream().findFirst().orElse(Map.of());
        long expiring = batches.stream()
            .filter(batch -> !"EXHAUSTED".equals(batch.get("status")))
            .filter(batch -> localDateTime(batch.get("expiresAt")).toLocalDate()
                .isBefore(LocalDate.now().plusDays(31)))
            .mapToLong(batch -> number(batch.get("remainingPoints")) - number(batch.get("frozenPoints")))
            .sum();
        return Map.of(
            "account", account,
            "batches", batches,
            "ledger", ledger,
            "tasks", tasks,
            "lastCheckin", checkin,
            "expiringIn30Days", expiring
        );
    }

    public Map<String, Object> administration(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "points:view");
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
            """
            SELECT id, rule_code AS ruleCode, rule_type AS ruleType,
                   target_type AS targetType, target_id AS targetId,
                   points_per_yuan AS pointsPerYuan, points_per_currency AS pointsPerCurrency,
                   minimum_points AS minimumPoints, step_points AS stepPoints,
                   max_discount_ratio AS maxDiscountRatio,
                   max_discount_amount AS maxDiscountAmount,
                   single_cap_points AS singleCapPoints,
                   member_period_cap_points AS memberPeriodCapPoints,
                   validity_months AS validityMonths, status, version
            FROM points_rule WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)
            ORDER BY rule_type, target_type, id
            """,
            principal.tenantId(), shopId
        );
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
            """
            SELECT id, task_code AS taskCode, task_type AS taskType, name,
                   reward_points AS rewardPoints, cycle_days AS cycleDays,
                   daily_rewards_json AS dailyRewards, cycle_bonus_points AS cycleBonusPoints,
                   repeat_cycle AS repeatCycle, member_period_cap_points AS memberPeriodCapPoints,
                   status, version
            FROM points_task WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)
            ORDER BY task_type, id
            """,
            principal.tenantId(), shopId
        );
        Map<String, Object> summary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS accountCount,
                   COALESCE(SUM(available_points), 0) AS availablePoints,
                   COALESCE(SUM(frozen_points), 0) AS frozenPoints,
                   COALESCE(SUM(total_earned), 0) AS totalEarned
            FROM points_account WHERE tenant_id = ?
            """,
            principal.tenantId()
        );
        return Map.of("rules", rules, "tasks", tasks, "summary", summary);
    }

    public Map<String, Object> redemptionCandidate(
        long tenantId,
        long shopId,
        long memberId,
        String targetType,
        long targetId,
        BigDecimal subtotal
    ) {
        long accountId = ensureAccount(tenantId, memberId);
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT available_points AS availablePoints FROM points_account WHERE id = ?",
            accountId
        );
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
            """
            SELECT id, points_per_currency AS pointsPerCurrency,
                   minimum_points AS minimumPoints, step_points AS stepPoints,
                   max_discount_ratio AS maxDiscountRatio,
                   max_discount_amount AS maxDiscountAmount
            FROM points_rule
            WHERE tenant_id = ? AND rule_type = 'REDEEM' AND status = 'ACTIVE'
              AND (shop_id IS NULL OR shop_id = ?)
              AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP(3))
              AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP(3))
              AND ((target_type = ? AND target_id = ?) OR target_type = 'GLOBAL')
            ORDER BY CASE WHEN target_type = ? THEN 0 ELSE 1 END, id LIMIT 1
            """,
            tenantId, shopId, targetType, targetId, targetType
        );
        if (rules.isEmpty()) return Map.of("available", false, "reason", "当前项目未配置积分抵扣");
        Map<String, Object> rule = rules.getFirst();
        PointsPolicy.Redemption redemption = PointsPolicy.redemption(
            number(account.get("availablePoints")), subtotal,
            integer(rule.get("pointsPerCurrency")), integer(rule.get("minimumPoints")),
            integer(rule.get("stepPoints")), decimal(rule.get("maxDiscountRatio")),
            rule.get("maxDiscountAmount") == null ? null : decimal(rule.get("maxDiscountAmount"))
        );
        if (redemption.points() == 0) {
            return Map.of("available", false, "reason", "可用积分未达到本次抵扣门槛");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("selectionType", "POINTS");
        result.put("referenceId", accountId);
        result.put("name", redemption.points() + " 积分抵扣");
        result.put("pointsUsed", redemption.points());
        result.put("discountAmount", redemption.discountAmount());
        result.put("payableAmount", subtotal.subtract(redemption.discountAmount()).setScale(2));
        result.put("recommended", false);
        result.put("ruleId", number(rule.get("id")));
        return result;
    }

    @Transactional
    public Map<String, Object> updateRule(
        TenantPrincipal principal, long shopId, long ruleId, RuleUpdateCommand command
    ) {
        accessService.requireShopPermission(principal, shopId, "points:manage");
        Map<String, Object> current = jdbcTemplate.queryForMap(
            "SELECT target_type AS targetType, validity_months AS validityMonths, version FROM points_rule WHERE id = ? AND tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) FOR UPDATE",
            ruleId, principal.tenantId(), shopId
        );
        if ("GLOBAL".equals(current.get("targetType"))
            && command.validityMonths() != integer(current.get("validityMonths"))
            && !principal.roles().contains("SUPER_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有超级管理员可以修改全局积分有效期");
        }
        if (command.validityMonths() < 1 || command.validityMonths() > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分有效期必须为 1 到 60 个月");
        }
        requireChanged(jdbcTemplate.update(
            """
            UPDATE points_rule SET points_per_yuan = ?, points_per_currency = ?,
                minimum_points = ?, step_points = ?, max_discount_ratio = ?,
                max_discount_amount = ?, single_cap_points = ?,
                member_period_cap_points = ?, validity_months = ?, status = ?,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) AND version = ?
            """,
            command.pointsPerYuan(), command.pointsPerCurrency(), command.minimumPoints(),
            command.stepPoints(), command.maxDiscountRatio(), command.maxDiscountAmount(),
            command.singleCapPoints(), command.memberPeriodCapPoints(), command.validityMonths(),
            command.status(), ruleId, principal.tenantId(), shopId, command.version()
        ), "积分规则已被其他操作修改");
        return jdbcTemplate.queryForMap(
            "SELECT id, rule_code AS ruleCode, points_per_yuan AS pointsPerYuan, points_per_currency AS pointsPerCurrency, minimum_points AS minimumPoints, step_points AS stepPoints, max_discount_ratio AS maxDiscountRatio, max_discount_amount AS maxDiscountAmount, single_cap_points AS singleCapPoints, member_period_cap_points AS memberPeriodCapPoints, validity_months AS validityMonths, status, version FROM points_rule WHERE id = ?",
            ruleId
        );
    }

    @Transactional
    public Map<String, Object> updateTask(
        TenantPrincipal principal, long shopId, long taskId, TaskUpdateCommand command
    ) {
        accessService.requireShopPermission(principal, shopId, "points:manage");
        if (command.cycleDays() < 1 || command.cycleDays() > 365
            || command.dailyRewards() == null || command.dailyRewards().size() != command.cycleDays()
            || command.dailyRewards().stream().anyMatch(value -> value == null || value < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "签到阶梯必须与周期天数一致且奖励不得为负数");
        }
        requireChanged(jdbcTemplate.update(
            """
            UPDATE points_task SET reward_points = ?, cycle_days = ?, daily_rewards_json = ?,
                cycle_bonus_points = ?, repeat_cycle = ?, member_period_cap_points = ?,
                status = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND (shop_id IS NULL OR shop_id = ?) AND version = ?
            """,
            command.rewardPoints(), command.cycleDays(), json(command.dailyRewards()),
            command.cycleBonusPoints(), command.repeatCycle(), command.memberPeriodCapPoints(),
            command.status(), taskId, principal.tenantId(), shopId, command.version()
        ), "积分任务已被其他操作修改");
        return jdbcTemplate.queryForMap(
            "SELECT id, task_code AS taskCode, reward_points AS rewardPoints, cycle_days AS cycleDays, daily_rewards_json AS dailyRewards, cycle_bonus_points AS cycleBonusPoints, repeat_cycle AS repeatCycle, member_period_cap_points AS memberPeriodCapPoints, status, version FROM points_task WHERE id = ?",
            taskId
        );
    }

    @Transactional
    public long earnPaidSalesOrder(TenantPrincipal principal, long shopId, long orderId) {
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            "SELECT member_id AS memberId, paid_amount AS paidAmount, status FROM sales_order WHERE id = ? AND tenant_id = ? AND shop_id = ? FOR UPDATE",
            orderId, principal.tenantId(), shopId
        );
        if (orders.isEmpty() || !"PAID".equals(orders.getFirst().get("status"))) return 0;
        Integer eligibleLines = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_order_item WHERE order_id = ? AND item_type = 'PACKAGE'",
            Integer.class, orderId
        );
        Integer confirmedServiceLines = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM sales_order_item soi
            JOIN sales_order so ON so.id = soi.order_id
            JOIN service_record sr ON sr.id = so.service_record_id
            WHERE soi.order_id = ? AND soi.item_type = 'SERVICE'
              AND sr.fulfillment_status = 'COMPLETED'
            """,
            Integer.class, orderId
        );
        if ((eligibleLines == null || eligibleLines == 0)
            && (confirmedServiceLines == null || confirmedServiceLines == 0)) return 0;
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id FROM points_batch WHERE tenant_id = ? AND source_reference_type = 'SALES_ORDER' AND source_reference_id = ?",
            principal.tenantId(), orderId
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("id"));
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
            """
            SELECT pr.id, pr.points_per_yuan AS pointsPerYuan,
                   pr.single_cap_points AS singleCapPoints,
                   pr.member_period_cap_points AS memberPeriodCapPoints,
                   pr.validity_months AS validityMonths
            FROM points_rule pr
            WHERE pr.tenant_id = ? AND pr.rule_type = 'EARN' AND pr.status = 'ACTIVE'
              AND (pr.shop_id IS NULL OR pr.shop_id = ?)
              AND (pr.starts_at IS NULL OR pr.starts_at <= CURRENT_TIMESTAMP(3))
              AND (pr.ends_at IS NULL OR pr.ends_at > CURRENT_TIMESTAMP(3))
              AND (
                pr.target_type = 'GLOBAL'
                OR (pr.target_type = 'ACTIVITY' AND EXISTS (
                    SELECT 1 FROM order_pricing_decision opd
                    WHERE opd.order_id = ? AND opd.selection_type = 'ACTIVITY' AND opd.activity_id = pr.target_id
                ))
                OR (pr.target_type = 'SERVICE' AND EXISTS (
                    SELECT 1 FROM sales_order_item soi
                    WHERE soi.order_id = ? AND soi.item_type = 'SERVICE' AND soi.service_id = pr.target_id
                ))
                OR (pr.target_type = 'PACKAGE' AND EXISTS (
                    SELECT 1 FROM sales_order_item soi
                    WHERE soi.order_id = ? AND soi.item_type = 'PACKAGE' AND soi.package_product_id = pr.target_id
                ))
              )
            ORDER BY CASE pr.target_type WHEN 'ACTIVITY' THEN 0 WHEN 'SERVICE' THEN 1 WHEN 'PACKAGE' THEN 1 ELSE 2 END,
                     CASE WHEN pr.shop_id = ? THEN 0 ELSE 1 END, pr.id
            LIMIT 1
            """,
            principal.tenantId(), shopId, orderId, orderId, orderId, shopId
        );
        if (rules.isEmpty()) return 0;
        Map<String, Object> order = orders.getFirst();
        Map<String, Object> rule = rules.getFirst();
        long points = decimal(order.get("paidAmount"))
            .multiply(decimal(rule.get("pointsPerYuan")))
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (rule.get("singleCapPoints") != null) {
            points = Math.min(points, number(rule.get("singleCapPoints")));
        }
        if (rule.get("memberPeriodCapPoints") != null) {
            Long earned = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(granted_points), 0) FROM points_batch WHERE tenant_id = ? AND account_id = ? AND source_rule_id = ? AND earned_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
                Long.class, principal.tenantId(), ensureAccount(principal.tenantId(), number(order.get("memberId"))), number(rule.get("id"))
            );
            points = Math.min(points, Math.max(0, number(rule.get("memberPeriodCapPoints")) - (earned == null ? 0 : earned)));
        }
        if (points <= 0) return 0;
        String key = "earn:sales-order:" + orderId;
        return grantInternal(
            principal, shopId, number(order.get("memberId")), points, "ORDER_PAYMENT",
            "SALES_ORDER", orderId, "订单实付自动累积积分", key, hash(key),
            integer(rule.get("validityMonths")), number(rule.get("id"))
        );
    }

    @Transactional
    public long earnCompletedMallOrder(TenantPrincipal principal, long shopId, long orderId) {
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            "SELECT member_id AS memberId, cash_amount AS cashAmount, status FROM mall_order WHERE id = ? AND tenant_id = ? AND shop_id = ? FOR UPDATE",
            orderId, principal.tenantId(), shopId
        );
        if (orders.isEmpty() || !"COMPLETED".equals(orders.getFirst().get("status"))
            || decimal(orders.getFirst().get("cashAmount")).signum() == 0) return 0;
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id FROM points_batch WHERE tenant_id = ? AND source_reference_type = 'MALL_ORDER' AND source_reference_id = ?",
            principal.tenantId(), orderId
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("id"));
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
            """
            SELECT pr.id, pr.points_per_yuan AS pointsPerYuan,
                   pr.single_cap_points AS singleCapPoints,
                   pr.member_period_cap_points AS memberPeriodCapPoints,
                   pr.validity_months AS validityMonths
            FROM points_rule pr
            WHERE pr.tenant_id = ? AND pr.rule_type = 'EARN' AND pr.status = 'ACTIVE'
              AND (pr.shop_id IS NULL OR pr.shop_id = ?)
              AND (pr.starts_at IS NULL OR pr.starts_at <= CURRENT_TIMESTAMP(3))
              AND (pr.ends_at IS NULL OR pr.ends_at > CURRENT_TIMESTAMP(3))
              AND (pr.target_type = 'GLOBAL' OR (pr.target_type = 'MALL_SKU' AND EXISTS (
                  SELECT 1 FROM mall_order_item moi WHERE moi.mall_order_id = ? AND moi.sku_id = pr.target_id
              )))
            ORDER BY CASE pr.target_type WHEN 'MALL_SKU' THEN 0 ELSE 1 END,
                     CASE WHEN pr.shop_id = ? THEN 0 ELSE 1 END, pr.id LIMIT 1
            """,
            principal.tenantId(), shopId, orderId, shopId
        );
        if (rules.isEmpty()) return 0;
        Map<String, Object> order = orders.getFirst();
        Map<String, Object> rule = rules.getFirst();
        long points = decimal(order.get("cashAmount")).multiply(decimal(rule.get("pointsPerYuan")))
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (rule.get("singleCapPoints") != null) points = Math.min(points, number(rule.get("singleCapPoints")));
        long accountId = ensureAccount(principal.tenantId(), number(order.get("memberId")));
        if (rule.get("memberPeriodCapPoints") != null) {
            Long earned = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(granted_points), 0) FROM points_batch WHERE tenant_id = ? AND account_id = ? AND source_rule_id = ? AND earned_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')",
                Long.class, principal.tenantId(), accountId, number(rule.get("id"))
            );
            points = Math.min(points, Math.max(0, number(rule.get("memberPeriodCapPoints")) - (earned == null ? 0 : earned)));
        }
        if (points <= 0) return 0;
        String key = "earn:mall-order:" + orderId;
        return grantInternal(
            principal, shopId, number(order.get("memberId")), points, "MALL_RECEIPT",
            "MALL_ORDER", orderId, "商城订单确认收货后按人民币实付累积积分",
            key, hash(key), integer(rule.get("validityMonths")), number(rule.get("id"))
        );
    }

    @Transactional
    public long clawbackEarnedForRefund(
        TenantPrincipal principal, long shopId, long orderId, long refundId,
        BigDecimal refundAmount, BigDecimal paidAmount
    ) {
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT requested_points AS requestedPoints FROM points_clawback WHERE tenant_id = ? AND refund_id = ?",
            principal.tenantId(), refundId
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("requestedPoints"));
        List<Map<String, Object>> batches = jdbcTemplate.queryForList(
            "SELECT id, account_id AS accountId, granted_points AS grantedPoints, remaining_points AS remainingPoints, frozen_points AS frozenPoints FROM points_batch WHERE tenant_id = ? AND source_reference_type = 'SALES_ORDER' AND source_reference_id = ? FOR UPDATE",
            principal.tenantId(), orderId
        );
        if (batches.isEmpty()) return 0;
        Map<String, Object> batch = batches.getFirst();
        long requested = refundAmount.compareTo(paidAmount) >= 0
            ? number(batch.get("grantedPoints"))
            : refundAmount.multiply(BigDecimal.valueOf(number(batch.get("grantedPoints"))))
                .divide(paidAmount, 0, RoundingMode.HALF_UP).longValueExact();
        Long previouslyRequested = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(requested_points), 0) FROM points_clawback WHERE tenant_id = ? AND sales_order_id = ?",
            Long.class, principal.tenantId(), orderId
        );
        requested = Math.min(requested, Math.max(
            0, number(batch.get("grantedPoints")) - (previouslyRequested == null ? 0 : previouslyRequested)
        ));
        if (requested <= 0) return 0;
        long deductible = Math.max(0, number(batch.get("remainingPoints")) - number(batch.get("frozenPoints")));
        long deducted = Math.min(requested, deductible);
        long outstanding = requested - deducted;
        long accountId = number(batch.get("accountId"));
        if (deducted > 0) {
            jdbcTemplate.update(
                "UPDATE points_batch SET remaining_points = remaining_points - ?, status = CASE WHEN remaining_points - ? = 0 THEN 'EXHAUSTED' ELSE status END WHERE id = ?",
                deducted, deducted, number(batch.get("id"))
            );
            jdbcTemplate.update(
                "UPDATE points_account SET available_points = available_points - ?, total_earned = total_earned - ?, version = version + 1 WHERE id = ? AND available_points >= ?",
                deducted, deducted, accountId, deducted
            );
            Map<String, Object> after = account(accountId);
            insertLedger(
                principal, shopId, accountId, number(batch.get("id")), null, "ADJUSTMENT", -deducted,
                number(after.get("availablePoints")), number(after.get("frozenPoints")),
                "REFUND", refundId, "CLAWBACK:REFUND:" + refundId,
                "clawback-refund:" + refundId, hash("clawback-refund:" + refundId), "退款追回订单奖励积分"
            );
        }
        jdbcTemplate.update(
            "INSERT INTO points_clawback (tenant_id, shop_id, account_id, sales_order_id, refund_id, requested_points, deducted_points, outstanding_points, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            principal.tenantId(), shopId, accountId, orderId, refundId, requested, deducted, outstanding,
            outstanding == 0 ? "SETTLED" : "OUTSTANDING"
        );
        return requested;
    }

    @Transactional
    public Map<String, Object> grant(
        TenantPrincipal principal,
        GrantCommand command,
        String idempotencyKey,
        String requestHash
    ) {
        long shopId = accessService.requireShopPermission(principal, command.shopId(), "points:manage");
        requireMember(principal.tenantId(), shopId, command.memberId());
        long batchId = grantInternal(
            principal, shopId, command.memberId(), command.points(), command.sourceType(),
            command.referenceType(), command.referenceId(), command.reason(),
            idempotencyKey, requestHash, command.validityMonths(), null
        );
        return jdbcTemplate.queryForMap(
            "SELECT id, batch_no AS batchNo, granted_points AS grantedPoints, remaining_points AS remainingPoints, expires_at AS expiresAt, status FROM points_batch WHERE id = ?",
            batchId
        );
    }

    @Transactional
    public Map<String, Object> checkin(
        TenantPrincipal principal,
        String idempotencyKey,
        String requestHash
    ) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> taskRows = jdbcTemplate.queryForList(
            """
            SELECT id, cycle_days AS cycleDays, daily_rewards_json AS dailyRewards,
                   cycle_bonus_points AS cycleBonusPoints, repeat_cycle AS repeatCycle
            FROM points_task
            WHERE tenant_id = ? AND (shop_id IS NULL OR shop_id = ?)
              AND task_type = 'CHECKIN' AND status = 'ACTIVE'
            ORDER BY CASE WHEN shop_id = ? THEN 0 ELSE 1 END, id LIMIT 1
            """,
            principal.tenantId(), principal.homeShopId(), principal.homeShopId()
        );
        if (taskRows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "当前未开放签到任务");
        Map<String, Object> task = taskRows.getFirst();
        long taskId = number(task.get("id"));
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id, streak_day AS streakDay, cycle_day AS cycleDay, reward_points AS rewardPoints FROM member_checkin WHERE tenant_id = ? AND member_id = ? AND task_id = ? AND checkin_date = CURRENT_DATE",
            principal.tenantId(), memberId, taskId
        );
        if (!existing.isEmpty()) return existing.getFirst();
        List<Map<String, Object>> previous = jdbcTemplate.queryForList(
            "SELECT checkin_date AS checkinDate, streak_day AS streakDay FROM member_checkin WHERE tenant_id = ? AND member_id = ? AND task_id = ? ORDER BY checkin_date DESC LIMIT 1 FOR UPDATE",
            principal.tenantId(), memberId, taskId
        );
        int streak = 1;
        if (!previous.isEmpty()
            && localDate(previous.getFirst().get("checkinDate")).equals(LocalDate.now().minusDays(1))) {
            streak = integer(previous.getFirst().get("streakDay")) + 1;
        }
        int cycleDays = integer(task.get("cycleDays"));
        int cycleDay = ((streak - 1) % cycleDays) + 1;
        List<Integer> rewards = integerList(task.get("dailyRewards"));
        int reward = PointsPolicy.checkinReward(
            rewards, cycleDay, integer(task.get("cycleBonusPoints"))
        );
        long batchId = grantInternal(
            principal, principal.homeShopId(), memberId, reward, "CHECKIN", "POINTS_TASK",
            taskId, "每日签到第 " + cycleDay + " 天", idempotencyKey + ":grant",
            requestHash, null, null
        );
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int finalStreak = streak;
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO member_checkin (
                    tenant_id, member_id, task_id, checkin_date, streak_day,
                    cycle_day, reward_points, points_batch_id, idempotency_key
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, memberId);
            statement.setLong(3, taskId);
            statement.setInt(4, finalStreak);
            statement.setInt(5, cycleDay);
            statement.setInt(6, reward);
            statement.setLong(7, batchId);
            statement.setString(8, idempotencyKey);
            return statement;
        }, keyHolder);
        return Map.of(
            "id", generated(keyHolder, "签到记录创建失败"),
            "streakDay", streak,
            "cycleDay", cycleDay,
            "rewardPoints", reward
        );
    }

    @Transactional
    public long reserve(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        String referenceType,
        long referenceId,
        long points,
        String idempotencyKey,
        String requestHash
    ) {
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT id FROM points_reservation WHERE tenant_id = ? AND idempotency_key = ?",
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("id"));
        long accountId = ensureAccount(principal.tenantId(), memberId);
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT available_points AS availablePoints, frozen_points AS frozenPoints FROM points_account WHERE id = ? FOR UPDATE",
            accountId
        );
        List<Map<String, Object>> batches = jdbcTemplate.queryForList(
            """
            SELECT id, remaining_points AS remainingPoints, frozen_points AS frozenPoints
            FROM points_batch
            WHERE account_id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP(3)
              AND remaining_points > frozen_points
            ORDER BY expires_at, id FOR UPDATE
            """,
            accountId
        );
        List<PointsPolicy.BatchBalance> balances = batches.stream()
            .map(row -> new PointsPolicy.BatchBalance(
                number(row.get("id")), number(row.get("remainingPoints")), number(row.get("frozenPoints"))
            )).toList();
        List<PointsPolicy.Allocation> allocations;
        try {
            allocations = PointsPolicy.allocateFefo(points, balances);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.getMessage());
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO points_reservation (
                    tenant_id, shop_id, account_id, reference_type, reference_id,
                    requested_points, status, idempotency_key, request_hash, version, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, 'RESERVED', ?, ?, 1, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, accountId);
            statement.setString(4, referenceType);
            statement.setLong(5, referenceId);
            statement.setLong(6, points);
            statement.setString(7, idempotencyKey);
            statement.setString(8, requestHash);
            statement.setLong(9, principal.accountId());
            return statement;
        }, keyHolder);
        long reservationId = generated(keyHolder, "积分冻结失败");
        for (PointsPolicy.Allocation allocation : allocations) {
            jdbcTemplate.update(
                "INSERT INTO points_reservation_allocation (reservation_id, batch_id, allocated_points) VALUES (?, ?, ?)",
                reservationId, allocation.batchId(), allocation.points()
            );
            requireChanged(jdbcTemplate.update(
                "UPDATE points_batch SET frozen_points = frozen_points + ? WHERE id = ? AND remaining_points - frozen_points >= ?",
                allocation.points(), allocation.batchId(), allocation.points()
            ), "积分批次余额已变化");
        }
        requireChanged(jdbcTemplate.update(
            "UPDATE points_account SET available_points = available_points - ?, frozen_points = frozen_points + ?, version = version + 1 WHERE id = ? AND available_points >= ?",
            points, points, accountId, points
        ), "可用积分已变化");
        insertLedger(
            principal, shopId, accountId, null, reservationId, "RESERVE", -points,
            number(account.get("availablePoints")) - points,
            number(account.get("frozenPoints")) + points,
            referenceType, referenceId, "RESERVE:" + referenceType + ":" + referenceId,
            idempotencyKey + ":ledger", requestHash, "积分冻结"
        );
        return reservationId;
    }

    @Transactional
    public int consumeReference(
        TenantPrincipal principal,
        long shopId,
        String referenceType,
        long referenceId
    ) {
        List<Map<String, Object>> rows = reservation(
            principal.tenantId(), referenceType, referenceId, "RESERVED"
        );
        if (rows.isEmpty()) return 0;
        Map<String, Object> reservation = rows.getFirst();
        long reservationId = number(reservation.get("id"));
        long accountId = number(reservation.get("accountId"));
        long points = number(reservation.get("requestedPoints"));
        List<Map<String, Object>> allocations = jdbcTemplate.queryForList(
            "SELECT batch_id AS batchId, allocated_points AS allocatedPoints FROM points_reservation_allocation WHERE reservation_id = ? ORDER BY id FOR UPDATE",
            reservationId
        );
        for (Map<String, Object> allocation : allocations) {
            long allocated = number(allocation.get("allocatedPoints"));
            requireChanged(jdbcTemplate.update(
                "UPDATE points_batch SET remaining_points = remaining_points - ?, frozen_points = frozen_points - ?, status = CASE WHEN remaining_points - ? = 0 THEN 'EXHAUSTED' ELSE status END WHERE id = ? AND frozen_points >= ?",
                allocated, allocated, allocated, number(allocation.get("batchId")), allocated
            ), "积分批次冻结已变化");
        }
        requireChanged(jdbcTemplate.update(
            "UPDATE points_account SET frozen_points = frozen_points - ?, total_consumed = total_consumed + ?, version = version + 1 WHERE id = ? AND frozen_points >= ?",
            points, points, accountId, points
        ), "积分账户冻结已变化");
        requireChanged(jdbcTemplate.update(
            "UPDATE points_reservation SET status = 'CONSUMED', completed_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ? AND status = 'RESERVED'",
            reservationId
        ), "积分冻结状态已变化");
        Map<String, Object> after = account(accountId);
        insertLedger(
            principal, shopId, accountId, null, reservationId, "CONSUME", -points,
            number(after.get("availablePoints")), number(after.get("frozenPoints")),
            referenceType, referenceId, "CONSUME:" + referenceType + ":" + referenceId,
            "consume-points:" + referenceType + ":" + referenceId,
            hash("consume-points:" + referenceType + ":" + referenceId), "积分核销"
        );
        return 1;
    }

    @Transactional
    public int releaseReference(
        TenantPrincipal principal,
        long shopId,
        String referenceType,
        long referenceId,
        String reason
    ) {
        List<Map<String, Object>> rows = reservation(
            principal.tenantId(), referenceType, referenceId, "RESERVED"
        );
        if (rows.isEmpty()) return 0;
        Map<String, Object> reservation = rows.getFirst();
        long reservationId = number(reservation.get("id"));
        long accountId = number(reservation.get("accountId"));
        long points = number(reservation.get("requestedPoints"));
        List<Map<String, Object>> allocations = jdbcTemplate.queryForList(
            "SELECT batch_id AS batchId, allocated_points AS allocatedPoints FROM points_reservation_allocation WHERE reservation_id = ? ORDER BY id FOR UPDATE",
            reservationId
        );
        for (Map<String, Object> allocation : allocations) {
            long allocated = number(allocation.get("allocatedPoints"));
            requireChanged(jdbcTemplate.update(
                "UPDATE points_batch SET frozen_points = frozen_points - ? WHERE id = ? AND frozen_points >= ?",
                allocated, number(allocation.get("batchId")), allocated
            ), "积分批次冻结已变化");
        }
        requireChanged(jdbcTemplate.update(
            "UPDATE points_account SET available_points = available_points + ?, frozen_points = frozen_points - ?, version = version + 1 WHERE id = ? AND frozen_points >= ?",
            points, points, accountId, points
        ), "积分账户冻结已变化");
        requireChanged(jdbcTemplate.update(
            "UPDATE points_reservation SET status = 'RELEASED', completed_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ? AND status = 'RESERVED'",
            reservationId
        ), "积分冻结状态已变化");
        Map<String, Object> after = account(accountId);
        insertLedger(
            principal, shopId, accountId, null, reservationId, "RELEASE", points,
            number(after.get("availablePoints")), number(after.get("frozenPoints")),
            referenceType, referenceId, "RELEASE:" + referenceType + ":" + referenceId,
            "release-points:" + referenceType + ":" + referenceId,
            hash("release-points:" + referenceType + ":" + referenceId), reason
        );
        return 1;
    }

    @Transactional
    public int restoreReference(
        TenantPrincipal principal,
        long shopId,
        String referenceType,
        long referenceId,
        String reason
    ) {
        accessService.requireShopPermission(principal, shopId, "points:manage");
        List<Map<String, Object>> rows = reservation(
            principal.tenantId(), referenceType, referenceId, "CONSUMED"
        );
        if (rows.isEmpty()) return 0;
        Map<String, Object> reservation = rows.getFirst();
        long reservationId = number(reservation.get("id"));
        long accountId = number(reservation.get("accountId"));
        long points = number(reservation.get("requestedPoints"));
        List<Map<String, Object>> allocations = jdbcTemplate.queryForList(
            "SELECT id, batch_id AS batchId, allocated_points AS allocatedPoints, restored_points AS restoredPoints FROM points_reservation_allocation WHERE reservation_id = ? ORDER BY id FOR UPDATE",
            reservationId
        );
        for (Map<String, Object> allocation : allocations) {
            long restore = number(allocation.get("allocatedPoints")) - number(allocation.get("restoredPoints"));
            if (restore == 0) continue;
            jdbcTemplate.update(
                "UPDATE points_batch SET remaining_points = remaining_points + ?, status = 'ACTIVE' WHERE id = ?",
                restore, number(allocation.get("batchId"))
            );
            jdbcTemplate.update(
                "UPDATE points_reservation_allocation SET restored_points = restored_points + ? WHERE id = ?",
                restore, number(allocation.get("id"))
            );
        }
        jdbcTemplate.update(
            "UPDATE points_account SET available_points = available_points + ?, version = version + 1 WHERE id = ?",
            points, accountId
        );
        requireChanged(jdbcTemplate.update(
            "UPDATE points_reservation SET status = 'RESTORED', version = version + 1 WHERE id = ? AND status = 'CONSUMED'",
            reservationId
        ), "积分消费已恢复");
        Map<String, Object> after = account(accountId);
        insertLedger(
            principal, shopId, accountId, null, reservationId, "REFUND", points,
            number(after.get("availablePoints")), number(after.get("frozenPoints")),
            referenceType, referenceId, "REFUND:" + referenceType + ":" + referenceId,
            "refund-points:" + referenceType + ":" + referenceId,
            hash("refund-points:" + referenceType + ":" + referenceId), reason
        );
        return 1;
    }

    @Transactional
    public int sendExpiryReminders(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "points:manage");
        int created = 0;
        for (int days : List.of(30, 7, 1)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT pa.id AS pointsAccountId, a.id AS recipientAccountId,
                       SUM(pb.remaining_points - pb.frozen_points) AS expiringPoints
                FROM points_account pa
                JOIN points_batch pb ON pb.account_id = pa.id
                JOIN account a ON a.member_id = pa.member_id AND a.status = 'ACTIVE'
                WHERE pa.tenant_id = ? AND pb.status = 'ACTIVE'
                  AND DATE(pb.expires_at) = DATE_ADD(CURRENT_DATE, INTERVAL ? DAY)
                  AND pb.remaining_points > pb.frozen_points
                GROUP BY pa.id, a.id
                """,
                principal.tenantId(), days
            );
            for (Map<String, Object> row : rows) {
                long pointsAccountId = number(row.get("pointsAccountId"));
                Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM points_expiry_notice WHERE tenant_id = ? AND account_id = ? AND notice_date = CURRENT_DATE AND days_before = ?",
                    Integer.class, principal.tenantId(), pointsAccountId, days
                );
                if (existing != null && existing > 0) continue;
                String eventId = outboxEventService.appendAndReturnId(
                    principal, shopId, "POINTS_ACCOUNT", String.valueOf(pointsAccountId),
                    "PointsExpiryReminder", Map.of("pointsAccountId", pointsAccountId)
                );
                int inserted = jdbcTemplate.update(
                    """
                    INSERT IGNORE INTO points_expiry_notice (
                        tenant_id, account_id, notice_date, days_before, expiring_points, event_id
                    ) VALUES (?, ?, CURRENT_DATE, ?, ?, ?)
                    """,
                    principal.tenantId(), pointsAccountId, days,
                    number(row.get("expiringPoints")), eventId
                );
                if (inserted == 0) continue;
                jdbcTemplate.update(
                    """
                    INSERT INTO notification_message (
                        tenant_id, shop_id, recipient_account_id, event_id, event_type,
                        business_type, business_id, category, channel, delivery_status,
                        external_status, title, safe_summary, action_path, status, version
                    ) VALUES (?, ?, ?, ?, 'PointsExpiryReminder', 'POINTS_ACCOUNT', ?,
                              'POINTS', 'IN_APP', 'DELIVERED', 'NOT_REQUESTED',
                              '积分即将到期', ?, '/points-store', 'UNREAD', 0)
                    """,
                    principal.tenantId(), shopId, number(row.get("recipientAccountId")), eventId,
                    String.valueOf(pointsAccountId),
                    number(row.get("expiringPoints")) + " 积分将在 " + days + " 天后到期"
                );
                created++;
            }
        }
        return created;
    }

    private long grantInternal(
        TenantPrincipal principal,
        long shopId,
        long memberId,
        long points,
        String sourceType,
        String referenceType,
        Long referenceId,
        String reason,
        String idempotencyKey,
        String requestHash,
        Integer validityMonths,
        Long sourceRuleId
    ) {
        if (points <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "发放积分必须大于 0");
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            "SELECT batch_id AS batchId FROM points_ledger WHERE tenant_id = ? AND idempotency_key = ?",
            principal.tenantId(), idempotencyKey
        );
        if (!replay.isEmpty()) return number(replay.getFirst().get("batchId"));
        long accountId = ensureAccount(principal.tenantId(), memberId);
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT available_points AS availablePoints, frozen_points AS frozenPoints FROM points_account WHERE id = ? FOR UPDATE",
            accountId
        );
        int months = validityMonths == null ? jdbcTemplate.queryForObject(
            "SELECT validity_months FROM points_rule WHERE tenant_id = ? AND rule_type = 'EARN' AND target_type = 'GLOBAL' AND status = 'ACTIVE' ORDER BY id LIMIT 1",
            Integer.class, principal.tenantId()
        ) : validityMonths;
        if (months < 1 || months > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "积分有效期必须为 1 到 60 个月");
        String batchNo = businessNo("PTS");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO points_batch (
                    tenant_id, shop_id, account_id, batch_no, source_type, source_rule_id,
                    source_reference_type, source_reference_id, granted_points,
                    remaining_points, frozen_points, earned_at, expires_at, status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP(3),
                          DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? MONTH), 'ACTIVE', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, accountId);
            statement.setString(4, batchNo);
            statement.setString(5, sourceType);
            if (sourceRuleId == null) statement.setNull(6, java.sql.Types.BIGINT);
            else statement.setLong(6, sourceRuleId);
            statement.setString(7, referenceType);
            if (referenceId == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, referenceId);
            statement.setLong(9, points);
            statement.setLong(10, points);
            statement.setInt(11, months);
            statement.setLong(12, principal.accountId());
            return statement;
        }, keyHolder);
        long batchId = generated(keyHolder, "积分批次创建失败");
        jdbcTemplate.update(
            "UPDATE points_account SET available_points = available_points + ?, total_earned = total_earned + ?, version = version + 1 WHERE id = ?",
            points, points, accountId
        );
        insertLedger(
            principal, shopId, accountId, batchId, null, "GRANT", points,
            number(account.get("availablePoints")) + points, number(account.get("frozenPoints")),
            referenceType, referenceId, "GRANT:" + batchId, idempotencyKey,
            requestHash, reason
        );
        outboxEventService.append(
            principal, shopId, "POINTS_BATCH", String.valueOf(batchId),
            "PointsGranted", Map.of("pointsBatchId", batchId)
        );
        return batchId;
    }

    private long ensureAccount(long tenantId, long memberId) {
        jdbcTemplate.update(
            "INSERT IGNORE INTO points_account (tenant_id, member_id) VALUES (?, ?)",
            tenantId, memberId
        );
        return jdbcTemplate.queryForObject(
            "SELECT id FROM points_account WHERE tenant_id = ? AND member_id = ?",
            Long.class, tenantId, memberId
        );
    }

    private List<Map<String, Object>> reservation(
        long tenantId,
        String referenceType,
        long referenceId,
        String status
    ) {
        return jdbcTemplate.queryForList(
            "SELECT id, account_id AS accountId, requested_points AS requestedPoints FROM points_reservation WHERE tenant_id = ? AND reference_type = ? AND reference_id = ? AND status = ? FOR UPDATE",
            tenantId, referenceType, referenceId, status
        );
    }

    private Map<String, Object> account(long accountId) {
        return jdbcTemplate.queryForMap(
            "SELECT available_points AS availablePoints, frozen_points AS frozenPoints FROM points_account WHERE id = ?",
            accountId
        );
    }

    private void insertLedger(
        TenantPrincipal principal,
        Long shopId,
        long accountId,
        Long batchId,
        Long reservationId,
        String entryType,
        long pointsDelta,
        long availableAfter,
        long frozenAfter,
        String referenceType,
        Long referenceId,
        String businessKey,
        String idempotencyKey,
        String requestHash,
        String reason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO points_ledger (
                tenant_id, shop_id, account_id, batch_id, reservation_id,
                entry_type, points_delta, available_after, frozen_after,
                reference_type, reference_id, business_key, idempotency_key,
                request_hash, reason, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, accountId, batchId, reservationId,
            entryType, pointsDelta, availableAfter, frozenAfter, referenceType,
            referenceId, businessKey, idempotencyKey, requestHash, reason,
            principal.accountId()
        );
    }

    private void requireMember(long tenantId, long shopId, long memberId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM member WHERE id = ? AND tenant_id = ? AND (shop_id = ? OR home_shop_id = ?) AND status = 'ACTIVE'",
            Integer.class, memberId, tenantId, shopId, shopId
        );
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在");
    }

    private List<Integer> integerList(Object json) {
        try {
            JsonNode node = objectMapper.readTree(json.toString());
            List<Integer> values = new ArrayList<>();
            node.forEach(value -> values.add(value.asInt()));
            return values;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "签到阶梯配置不可读");
        }
    }

    private LocalDate localDate(Object value) {
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(value.toString());
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private long generated(KeyHolder keyHolder, String message) {
        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        return key.longValue();
    }

    private void requireChanged(int changed, String message) {
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String businessNo(String prefix) {
        return prefix + LocalDate.now().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String hash(String value) {
        return SessionTokenCodec.sha256(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分配置无法序列化");
        }
    }

    public record GrantCommand(
        long shopId,
        long memberId,
        long points,
        String sourceType,
        String referenceType,
        Long referenceId,
        String reason,
        Integer validityMonths
    ) {
    }

    public record RuleUpdateCommand(
        BigDecimal pointsPerYuan, int pointsPerCurrency, int minimumPoints,
        int stepPoints, BigDecimal maxDiscountRatio, BigDecimal maxDiscountAmount,
        Integer singleCapPoints, Integer memberPeriodCapPoints,
        int validityMonths, String status, int version
    ) {
    }

    public record TaskUpdateCommand(
        int rewardPoints, int cycleDays, List<Integer> dailyRewards,
        int cycleBonusPoints, boolean repeatCycle, Integer memberPeriodCapPoints,
        String status, int version
    ) {
    }
}
