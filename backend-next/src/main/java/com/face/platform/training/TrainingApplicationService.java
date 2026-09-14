package com.face.platform.training;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrainingApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final OutboxEventService outboxEventService;

    public TrainingApplicationService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        OutboxEventService outboxEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.outboxEventService = outboxEventService;
    }

    public List<Map<String, Object>> courses(TenantPrincipal principal, long shopId) {
        accessService.requireShopPermission(principal, shopId, "training:view");
        return jdbcTemplate.queryForList(
            """
            SELECT id, course_code AS courseCode, revision, title,
                   safe_summary AS safeSummary, pass_score AS passScore,
                   validity_days AS validityDays, status, version,
                   created_by AS createdBy, published_at AS publishedAt,
                   retired_at AS retiredAt, updated_at AS updatedAt
            FROM training_course
            WHERE tenant_id = ? AND shop_id = ?
            ORDER BY updated_at DESC, id DESC
            """,
            principal.tenantId(), shopId
        );
    }

    @Transactional
    public Map<String, Object> createCourse(
        TenantPrincipal principal,
        long shopId,
        String courseCode,
        int revision,
        String title,
        String safeSummary,
        int passScore,
        Integer validityDays,
        String idempotencyKey
    ) {
        accessService.requireShopPermission(principal, shopId, "training:manage");
        if (passScore < 0 || passScore > 100 || revision < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "课程版本或通过分数不正确");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO training_course (
                    tenant_id, shop_id, course_code, revision, title, safe_summary,
                    pass_score, validity_days, created_by, create_idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, code(courseCode));
            statement.setInt(4, revision);
            statement.setString(5, text(title, 120, "课程名称"));
            statement.setString(6, text(safeSummary, 500, "课程摘要"));
            statement.setInt(7, passScore);
            if (validityDays == null) statement.setNull(8, java.sql.Types.INTEGER);
            else statement.setInt(8, validityDays);
            statement.setLong(9, principal.accountId());
            statement.setString(10, idempotencyKey);
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        return course(principal, shopId, id, "training:view");
    }

    @Transactional
    public Map<String, Object> transitionCourse(
        TenantPrincipal principal,
        long shopId,
        long courseId,
        int version,
        String target
    ) {
        accessService.requireShopPermission(principal, shopId, "training:manage");
        Map<String, Object> course = course(principal, shopId, courseId, "training:manage");
        String from = course.get("status").toString();
        String to = target.toUpperCase();
        if (!TrainingPolicy.canTransitionCourse(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "课程状态不允许执行该操作");
        }
        String fields = "ACTIVE".equals(to)
            ? ", published_by = ?, published_at = CURRENT_TIMESTAMP(3)"
            : ", retired_by = ?, retired_at = CURRENT_TIMESTAMP(3)";
        int changed = jdbcTemplate.update(
            "UPDATE training_course SET status = ?, version = version + 1" + fields
                + " WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = ?",
            to, principal.accountId(), courseId, principal.tenantId(), shopId, version, from
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "课程版本已变化，请刷新后重试");
        return course(principal, shopId, courseId, "training:view");
    }

    public List<Map<String, Object>> records(TenantPrincipal principal, long shopId, String status) {
        accessService.requireShopPermission(principal, shopId, "training:view");
        String statusClause = status == null || status.isBlank() ? "" : " AND tr.status = ?";
        if (statusClause.isEmpty()) return recordRows(principal.tenantId(), shopId, null, null);
        return recordRows(principal.tenantId(), shopId, null, status.toUpperCase());
    }

    public List<Map<String, Object>> myRecords(TenantPrincipal principal) {
        accessService.requirePermission(principal, "training:self");
        long staffId = accessService.requireStaffId(principal);
        return recordRows(principal.tenantId(), null, staffId, null);
    }

    @Transactional
    public Map<String, Object> assign(
        TenantPrincipal principal,
        long shopId,
        long courseId,
        long staffId,
        java.time.LocalDateTime dueAt,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "training:manage");
        Map<String, Object> course = course(principal, shopId, courseId, "training:manage");
        if (!"ACTIVE".equals(course.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只能分配已发布课程");
        }
        requireActiveStaff(principal.tenantId(), shopId, staffId);
        String recordNo = "TR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO training_record (
                    tenant_id, shop_id, record_no, course_id, staff_id,
                    assigned_by, due_at, assign_idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, recordNo);
            statement.setLong(4, courseId);
            statement.setLong(5, staffId);
            statement.setLong(6, principal.accountId());
            if (dueAt == null) statement.setNull(7, java.sql.Types.TIMESTAMP);
            else statement.setObject(7, dueAt);
            statement.setString(8, idempotencyKey);
            return statement;
        }, keyHolder);
        long recordId = keyHolder.getKey().longValue();
        history(principal, shopId, recordId, null, "ASSIGNED", null, null, idempotencyKey, requestHash);
        outboxEventService.append(principal, shopId, "TRAINING_RECORD", String.valueOf(recordId),
            "TrainingAssigned", Map.of("trainingRecordId", recordId, "staffId", staffId));
        return record(principal.tenantId(), shopId, recordId, null);
    }

    @Transactional
    public Map<String, Object> selfTransition(
        TenantPrincipal principal,
        long recordId,
        int version,
        String target,
        String evidenceSummary,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requirePermission(principal, "training:self");
        long staffId = accessService.requireStaffId(principal);
        Map<String, Object> current = record(principal.tenantId(), null, recordId, staffId);
        long shopId = number(current.get("shopId"));
        String from = current.get("status").toString();
        String to = target.toUpperCase();
        if (!("IN_PROGRESS".equals(to) || "SUBMITTED".equals(to))
            || !TrainingPolicy.canTransitionRecord(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "本人培训状态不允许执行该操作");
        }
        String extra = "IN_PROGRESS".equals(to)
            ? ", started_at = CURRENT_TIMESTAMP(3)"
            : ", submitted_at = CURRENT_TIMESTAMP(3), evidence_summary = ?";
        Object[] args = "IN_PROGRESS".equals(to)
            ? new Object[]{to, recordId, principal.tenantId(), staffId, version, from}
            : new Object[]{to, text(evidenceSummary, 500, "完成说明"), recordId, principal.tenantId(), staffId, version, from};
        int changed = jdbcTemplate.update(
            "UPDATE training_record SET status = ?, version = version + 1" + extra
                + " WHERE id = ? AND tenant_id = ? AND staff_id = ? AND version = ? AND status = ?",
            args
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "培训记录版本已变化，请刷新后重试");
        history(principal, shopId, recordId, from, to, null,
            "SUBMITTED".equals(to) ? evidenceSummary : null, idempotencyKey, requestHash);
        return record(principal.tenantId(), shopId, recordId, staffId);
    }

    @Transactional
    public Map<String, Object> verify(
        TenantPrincipal principal,
        long shopId,
        long recordId,
        int version,
        int score,
        String safeReason,
        String idempotencyKey,
        String requestHash
    ) {
        accessService.requireShopPermission(principal, shopId, "training:verify");
        Map<String, Object> current = record(principal.tenantId(), shopId, recordId, null);
        if (!"SUBMITTED".equals(current.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已提交培训可以验证");
        }
        Long traineeAccountId = jdbcTemplate.query(
            "SELECT id FROM account WHERE tenant_id = ? AND staff_id = ? AND status = 'ACTIVE' LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null,
            principal.tenantId(), number(current.get("staffId"))
        );
        if (!TrainingPolicy.canVerify(principal.accountId(), traineeAccountId)) {
            throw new ApiException(HttpStatus.CONFLICT, "受训人不能验证自己的培训结果");
        }
        int passScore = ((Number) current.get("passScore")).intValue();
        String target = TrainingPolicy.passes(score, passScore) ? "PASSED" : "FAILED";
        String certificateNo = "PASSED".equals(target)
            ? "CERT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase()
            : null;
        Integer validityDays = current.get("validityDays") instanceof Number n ? n.intValue() : null;
        LocalDate validUntil = "PASSED".equals(target) && validityDays != null
            ? LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(validityDays) : null;
        int changed = jdbcTemplate.update(
            """
            UPDATE training_record
            SET status = ?, score = ?, verified_by = ?, verified_at = CURRENT_TIMESTAMP(3),
                certificate_no = ?, valid_until = ?, version = version + 1
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND version = ? AND status = 'SUBMITTED'
            """,
            target, score, principal.accountId(), certificateNo, validUntil,
            recordId, principal.tenantId(), shopId, version
        );
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "培训记录版本已变化，请刷新后重试");
        history(principal, shopId, recordId, "SUBMITTED", target, score, safeReason, idempotencyKey, requestHash);
        outboxEventService.append(principal, shopId, "TRAINING_RECORD", String.valueOf(recordId),
            "TrainingVerified", Map.of("trainingRecordId", recordId));
        return record(principal.tenantId(), shopId, recordId, null);
    }

    private List<Map<String, Object>> recordRows(long tenantId, Long shopId, Long staffId, String status) {
        StringBuilder where = new StringBuilder(" WHERE tr.tenant_id = ?");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        if (shopId != null) { where.append(" AND tr.shop_id = ?"); args.add(shopId); }
        if (staffId != null) { where.append(" AND tr.staff_id = ?"); args.add(staffId); }
        if (status != null) { where.append(" AND tr.status = ?"); args.add(status); }
        return jdbcTemplate.queryForList(baseRecordSql() + where + " ORDER BY tr.updated_at DESC, tr.id DESC", args.toArray());
    }

    private Map<String, Object> record(long tenantId, Long shopId, long recordId, Long staffId) {
        StringBuilder where = new StringBuilder(" WHERE tr.id = ? AND tr.tenant_id = ?");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(List.of(recordId, tenantId));
        if (shopId != null) { where.append(" AND tr.shop_id = ?"); args.add(shopId); }
        if (staffId != null) { where.append(" AND tr.staff_id = ?"); args.add(staffId); }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(baseRecordSql() + where + " LIMIT 1", args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "培训记录不存在或不在当前范围");
        return rows.getFirst();
    }

    private String baseRecordSql() {
        return """
            SELECT tr.id, tr.shop_id AS shopId, tr.record_no AS recordNo,
                   tr.course_id AS courseId, tc.course_code AS courseCode,
                   tc.revision AS courseRevision, tc.title AS courseTitle,
                   tc.safe_summary AS courseSummary, tc.pass_score AS passScore,
                   tc.validity_days AS validityDays, tr.staff_id AS staffId,
                   st.staff_no AS staffNo, st.name AS staffName, tr.status,
                   tr.due_at AS dueAt, tr.started_at AS startedAt,
                   tr.submitted_at AS submittedAt, tr.score,
                   tr.evidence_summary AS evidenceSummary,
                   tr.certificate_no AS certificateNo, tr.valid_until AS validUntil,
                   tr.version, tr.updated_at AS updatedAt
            FROM training_record tr
            JOIN training_course tc ON tc.id = tr.course_id AND tc.tenant_id = tr.tenant_id
            JOIN staff st ON st.id = tr.staff_id AND st.tenant_id = tr.tenant_id
            """;
    }

    private Map<String, Object> course(TenantPrincipal principal, long shopId, long courseId, String permission) {
        accessService.requireShopPermission(principal, shopId, permission);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, course_code AS courseCode, revision, title,
                   safe_summary AS safeSummary, pass_score AS passScore,
                   validity_days AS validityDays, status, version
            FROM training_course
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            LIMIT 1
            """,
            courseId, principal.tenantId(), shopId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "课程不存在或不在当前门店");
        return rows.getFirst();
    }

    private void requireActiveStaff(long tenantId, long shopId, long staffId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM staff st
            JOIN staff_shop_assignment ssa ON ssa.staff_id = st.id AND ssa.tenant_id = st.tenant_id
            WHERE st.id = ? AND st.tenant_id = ? AND st.status = 'ACTIVE'
              AND ssa.shop_id = ? AND ssa.status = 'ACTIVE'
              AND ssa.effective_from <= CURRENT_DATE
              AND (ssa.effective_to IS NULL OR ssa.effective_to >= CURRENT_DATE)
            """,
            Integer.class, staffId, tenantId, shopId
        );
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "员工未在当前门店有效任职");
    }

    private void history(
        TenantPrincipal principal, long shopId, long recordId, String from, String to,
        Integer score, String reason, String key, String hash
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO training_record_history (
                tenant_id, shop_id, training_record_id, from_status, to_status,
                actor_account_id, score, safe_reason, idempotency_key, request_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), shopId, recordId, from, to, principal.accountId(),
            score, reason == null ? null : text(reason, 500, "说明"), key, hash
        );
    }

    private String code(String value) {
        String safe = value == null ? "" : value.trim().toUpperCase();
        if (!safe.matches("[A-Z0-9_-]{2,40}")) throw new ApiException(HttpStatus.BAD_REQUEST, "课程编码格式不正确");
        return safe;
    }

    private String text(String value, int max, String label) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空或过长");
        return safe;
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalStateException("培训数据标识不完整");
        return number.longValue();
    }
}
