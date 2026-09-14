package com.face.platform.member;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemberService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    public MemberService(JdbcTemplate jdbcTemplate, TenantAccessService tenantAccessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        Long shopId,
        String keyword,
        String status,
        int page,
        int pageSize
    ) {
        List<Long> accessibleShopIds = tenantAccessService.accessibleShopIds(principal, "member:view");
        if (shopId != null) {
            tenantAccessService.requireShopPermission(principal, shopId, "member:view");
            accessibleShopIds = List.of(shopId);
        }
        if (accessibleShopIds.isEmpty()) {
            return pageResult(List.of(), 0L, 0L, 0L, Math.max(page, 1), safePageSize(pageSize));
        }

        int safePage = Math.max(page, 1);
        int safeLimit = safePageSize(pageSize);
        int offset = (safePage - 1) * safeLimit;
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = normalizeStatus(status);

        List<Object> args = new ArrayList<>();
        String where = buildWhere(principal, accessibleShopIds, shopId, normalizedKeyword, normalizedStatus, args);
        String shopNamesScope =
            " AND msp_names.shop_id IN (" + placeholders(accessibleShopIds.size()) + ")";
        String statusProjection = shopId == null
            ? "m.status"
            : """
              COALESCE((SELECT msp_status.status
                        FROM member_shop_profile msp_status
                        WHERE msp_status.tenant_id = m.tenant_id
                          AND msp_status.member_id = m.id
                          AND msp_status.shop_id = ?
                        LIMIT 1), m.status)
              """;

        List<Object> selectArgs = new ArrayList<>();
        selectArgs.add(principal.tenantId());
        if (!shopNamesScope.isEmpty()) selectArgs.addAll(accessibleShopIds);
        if (shopId != null) selectArgs.add(shopId);
        selectArgs.addAll(args);
        selectArgs.add(safeLimit);
        selectArgs.add(offset);

        String sql = """
            SELECT m.id,
                   m.member_no AS memberNo,
                   m.global_member_no AS globalMemberNo,
                   m.name,
                   m.phone,
                   m.gender,
                   m.birthday,
                   m.source,
                   m.notes,
                   m.points,
                   m.home_shop_id AS homeShopId,
                   hs.name AS homeShopName,
                   %s AS status,
                   m.version,
                   m.created_at AS createdAt,
                   m.updated_at AS updatedAt,
                   COALESCE((SELECT ma.balance FROM member_account ma
                             WHERE ma.tenant_id = m.tenant_id AND ma.member_id = m.id
                               AND ma.account_type = 'BALANCE' LIMIT 1), 0) AS balance,
                   COALESCE((SELECT ma.balance FROM member_account ma
                             WHERE ma.tenant_id = m.tenant_id AND ma.member_id = m.id
                               AND ma.account_type = 'GIFT_BALANCE' LIMIT 1), 0) AS giftBalance,
                   COALESCE((SELECT SUM(msp_visits.visit_count) FROM member_shop_profile msp_visits
                             WHERE msp_visits.tenant_id = m.tenant_id AND msp_visits.member_id = m.id), 0) AS visitCount,
                   (SELECT MAX(msp_visits.last_visit_at) FROM member_shop_profile msp_visits
                    WHERE msp_visits.tenant_id = m.tenant_id AND msp_visits.member_id = m.id) AS lastVisitAt,
                   (SELECT GROUP_CONCAT(DISTINCT s_names.name ORDER BY s_names.name SEPARATOR '、')
                    FROM member_shop_profile msp_names
                    JOIN shop s_names ON s_names.id = msp_names.shop_id
                    WHERE msp_names.tenant_id = ?
                      AND msp_names.member_id = m.id
                      AND msp_names.status = 'ACTIVE'%s) AS shopNames
            FROM member m
            JOIN shop hs ON hs.id = m.home_shop_id
            %s
            ORDER BY m.updated_at DESC, m.id DESC
            LIMIT ? OFFSET ?
            """.formatted(statusProjection, shopNamesScope, where);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, selectArgs.toArray());
        long total = count(where, args);
        long active = count(buildWhere(principal, accessibleShopIds, shopId, normalizedKeyword, "ACTIVE", new ArrayList<>()),
            buildWhereArgs(principal, accessibleShopIds, shopId, normalizedKeyword, "ACTIVE"));
        long inactive = count(buildWhere(principal, accessibleShopIds, shopId, normalizedKeyword, "INACTIVE", new ArrayList<>()),
            buildWhereArgs(principal, accessibleShopIds, shopId, normalizedKeyword, "INACTIVE"));
        return pageResult(records, total, active, inactive, safePage, safeLimit);
    }

    public Map<String, Object> detail(TenantPrincipal principal, long memberId, Long shopId) {
        List<Long> scope = shopId == null
            ? tenantAccessService.accessibleShopIds(principal, "member:view")
            : List.of(tenantAccessService.requireShopPermission(principal, shopId, "member:view"));
        requireMemberInScope(principal, memberId, scope, shopId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT m.id, m.member_no AS memberNo, m.global_member_no AS globalMemberNo,
                   m.name, m.phone, m.gender, m.birthday, m.avatar_url AS avatarUrl,
                   m.source, m.notes, m.points, m.home_shop_id AS homeShopId,
                   hs.name AS homeShopName, m.status, m.version,
                   m.created_at AS createdAt, m.updated_at AS updatedAt
            FROM member m
            JOIN shop hs ON hs.id = m.home_shop_id
            WHERE m.id = ? AND m.tenant_id = ?
            LIMIT 1
            """,
            memberId,
            principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("shops", profiles(principal, memberId, scope));
        result.put("accounts", jdbcTemplate.queryForList(
            """
            SELECT account_type AS accountType, currency_code AS currencyCode,
                   balance, version, status
            FROM member_account
            WHERE tenant_id = ? AND member_id = ?
            ORDER BY account_type
            """,
            principal.tenantId(),
            memberId
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> create(TenantPrincipal principal, MemberCreateRequest request) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "member:manage"
        );
        String phone = request.phone().trim();

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id FROM member WHERE tenant_id = ? AND phone = ? ORDER BY id LIMIT 1",
            principal.tenantId(),
            phone
        );
        if (!existing.isEmpty()) {
            long memberId = ((Number) existing.getFirst().get("id")).longValue();
            jdbcTemplate.update(
                """
                INSERT INTO member_shop_profile
                    (tenant_id, member_id, shop_id, first_visit_at, source, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = 'ACTIVE', source = COALESCE(VALUES(source), source)
                """,
                principal.tenantId(),
                memberId,
                shopId,
                trimToNull(request.source())
            );
            jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE', version = version + 1 WHERE id = ? AND tenant_id = ?",
                memberId,
                principal.tenantId()
            );
            ensureAccounts(principal.tenantId(), memberId);
            jdbcTemplate.update(
                "UPDATE member_account SET status = 'ACTIVE' WHERE tenant_id = ? AND member_id = ?",
                principal.tenantId(),
                memberId
            );
            audit(principal, shopId, "MEMBER_LINK_SHOP", memberId, null, request.name(), phone);
            return Map.of("id", memberId, "linkedExisting", true);
        }

        String temporary = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO member
                    (tenant_id, home_shop_id, shop_id, member_no, global_member_no,
                     name, phone, gender, birthday, source, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, shopId);
            statement.setString(4, "TMP" + temporary);
            statement.setString(5, "GTMP" + temporary);
            statement.setString(6, request.name().trim());
            statement.setString(7, phone);
            statement.setString(8, trimToNull(request.gender()));
            if (request.birthday() == null) {
                statement.setObject(9, null);
            } else {
                statement.setObject(9, request.birthday());
            }
            statement.setString(10, trimToNull(request.source()));
            statement.setString(11, trimToNull(request.notes()));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "会员编号生成失败");
        long memberId = key.longValue();
        String memberNo = "M%06d".formatted(memberId);
        String globalMemberNo = "GM%03d%012d".formatted(principal.tenantId(), memberId);
        jdbcTemplate.update(
            "UPDATE member SET member_no = ?, global_member_no = ? WHERE id = ? AND tenant_id = ?",
            memberNo,
            globalMemberNo,
            memberId,
            principal.tenantId()
        );
        jdbcTemplate.update(
            """
            INSERT INTO member_shop_profile
                (tenant_id, member_id, shop_id, first_visit_at, source, status)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), ?, 'ACTIVE')
            """,
            principal.tenantId(),
            memberId,
            shopId,
            trimToNull(request.source())
        );
        ensureAccounts(principal.tenantId(), memberId);
        audit(principal, shopId, "MEMBER_CREATE", memberId, null, request.name(), phone);
        return Map.of("id", memberId, "memberNo", memberNo, "linkedExisting", false);
    }

    @Transactional
    public Map<String, Object> update(TenantPrincipal principal, long memberId, MemberUpdateRequest request) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "member:manage"
        );
        requireMemberInScope(principal, memberId, List.of(shopId), shopId);
        Map<String, Object> before = memberSnapshot(principal.tenantId(), memberId);
        String phone = request.phone().trim();

        Integer duplicates = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM member WHERE tenant_id = ? AND phone = ? AND id <> ?",
            Integer.class,
            principal.tenantId(),
            phone,
            memberId
        );
        if (duplicates != null && duplicates > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该手机号已经属于另一位会员");
        }

        int changed = jdbcTemplate.update(
            """
            UPDATE member
            SET name = ?, phone = ?, gender = ?, birthday = ?, source = ?, notes = ?,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND version = ?
            """,
            request.name().trim(),
            phone,
            trimToNull(request.gender()),
            request.birthday(),
            trimToNull(request.source()),
            trimToNull(request.notes()),
            memberId,
            principal.tenantId(),
            request.version()
        );
        if (changed == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "会员资料已被其他人修改，请刷新后重试");
        }
        jdbcTemplate.update(
            "UPDATE member_shop_profile SET source = COALESCE(?, source) WHERE member_id = ? AND shop_id = ?",
            trimToNull(request.source()),
            memberId,
            shopId
        );
        audit(
            principal,
            shopId,
            "MEMBER_UPDATE",
            memberId,
            String.valueOf(before.get("name")),
            request.name().trim(),
            phone
        );
        return Map.of("id", memberId, "version", request.version() + 1);
    }

    @Transactional
    public void changeStatus(TenantPrincipal principal, long memberId, MemberStatusRequest request) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "member:manage"
        );
        requireMemberInScope(principal, memberId, List.of(shopId), shopId);
        String status = normalizeStatus(request.status());
        if (status == null) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的会员状态");

        jdbcTemplate.update(
            "UPDATE member_shop_profile SET status = ? WHERE tenant_id = ? AND member_id = ? AND shop_id = ?",
            status,
            principal.tenantId(),
            memberId,
            shopId
        );
        if ("ACTIVE".equals(status)) {
            jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE', version = version + 1 WHERE id = ? AND tenant_id = ?",
                memberId,
                principal.tenantId()
            );
            jdbcTemplate.update(
                "UPDATE member_account SET status = 'ACTIVE' WHERE tenant_id = ? AND member_id = ?",
                principal.tenantId(),
                memberId
            );
        } else {
            Integer activeProfiles = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM member_shop_profile
                WHERE tenant_id = ? AND member_id = ? AND status = 'ACTIVE'
                """,
                Integer.class,
                principal.tenantId(),
                memberId
            );
            if (activeProfiles == null || activeProfiles == 0) {
                jdbcTemplate.update(
                    "UPDATE member SET status = 'INACTIVE', version = version + 1 WHERE id = ? AND tenant_id = ?",
                    memberId,
                    principal.tenantId()
                );
                jdbcTemplate.update(
                    "UPDATE member_account SET status = 'FROZEN' WHERE tenant_id = ? AND member_id = ?",
                    principal.tenantId(),
                    memberId
                );
            }
        }
        audit(principal, shopId, "MEMBER_STATUS_" + status, memberId, null, null, null);
    }

    private String buildWhere(
        TenantPrincipal principal,
        List<Long> accessibleShopIds,
        Long shopId,
        String keyword,
        String status,
        List<Object> args
    ) {
        StringBuilder where = new StringBuilder("WHERE m.tenant_id = ?");
        args.add(principal.tenantId());
        where.append("""
             AND EXISTS (
                 SELECT 1 FROM member_shop_profile msp_scope
                 WHERE msp_scope.tenant_id = m.tenant_id
                   AND msp_scope.member_id = m.id
                   AND msp_scope.shop_id IN (%s)
            """.formatted(placeholders(accessibleShopIds.size())));
        args.addAll(accessibleShopIds);
        if (status != null) {
            where.append(" AND msp_scope.status = ?");
            args.add(status);
        }
        where.append(")");
        if (keyword != null) {
            where.append(" AND (m.name LIKE ? OR m.phone LIKE ? OR m.member_no LIKE ? OR m.global_member_no LIKE ?)");
            String pattern = "%" + keyword + "%";
            Collections.addAll(args, pattern, pattern, pattern, pattern);
        }
        return where.toString();
    }

    private List<Object> buildWhereArgs(
        TenantPrincipal principal,
        List<Long> accessibleShopIds,
        Long shopId,
        String keyword,
        String status
    ) {
        List<Object> args = new ArrayList<>();
        buildWhere(principal, accessibleShopIds, shopId, keyword, status, args);
        return args;
    }

    private long count(String where, List<Object> args) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member m " + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private List<Map<String, Object>> profiles(TenantPrincipal principal, long memberId, List<Long> scope) {
        if (principal.tenantWide() && scope.isEmpty()) {
            return jdbcTemplate.queryForList(
                """
                SELECT msp.shop_id AS shopId, s.name AS shopName, msp.first_visit_at AS firstVisitAt,
                       msp.last_visit_at AS lastVisitAt, msp.visit_count AS visitCount,
                       msp.source, msp.shop_notes AS shopNotes, msp.status
                FROM member_shop_profile msp
                JOIN shop s ON s.id = msp.shop_id
                WHERE msp.tenant_id = ? AND msp.member_id = ?
                ORDER BY s.name
                """,
                principal.tenantId(),
                memberId
            );
        }
        return jdbcTemplate.queryForList(
            """
            SELECT msp.shop_id AS shopId, s.name AS shopName, msp.first_visit_at AS firstVisitAt,
                   msp.last_visit_at AS lastVisitAt, msp.visit_count AS visitCount,
                   msp.source, msp.shop_notes AS shopNotes, msp.status
            FROM member_shop_profile msp
            JOIN shop s ON s.id = msp.shop_id
            WHERE msp.tenant_id = ? AND msp.member_id = ?
              AND msp.shop_id IN (%s)
            ORDER BY s.name
            """.formatted(placeholders(scope.size())),
            concat(principal.tenantId(), memberId, scope).toArray()
        );
    }

    public Map<String, Object> requireActiveMemberInShop(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT m.id, m.member_no AS memberNo, m.name
            FROM member m
            JOIN member_shop_profile msp
              ON msp.member_id = m.id
             AND msp.tenant_id = m.tenant_id
            WHERE m.id = ?
              AND m.tenant_id = ?
              AND m.status = 'ACTIVE'
              AND msp.shop_id = ?
              AND msp.status = 'ACTIVE'
            LIMIT 1
            """,
            memberId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在或未在该门店启用");
        }
        return rows.getFirst();
    }

    private void requireMemberInScope(
        TenantPrincipal principal,
        long memberId,
        List<Long> scope,
        Long selectedShopId
    ) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT COUNT(*) FROM member m
            WHERE m.id = ? AND m.tenant_id = ?
            """
        );
        List<Object> args = new ArrayList<>(List.of(memberId, principal.tenantId()));
        if (scope.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "当前账号没有该功能的可管理门店");
        sql.append(
            """
             AND EXISTS (
                 SELECT 1 FROM member_shop_profile msp
                 WHERE msp.member_id = m.id AND msp.tenant_id = m.tenant_id
                   AND msp.shop_id IN (%s)
             )
            """.formatted(placeholders(scope.size()))
        );
        args.addAll(scope);
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在或不在当前管理范围");
    }

    private Map<String, Object> memberSnapshot(long tenantId, long memberId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT name, phone FROM member WHERE tenant_id = ? AND id = ? LIMIT 1",
            tenantId,
            memberId
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "会员不存在");
        return rows.getFirst();
    }

    /**
     * Owns the member-side of online identity attachment. Identity code must call
     * this method instead of writing member facts itself, so an existing offline
     * member is matched by tenant and phone before a new member is created.
     */
    @Transactional
    public OnlineIdentityMember attachOnlineIdentity(
        long tenantId,
        long shopId,
        String phone,
        String requestedName
    ) {
        List<Map<String, Object>> matches = jdbcTemplate.queryForList(
            """
            SELECT id, name, status
            FROM member
            WHERE tenant_id = ? AND phone = ?
            ORDER BY id
            FOR UPDATE
            """,
            tenantId,
            phone
        );
        if (matches.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "该手机号对应多条历史会员，请联系门店处理");
        }
        if (!matches.isEmpty()) {
            Map<String, Object> match = matches.getFirst();
            if (!"ACTIVE".equals(String.valueOf(match.get("status")))) {
                throw new ApiException(HttpStatus.CONFLICT, "该手机号对应的会员资料已停用");
            }
            long memberId = ((Number) match.get("id")).longValue();
            jdbcTemplate.update(
                """
                INSERT INTO member_shop_profile
                    (tenant_id, member_id, shop_id, first_visit_at, source, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), 'ONLINE', 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = 'ACTIVE'
                """,
                tenantId,
                memberId,
                shopId
            );
            ensureAccounts(tenantId, memberId);
            return new OnlineIdentityMember(
                memberId,
                String.valueOf(match.get("name")),
                true
            );
        }

        String name = trimToNull(requestedName);
        if (name == null) name = "手机用户" + phone.substring(phone.length() - 4);
        String temporary = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String memberName = name;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO member
                    (tenant_id, home_shop_id, shop_id, member_no, global_member_no,
                     name, phone, source, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ONLINE', 'ACTIVE')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tenantId);
            statement.setLong(2, shopId);
            statement.setLong(3, shopId);
            statement.setString(4, "TMP" + temporary);
            statement.setString(5, "GTMP" + temporary);
            statement.setString(6, memberName);
            statement.setString(7, phone);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "会员编号生成失败");
        }
        long memberId = key.longValue();
        jdbcTemplate.update(
            "UPDATE member SET member_no = ?, global_member_no = ? WHERE id = ? AND tenant_id = ?",
            "M%06d".formatted(memberId),
            "GM%03d%012d".formatted(tenantId, memberId),
            memberId,
            tenantId
        );
        jdbcTemplate.update(
            """
            INSERT INTO member_shop_profile
                (tenant_id, member_id, shop_id, first_visit_at, source, status)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), 'ONLINE', 'ACTIVE')
            """,
            tenantId,
            memberId,
            shopId
        );
        ensureAccounts(tenantId, memberId);
        return new OnlineIdentityMember(memberId, memberName, false);
    }

    private void ensureAccounts(long tenantId, long memberId) {
        for (String type : List.of("BALANCE", "GIFT_BALANCE", "POINTS")) {
            jdbcTemplate.update(
                """
                INSERT IGNORE INTO member_account
                    (tenant_id, member_id, account_type, currency_code, balance, status)
                VALUES (?, ?, ?, 'CNY', 0, 'ACTIVE')
                """,
                tenantId,
                memberId,
                type
            );
        }
    }

    public record OnlineIdentityMember(long memberId, String name, boolean matchedExisting) {
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        long memberId,
        String beforeName,
        String afterName,
        String phone
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log
                (tenant_id, shop_id, account_id, action, entity_type, entity_id, before_data, after_data)
            VALUES (?, ?, ?, ?, 'MEMBER', ?,
                    IF(? IS NULL, NULL, JSON_OBJECT('name', ?)),
                    IF(? IS NULL AND ? IS NULL, NULL, JSON_OBJECT('name', ?, 'phone', ?)))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            memberId,
            beforeName,
            beforeName,
            afterName,
            phone,
            afterName,
            phone
        );
    }

    private Map<String, Object> pageResult(
        List<Map<String, Object>> records,
        long total,
        long active,
        long inactive,
        int page,
        int pageSize
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("active", active);
        result.put("inactive", inactive);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private List<Object> concat(long tenantId, long memberId, List<Long> scope) {
        List<Object> values = new ArrayList<>();
        values.add(tenantId);
        values.add(memberId);
        values.addAll(scope);
        return values;
    }

    private int safePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private String placeholders(int size) {
        if (size <= 0) throw new ApiException(HttpStatus.FORBIDDEN, "当前账号没有可管理门店");
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String normalizeStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        normalized = normalized.toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的会员状态");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
