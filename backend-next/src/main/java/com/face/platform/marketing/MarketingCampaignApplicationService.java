package com.face.platform.marketing;

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

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MarketingCampaignApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final MarketingAudienceQueryPort audienceQueryPort;
    private final MarketingDeliveryPort deliveryPort;
    private final OutboxEventService outboxEventService;

    public MarketingCampaignApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        MarketingAudienceQueryPort audienceQueryPort,
        MarketingDeliveryPort deliveryPort,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.audienceQueryPort = audienceQueryPort;
        this.deliveryPort = deliveryPort;
        this.outboxEventService = outboxEventService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(
        TenantPrincipal principal, long shopId, String status, int page, int pageSize
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:view");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String normalized = optionalStatus(status);
        String predicate = normalized == null ? "" : " AND mc.status = ?";
        List<Object> args = new ArrayList<>(List.of(principal.tenantId(), shopId));
        if (normalized != null) args.add(normalized);
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT mc.id, mc.campaign_no AS campaignNo, mc.title, mc.safe_summary AS safeSummary,
                   mc.channel, mc.action_path AS actionPath, mc.scheduled_at AS scheduledAt,
                   mc.status, mc.version, mc.created_by AS createdBy,
                   mc.submitted_by AS submittedBy, mc.submitted_at AS submittedAt,
                   mc.approved_by AS approvedBy, mc.approved_at AS approvedAt,
                   mc.executed_by AS executedBy, mc.executed_at AS executedAt,
                   mc.completed_at AS completedAt, mc.cancel_reason AS cancelReason,
                   mc.created_at AS createdAt, mc.updated_at AS updatedAt,
                   (SELECT COUNT(*) FROM marketing_campaign_audience a
                    WHERE a.tenant_id = mc.tenant_id AND a.campaign_id = mc.id) AS audienceCount,
                   (SELECT COUNT(*) FROM marketing_delivery_attempt d
                    WHERE d.tenant_id = mc.tenant_id AND d.campaign_id = mc.id
                      AND d.status = 'DELIVERED') AS deliveredCount
            FROM marketing_campaign mc
            WHERE mc.tenant_id = ? AND mc.shop_id = ?%s
            ORDER BY mc.id DESC LIMIT ? OFFSET ?
            """.formatted(predicate),
            args.toArray()
        );
        List<Object> countArgs = new ArrayList<>(List.of(principal.tenantId(), shopId));
        if (normalized != null) countArgs.add(normalized);
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM marketing_campaign mc WHERE mc.tenant_id = ? AND mc.shop_id = ?" + predicate,
            Long.class, countArgs.toArray()
        );
        return Map.of(
            "records", records, "total", total == null ? 0 : total,
            "page", safePage, "pageSize", safeSize
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(TenantPrincipal principal, long shopId, long campaignId) {
        accessService.requireShopPermission(principal, shopId, "marketing:view");
        Map<String, Object> campaign = requireCampaign(principal, shopId, campaignId, false);
        Map<String, Object> result = new LinkedHashMap<>(campaign);
        result.put("history", jdbcTemplate.queryForList(
            """
            SELECT id, from_status AS fromStatus, to_status AS toStatus,
                   actor_account_id AS actorAccountId, reason, created_at AS createdAt
            FROM marketing_campaign_status_history
            WHERE tenant_id = ? AND shop_id = ? AND campaign_id = ?
            ORDER BY id
            """,
            principal.tenantId(), shopId, campaignId
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal, long shopId, CreateRequest request,
        String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:manage");
        if (request == null) throw new ApiException(HttpStatus.BAD_REQUEST, "活动请求不能为空");
        String key = key(idempotencyKey);
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, create_request_hash AS requestHash
            FROM marketing_campaign
            WHERE tenant_id = ? AND created_by = ? AND create_idempotency_key = ?
            """,
            principal.tenantId(), principal.accountId(), key
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().get("requestHash").toString())) {
                throw new ApiException(HttpStatus.CONFLICT, "该幂等键已用于不同的活动请求");
            }
            return detail(principal, shopId, number(replay.getFirst().get("id")));
        }
        String title = required(request.title(), "活动标题", 120);
        String summary = required(request.safeSummary(), "安全摘要", 500);
        String channel = channel(request.channel());
        String actionPath = optional(request.actionPath(), 255);
        String campaignNo = "MK" + System.currentTimeMillis()
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO marketing_campaign (
                    tenant_id, shop_id, campaign_no, title, safe_summary, channel,
                    action_path, scheduled_at, created_by,
                    create_idempotency_key, create_request_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, campaignNo);
            statement.setString(4, title);
            statement.setString(5, summary);
            statement.setString(6, channel);
            statement.setString(7, actionPath);
            statement.setObject(8, request.scheduledAt());
            statement.setLong(9, principal.accountId());
            statement.setString(10, key);
            statement.setString(11, requestHash);
            return statement;
        }, holder);
        Number generated = holder.getKey();
        if (generated == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "活动创建失败");
        long campaignId = generated.longValue();
        history(principal, shopId, campaignId, null, "DRAFT", null, key, requestHash);
        audit(principal, shopId, campaignId, "MARKETING_CAMPAIGN_CREATED", "DRAFT");
        return detail(principal, shopId, campaignId);
    }

    @Transactional
    public Map<String, Object> update(
        TenantPrincipal principal, long shopId, long campaignId, int version,
        CreateRequest request
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:manage");
        Map<String, Object> row = requireCampaign(principal, shopId, campaignId, true);
        requireVersion(row, version);
        if (!"DRAFT".equals(row.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有草稿活动可以修改");
        }
        int changed = jdbcTemplate.update(
            """
            UPDATE marketing_campaign
            SET title = ?, safe_summary = ?, channel = ?, action_path = ?,
                scheduled_at = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND status = 'DRAFT' AND version = ?
            """,
            required(request.title(), "活动标题", 120),
            required(request.safeSummary(), "安全摘要", 500),
            channel(request.channel()), optional(request.actionPath(), 255), request.scheduledAt(),
            campaignId, principal.tenantId(), shopId, version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "活动版本已变化");
        audit(principal, shopId, campaignId, "MARKETING_CAMPAIGN_UPDATED", "DRAFT");
        return detail(principal, shopId, campaignId);
    }

    @Transactional
    public Map<String, Object> submit(
        TenantPrincipal principal, long shopId, long campaignId, int version,
        String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:manage");
        Map<String, Object> row = requireCampaign(principal, shopId, campaignId, true);
        requireVersion(row, version);
        if (!MarketingCampaignPolicy.channelAvailable(row.get("channel").toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "该营销通道尚未配置，不能提交执行");
        }
        transition(principal, shopId, campaignId, row, "PENDING_APPROVAL", null,
            idempotencyKey, requestHash,
            "submitted_by = ?, submitted_at = CURRENT_TIMESTAMP(3)", principal.accountId());
        return detail(principal, shopId, campaignId);
    }

    @Transactional
    public Map<String, Object> decide(
        TenantPrincipal principal, long shopId, long campaignId, int version,
        String action, String reason, String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:approve");
        Map<String, Object> row = requireCampaign(principal, shopId, campaignId, true);
        requireVersion(row, version);
        if (!MarketingCampaignPolicy.canApprove(
            principal.accountId(), number(row.get("createdBy")), number(row.get("submittedBy"))
        )) {
            throw new ApiException(HttpStatus.FORBIDDEN, "活动创建人和提交人不能审批本活动");
        }
        String normalized = required(action, "审批动作", 20).toUpperCase(Locale.ROOT);
        String target = switch (normalized) {
            case "APPROVE", "APPROVED" -> "APPROVED";
            case "REJECT", "REJECTED" -> "REJECTED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "审批动作不正确");
        };
        String safeReason = optional(reason, 500);
        if ("REJECTED".equals(target) && safeReason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "拒绝活动必须填写原因");
        }
        String extraSql = "APPROVED".equals(target)
            ? "approved_by = ?, approved_at = CURRENT_TIMESTAMP(3)"
            : "approved_by = ?, approved_at = CURRENT_TIMESTAMP(3)";
        transition(principal, shopId, campaignId, row, target, safeReason,
            idempotencyKey, requestHash, extraSql, principal.accountId());
        return detail(principal, shopId, campaignId);
    }

    @Transactional
    public Map<String, Object> cancel(
        TenantPrincipal principal, long shopId, long campaignId, int version,
        String reason, String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:manage");
        Map<String, Object> row = requireCampaign(principal, shopId, campaignId, true);
        requireVersion(row, version);
        String safeReason = required(reason, "取消原因", 500);
        transition(principal, shopId, campaignId, row, "CANCELLED", safeReason,
            idempotencyKey, requestHash,
            "cancelled_by = ?, cancelled_at = CURRENT_TIMESTAMP(3), cancel_reason = ?",
            principal.accountId(), safeReason);
        return detail(principal, shopId, campaignId);
    }

    @Transactional
    public Map<String, Object> execute(
        TenantPrincipal principal, long shopId, long campaignId, int version,
        String idempotencyKey, String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:execute");
        Map<String, Object> row = requireCampaign(principal, shopId, campaignId, true);
        requireVersion(row, version);
        String channel = row.get("channel").toString();
        if (!MarketingCampaignPolicy.channelAvailable(channel)) {
            throw new ApiException(HttpStatus.CONFLICT, "该营销通道尚未配置，禁止伪造投递成功");
        }
        Object scheduled = row.get("scheduledAt");
        if (scheduled instanceof Timestamp timestamp && timestamp.toInstant().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "活动尚未到计划执行时间");
        }
        List<MarketingAudienceQueryPort.Candidate> candidates =
            audienceQueryPort.activeMembers(principal.tenantId(), shopId);
        Map<Long, Map<String, Object>> consents = eligibleConsents(principal.tenantId(), channel, candidates);
        List<MarketingAudienceQueryPort.Candidate> eligible = candidates.stream()
            .filter(candidate -> consents.containsKey(candidate.memberId()))
            .toList();
        if (eligible.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "当前没有明确同意该通道的会员，活动未执行");
        }
        String key = key(idempotencyKey);
        transition(principal, shopId, campaignId, row, "RUNNING", null,
            key + ":RUNNING", requestHash,
            "executed_by = ?, executed_at = CURRENT_TIMESTAMP(3)", principal.accountId());

        int delivered = 0;
        for (MarketingAudienceQueryPort.Candidate candidate : eligible) {
            Map<String, Object> consent = consents.get(candidate.memberId());
            KeyHolder audienceHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO marketing_campaign_audience (
                        tenant_id, shop_id, campaign_id, member_id, recipient_account_id,
                        consent_id, consent_version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, shopId);
                statement.setLong(3, campaignId);
                statement.setLong(4, candidate.memberId());
                statement.setLong(5, candidate.recipientAccountId());
                statement.setLong(6, number(consent.get("id")));
                statement.setInt(7, ((Number) consent.get("version")).intValue());
                return statement;
            }, audienceHolder);
            long audienceId = audienceHolder.getKey().longValue();
            String eventId = outboxEventService.appendAndReturnId(
                principal, shopId, "MARKETING_CAMPAIGN", Long.toString(campaignId),
                "MarketingCampaignDelivered",
                Map.of("campaignId", campaignId, "memberId", candidate.memberId())
            );
            deliveryPort.deliverInApp(new MarketingDeliveryPort.Message(
                principal.tenantId(), shopId, campaignId, candidate.recipientAccountId(),
                eventId, row.get("title").toString(), row.get("safeSummary").toString(),
                row.get("actionPath") == null ? null : row.get("actionPath").toString()
            ));
            jdbcTemplate.update(
                """
                INSERT INTO marketing_delivery_attempt (
                    tenant_id, shop_id, campaign_id, audience_id, member_id,
                    recipient_account_id, channel, status, event_id,
                    attempt_count, delivered_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'IN_APP', 'DELIVERED', ?, 1, CURRENT_TIMESTAMP(3))
                """,
                principal.tenantId(), shopId, campaignId, audienceId, candidate.memberId(),
                candidate.recipientAccountId(), eventId
            );
            delivered++;
        }
        Map<String, Object> running = requireCampaign(principal, shopId, campaignId, true);
        transition(principal, shopId, campaignId, running, "COMPLETED", null,
            key + ":COMPLETED", requestHash,
            "completed_at = CURRENT_TIMESTAMP(3)");
        audit(principal, shopId, campaignId, "MARKETING_CAMPAIGN_DELIVERED", "COMPLETED");
        Map<String, Object> result = new LinkedHashMap<>(detail(principal, shopId, campaignId));
        result.put("audienceCount", eligible.size());
        result.put("deliveredCount", delivered);
        result.put("externalStatus", "NOT_REQUESTED");
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> audience(
        TenantPrincipal principal, long shopId, long campaignId, int page, int pageSize
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:view");
        requireCampaign(principal, shopId, campaignId, false);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, recipient_account_id AS recipientAccountId,
                   consent_id AS consentId, consent_version AS consentVersion,
                   status, frozen_at AS frozenAt
            FROM marketing_campaign_audience
            WHERE tenant_id = ? AND shop_id = ? AND campaign_id = ?
            ORDER BY id LIMIT ? OFFSET ?
            """,
            principal.tenantId(), shopId, campaignId, safeSize, (safePage - 1) * safeSize
        );
        return Map.of("records", records, "page", safePage, "pageSize", safeSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> deliveries(
        TenantPrincipal principal, long shopId, long campaignId, int page, int pageSize
    ) {
        accessService.requireShopPermission(principal, shopId, "marketing:view");
        requireCampaign(principal, shopId, campaignId, false);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT id, audience_id AS audienceId, member_id AS memberId,
                   recipient_account_id AS recipientAccountId, channel, status,
                   attempt_count AS attemptCount, last_error_code AS lastErrorCode,
                   delivered_at AS deliveredAt, created_at AS createdAt
            FROM marketing_delivery_attempt
            WHERE tenant_id = ? AND shop_id = ? AND campaign_id = ?
            ORDER BY id LIMIT ? OFFSET ?
            """,
            principal.tenantId(), shopId, campaignId, safeSize, (safePage - 1) * safeSize
        );
        return Map.of("records", records, "page", safePage, "pageSize", safeSize);
    }

    private Map<Long, Map<String, Object>> eligibleConsents(
        long tenantId, String channel, List<MarketingAudienceQueryPort.Candidate> candidates
    ) {
        if (candidates.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(candidates.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(tenantId, channel));
        candidates.forEach(candidate -> args.add(candidate.memberId()));
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, version
            FROM member_marketing_consent
            WHERE tenant_id = ? AND channel = ? AND status = 'GRANTED'
              AND member_id IN (%s)
            """.formatted(placeholders),
            args.toArray()
        )) {
            result.put(number(row.get("memberId")), row);
        }
        return result;
    }

    private void transition(
        TenantPrincipal principal, long shopId, long campaignId,
        Map<String, Object> row, String target, String reason,
        String idempotencyKey, String requestHash,
        String extraSql, Object... extraArgs
    ) {
        String from = row.get("status").toString();
        if (!MarketingCampaignPolicy.canTransition(from, target)) {
            throw new ApiException(HttpStatus.CONFLICT, "活动状态不允许执行该操作");
        }
        int version = ((Number) row.get("version")).intValue();
        List<Object> args = new ArrayList<>();
        args.add(target);
        Collections.addAll(args, extraArgs);
        args.add(campaignId);
        args.add(principal.tenantId());
        args.add(shopId);
        args.add(from);
        args.add(version);
        int changed = jdbcTemplate.update(
            "UPDATE marketing_campaign SET status = ?, " + extraSql
                + ", version = version + 1 WHERE id = ? AND tenant_id = ? AND shop_id = ?"
                + " AND status = ? AND version = ?",
            args.toArray()
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "活动状态或版本已变化");
        history(principal, shopId, campaignId, from, target, reason,
            key(idempotencyKey), requestHash);
        audit(principal, shopId, campaignId, "MARKETING_CAMPAIGN_" + target, target);
    }

    private void history(
        TenantPrincipal principal, long shopId, long campaignId,
        String from, String to, String reason, String idempotencyKey, String requestHash
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO marketing_campaign_status_history (
                tenant_id, shop_id, campaign_id, from_status, to_status,
                actor_account_id, reason, idempotency_key, request_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, campaignId, from, to,
            principal.accountId(), reason, idempotencyKey, requestHash
        );
    }

    private Map<String, Object> requireCampaign(
        TenantPrincipal principal, long shopId, long campaignId, boolean lock
    ) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, campaign_no AS campaignNo, title, safe_summary AS safeSummary,
                   channel, action_path AS actionPath, scheduled_at AS scheduledAt,
                   status, version, created_by AS createdBy,
                   submitted_by AS submittedBy, submitted_at AS submittedAt,
                   approved_by AS approvedBy, approved_at AS approvedAt,
                   executed_by AS executedBy, executed_at AS executedAt,
                   completed_at AS completedAt, cancelled_by AS cancelledBy,
                   cancelled_at AS cancelledAt, cancel_reason AS cancelReason,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM marketing_campaign
            WHERE id = ? AND tenant_id = ? AND shop_id = ?%s
            """.formatted(suffix),
            campaignId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "营销活动不存在");
        return rows.getFirst();
    }

    private void requireVersion(Map<String, Object> row, int expected) {
        if (((Number) row.get("version")).intValue() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "活动版本冲突，请刷新后重试");
        }
    }

    private void audit(
        TenantPrincipal principal, long shopId, long campaignId, String action, String status
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, ?, 'MARKETING_CAMPAIGN', ?, JSON_OBJECT('status', ?))
            """,
            principal.tenantId(), shopId, principal.accountId(), action, campaignId, status
        );
    }

    private String channel(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!MarketingConsentPolicy.validChannel(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "营销通道不正确");
        }
        return normalized;
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED",
            "RUNNING", "COMPLETED", "CANCELLED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "活动状态不正确");
        }
        return status;
    }

    private String required(String value, String label, int max) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空且不能超过" + max + "字");
        }
        return safe;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String safe = value.trim();
        if (safe.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "字段长度超过限制");
        return safe;
    }

    private String key(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > 100 || !safe.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key 格式不正确");
        }
        return safe;
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new ApiException(HttpStatus.CONFLICT, "活动职责数据不完整");
        }
        return number.longValue();
    }

    public record CreateRequest(
        String title, String safeSummary, String channel,
        String actionPath, java.time.LocalDateTime scheduledAt
    ) {
    }
}
