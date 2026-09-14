package com.face.platform.sc6;

import com.face.platform.api.ApiException;
import com.face.platform.aftersale.AfterSaleApplicationService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class Sc6ReviewApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final AfterSaleApplicationService afterSaleService;

    public Sc6ReviewApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        AfterSaleApplicationService afterSaleService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.afterSaleService = afterSaleService;
    }

    public List<Map<String, Object>> listMine(TenantPrincipal principal) {
        long memberId = accessService.requireMemberId(principal);
        return jdbcTemplate.queryForList(
            """
            SELECT r.id, r.shop_id AS shopId, r.service_record_id AS serviceRecordId,
                   r.confirmation_id AS confirmationId, r.staff_rating AS staffRating,
                   r.effect_rating AS effectRating, r.environment_rating AS environmentRating,
                   r.average_rating AS averageRating, r.visibility,
                   r.moderation_status AS moderationStatus,
                   r.current_version_no AS currentVersionNo, r.wants_contact AS wantsContact,
                   r.reply_text AS replyText, r.after_sale_case_id AS afterSaleCaseId,
                   r.deleted_at AS deletedAt, r.version, r.created_at AS createdAt,
                   rv.content
            FROM service_review r
            JOIN service_review_version rv
              ON rv.review_id = r.id AND rv.version_no = r.current_version_no
            WHERE r.tenant_id = ? AND r.member_id = ?
            ORDER BY r.created_at DESC, r.id DESC
            """,
            principal.tenantId(), memberId
        );
    }

    public List<Map<String, Object>> moderationQueue(
        TenantPrincipal principal, long shopId, String status
    ) {
        accessService.requireShopPermission(principal, shopId, "review:moderate");
        String normalized = status == null || status.isBlank()
            ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "PENDING", "APPROVED", "HIDDEN", "NOT_REQUIRED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "评价审核状态不正确");
        }
        String filter = "ALL".equals(normalized) ? "" : " AND r.moderation_status = ?";
        Object[] args = "ALL".equals(normalized)
            ? new Object[]{principal.tenantId(), shopId}
            : new Object[]{principal.tenantId(), shopId, normalized};
        return jdbcTemplate.queryForList(
            """
            SELECT r.id, r.member_id AS memberId, m.name AS memberName,
                   r.service_record_id AS serviceRecordId,
                   r.staff_rating AS staffRating, r.effect_rating AS effectRating,
                   r.environment_rating AS environmentRating,
                   r.average_rating AS averageRating, r.visibility,
                   r.moderation_status AS moderationStatus,
                   r.current_version_no AS currentVersionNo, r.wants_contact AS wantsContact,
                   r.reply_text AS replyText, r.hidden_reason AS hiddenReason,
                   r.after_sale_case_id AS afterSaleCaseId, r.deleted_at AS deletedAt,
                   r.version, rv.content, r.created_at AS createdAt
            FROM service_review r
            JOIN member m ON m.id = r.member_id
            JOIN service_review_version rv
              ON rv.review_id = r.id AND rv.version_no = r.current_version_no
            WHERE r.tenant_id = ? AND r.shop_id = ?%s
            ORDER BY r.created_at DESC, r.id DESC LIMIT 200
            """.formatted(filter),
            args
        );
    }

    @Transactional
    public Map<String, Object> save(
        TenantPrincipal principal,
        long serviceRecordId,
        Integer expectedVersion,
        int staffRating,
        int effectRating,
        int environmentRating,
        String visibility,
        String content,
        boolean wantsContact,
        String idempotencyKey
    ) {
        long memberId = accessService.requireMemberId(principal);
        BigDecimal average = Sc6Policy.average(staffRating, effectRating, environmentRating);
        String safeVisibility = enumValue(
            visibility, Set.of("PUBLIC", "SHOP_ONLY"), "评价可见范围"
        );
        String safeContent = trim(content, 1000);
        String key = requiredKey(idempotencyKey);
        List<Map<String, Object>> contextRows = jdbcTemplate.queryForList(
            """
            SELECT sr.id AS serviceRecordId, sr.shop_id AS shopId,
                   cc.id AS confirmationId, cc.status AS confirmationStatus,
                   cc.finalized_at AS finalizedAt, so.id AS orderId
            FROM service_record sr
            JOIN customer_confirmation cc
              ON cc.service_record_id = sr.id AND cc.tenant_id = sr.tenant_id
            LEFT JOIN sales_order so
              ON so.service_record_id = sr.id AND so.tenant_id = sr.tenant_id
            WHERE sr.id = ? AND sr.tenant_id = ? AND sr.member_id = ?
            ORDER BY so.id LIMIT 1
            """,
            serviceRecordId, principal.tenantId(), memberId
        );
        if (contextRows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "可评价护理记录不存在");
        Map<String, Object> context = contextRows.getFirst();
        if (!Set.of("CONFIRMED", "SYSTEM_AUTO_CONFIRMED").contains(
            context.get("confirmationStatus").toString()
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "护理完成确认后才可评价");
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id, member_id AS memberId, current_version_no AS currentVersionNo, version, deleted_at AS deletedAt, after_sale_case_id AS afterSaleCaseId FROM service_review WHERE tenant_id = ? AND service_record_id = ? FOR UPDATE",
            principal.tenantId(), serviceRecordId
        );
        long reviewId;
        int versionNo;
        String changeType;
        if (existing.isEmpty()) {
            KeyHolder holder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO service_review (
                        tenant_id, shop_id, member_id, service_record_id, confirmation_id,
                        staff_rating, effect_rating, environment_rating, average_rating,
                        visibility, moderation_status, current_version_no, wants_contact
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, number(context.get("shopId")));
                statement.setLong(3, memberId);
                statement.setLong(4, serviceRecordId);
                statement.setLong(5, number(context.get("confirmationId")));
                statement.setInt(6, staffRating);
                statement.setInt(7, effectRating);
                statement.setInt(8, environmentRating);
                statement.setBigDecimal(9, average);
                statement.setString(10, safeVisibility);
                statement.setString(11, moderationStatus(safeVisibility));
                statement.setBoolean(12, wantsContact);
                return statement;
            }, holder);
            reviewId = generated(holder);
            versionNo = 1;
            changeType = "CREATED";
        } else {
            Map<String, Object> review = existing.getFirst();
            if (review.get("deletedAt") != null) {
                throw new ApiException(HttpStatus.CONFLICT, "已删除评价不能再次修改");
            }
            int currentVersion = ((Number) review.get("version")).intValue();
            if (expectedVersion == null || expectedVersion != currentVersion) {
                throw new ApiException(HttpStatus.CONFLICT, "评价版本已变化，请刷新后重试");
            }
            reviewId = number(review.get("id"));
            versionNo = ((Number) review.get("currentVersionNo")).intValue() + 1;
            int changed = jdbcTemplate.update(
                """
                UPDATE service_review
                SET staff_rating = ?, effect_rating = ?, environment_rating = ?,
                    average_rating = ?, visibility = ?, moderation_status = ?,
                    current_version_no = ?, wants_contact = ?,
                    hidden_reason = NULL, version = version + 1
                WHERE id = ? AND tenant_id = ? AND version = ? AND deleted_at IS NULL
                """,
                staffRating, effectRating, environmentRating, average,
                safeVisibility, moderationStatus(safeVisibility), versionNo, wantsContact,
                reviewId, principal.tenantId(), currentVersion
            );
            if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "评价已被修改");
            changeType = "EDITED";
        }
        jdbcTemplate.update(
            """
            INSERT INTO service_review_version (
                tenant_id, review_id, version_no, staff_rating, effect_rating,
                environment_rating, average_rating, visibility, content,
                wants_contact, change_type, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), reviewId, versionNo, staffRating, effectRating,
            environmentRating, average, safeVisibility, safeContent,
            wantsContact, changeType, principal.accountId()
        );
        if (Sc6Policy.triggersContact(
            staffRating, effectRating, environmentRating, wantsContact
        )) {
            linkAfterSale(principal, context, reviewId, key, average, wantsContact);
        }
        audit(principal, number(context.get("shopId")), reviewId, "SERVICE_REVIEW_" + changeType);
        return detail(principal.tenantId(), memberId, reviewId);
    }

    @Transactional
    public Map<String, Object> delete(
        TenantPrincipal principal, long reviewId, int expectedVersion, String idempotencyKey
    ) {
        long memberId = accessService.requireMemberId(principal);
        Map<String, Object> review = memberReview(principal, reviewId, true);
        if (review.get("deletedAt") != null) return detail(principal.tenantId(), memberId, reviewId);
        if (((Number) review.get("version")).intValue() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "评价版本已变化，请刷新后重试");
        }
        int nextVersion = ((Number) review.get("currentVersionNo")).intValue() + 1;
        jdbcTemplate.update(
            """
            INSERT INTO service_review_version (
                tenant_id, review_id, version_no, staff_rating, effect_rating,
                environment_rating, average_rating, visibility, content,
                wants_contact, change_type, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, 'DELETED', ?)
            """,
            principal.tenantId(), reviewId, nextVersion,
            review.get("staffRating"), review.get("effectRating"), review.get("environmentRating"),
            review.get("averageRating"), review.get("visibility"), review.get("wantsContact"),
            principal.accountId()
        );
        jdbcTemplate.update(
            "UPDATE service_review SET deleted_at = CURRENT_TIMESTAMP(3), current_version_no = ?, moderation_status = 'HIDDEN', version = version + 1 WHERE id = ? AND tenant_id = ? AND version = ?",
            nextVersion, reviewId, principal.tenantId(), expectedVersion
        );
        audit(principal, number(review.get("shopId")), reviewId, "SERVICE_REVIEW_DELETED");
        return detail(principal.tenantId(), memberId, reviewId);
    }

    @Transactional
    public Map<String, Object> moderate(
        TenantPrincipal principal, long shopId, long reviewId, int expectedVersion,
        String action, String note, String idempotencyKey
    ) {
        accessService.requireShopPermission(principal, shopId, "review:moderate");
        String safeAction = enumValue(action, Set.of("APPROVE", "HIDE", "REPLY"), "评价动作");
        String safeNote = trim(note, 500);
        if (("HIDE".equals(safeAction) || "REPLY".equals(safeAction)) && safeNote == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "隐藏或回复评价必须填写说明");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, version, deleted_at AS deletedAt FROM service_review WHERE id = ? AND tenant_id = ? AND shop_id = ? FOR UPDATE",
            reviewId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "评价不存在");
        if (((Number) rows.getFirst().get("version")).intValue() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "评价版本已变化，请刷新后重试");
        }
        if ("REPLY".equals(safeAction)) {
            jdbcTemplate.update(
                "UPDATE service_review SET reply_text = ?, replied_by = ?, replied_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ? AND version = ?",
                safeNote, principal.accountId(), reviewId, expectedVersion
            );
        } else {
            jdbcTemplate.update(
                "UPDATE service_review SET moderation_status = ?, hidden_reason = ?, version = version + 1 WHERE id = ? AND version = ?",
                "APPROVE".equals(safeAction) ? "APPROVED" : "HIDDEN",
                "HIDE".equals(safeAction) ? safeNote : null,
                reviewId, expectedVersion
            );
        }
        jdbcTemplate.update(
            "INSERT INTO service_review_moderation_log (tenant_id, shop_id, review_id, action, reason, idempotency_key, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
            principal.tenantId(), shopId, reviewId, safeAction, safeNote,
            requiredKey(idempotencyKey), principal.accountId()
        );
        audit(principal, shopId, reviewId, "SERVICE_REVIEW_" + safeAction);
        return jdbcTemplate.queryForMap(
            "SELECT id, moderation_status AS moderationStatus, reply_text AS replyText, hidden_reason AS hiddenReason, version FROM service_review WHERE id = ?",
            reviewId
        );
    }

    private void linkAfterSale(
        TenantPrincipal principal, Map<String, Object> context, long reviewId,
        String idempotencyKey, BigDecimal average, boolean wantsContact
    ) {
        List<Long> linked = jdbcTemplate.queryForList(
            "SELECT after_sale_case_id FROM service_review WHERE id = ? AND after_sale_case_id IS NOT NULL",
            Long.class, reviewId
        );
        if (!linked.isEmpty()) return;
        String summary = wantsContact
            ? "会员评价后希望门店联系，平均分 " + average
            : "会员低分评价自动触发售后，平均分 " + average;
        String hash = SessionTokenCodec.sha256(reviewId + "|" + summary);
        Map<String, Object> afterSale = afterSaleService.create(
            principal, number(context.get("shopId")), accessService.requireMemberId(principal),
            context.get("orderId") == null ? null : number(context.get("orderId")),
            number(context.get("serviceRecordId")), "SERVICE_QUALITY", "HIGH", summary,
            idempotencyKey + ":review-aftersale", hash
        );
        long caseId = number(afterSale.get("id"));
        jdbcTemplate.update(
            "UPDATE after_sale_case SET origin_type = ?, entry_deadline_at = DATE_ADD(?, INTERVAL 7 DAY) WHERE id = ?",
            wantsContact ? "CONTACT_REQUEST" : "LOW_SCORE_REVIEW",
            context.get("finalizedAt"), caseId
        );
        jdbcTemplate.update(
            "UPDATE service_review SET after_sale_case_id = ? WHERE id = ?",
            caseId, reviewId
        );
    }

    private Map<String, Object> memberReview(
        TenantPrincipal principal, long reviewId, boolean lock
    ) {
        long memberId = accessService.requireMemberId(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, shop_id AS shopId, member_id AS memberId, staff_rating AS staffRating, effect_rating AS effectRating, environment_rating AS environmentRating, average_rating AS averageRating, visibility, wants_contact AS wantsContact, current_version_no AS currentVersionNo, version, deleted_at AS deletedAt FROM service_review WHERE id = ? AND tenant_id = ? AND member_id = ?" + (lock ? " FOR UPDATE" : ""),
            reviewId, principal.tenantId(), memberId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "评价不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private Map<String, Object> detail(long tenantId, long memberId, long reviewId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT r.id, r.shop_id AS shopId, r.service_record_id AS serviceRecordId,
                   r.confirmation_id AS confirmationId, r.staff_rating AS staffRating,
                   r.effect_rating AS effectRating, r.environment_rating AS environmentRating,
                   r.average_rating AS averageRating, r.visibility,
                   r.moderation_status AS moderationStatus,
                   r.current_version_no AS currentVersionNo, r.wants_contact AS wantsContact,
                   r.reply_text AS replyText, r.after_sale_case_id AS afterSaleCaseId,
                   r.deleted_at AS deletedAt, r.version, rv.content
            FROM service_review r
            JOIN service_review_version rv ON rv.review_id = r.id AND rv.version_no = r.current_version_no
            WHERE r.id = ? AND r.tenant_id = ? AND r.member_id = ?
            """,
            reviewId, tenantId, memberId
        );
    }

    private String moderationStatus(String visibility) {
        return "PUBLIC".equals(visibility) ? "PENDING" : "NOT_REQUIRED";
    }

    private String enumValue(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不正确");
        return normalized;
    }

    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "必须提供有效幂等键");
        }
        return value.trim();
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "内容过长");
        return normalized;
    }

    private long generated(KeyHolder holder) {
        Number key = holder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "评价创建失败");
        return key.longValue();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private void audit(TenantPrincipal principal, long shopId, long id, String action) {
        jdbcTemplate.update(
            "INSERT INTO audit_log (tenant_id, shop_id, account_id, action, entity_type, entity_id) VALUES (?, ?, ?, ?, 'SERVICE_REVIEW', ?)",
            principal.tenantId(), shopId, principal.accountId(), action, id
        );
    }
}
