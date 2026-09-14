package com.face.platform.legacyimport;

import com.face.platform.api.ApiException;
import com.face.platform.member.MemberCreateRequest;
import com.face.platform.member.MemberService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LegacyImportService {

    private static final Set<String> CARD_TYPES = Set.of("COMBO_CARD", "STORED_VALUE_CARD", "DISCOUNT_CARD");
    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final MemberService memberService;
    private final ObjectMapper objectMapper;

    public LegacyImportService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        MemberService memberService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.memberService = memberService;
        this.objectMapper = objectMapper;
    }

    public void requireView(TenantPrincipal principal) {
        requireManagementRole(principal);
        accessService.requireManagementPermission(principal, "import:view");
    }

    @Transactional
    public Map<String, Object> preflight(
        TenantPrincipal principal,
        long shopId,
        String fileName,
        byte[] content
    ) {
        requireManagementRole(principal);
        accessService.requireManagementPermission(principal, "import:preflight");
        accessService.requireShopAccess(principal, shopId);
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只支持系统模板生成的 .xlsx 文件");
        }
        String fileHash = sha256(content);
        List<Map<String, Object>> duplicate = jdbcTemplate.queryForList(
            "SELECT id FROM legacy_import_batch WHERE tenant_id = ? AND shop_id = ? AND file_sha256 = ?",
            principal.tenantId(), shopId, fileHash
        );
        if (!duplicate.isEmpty()) return batch(principal, number(duplicate.getFirst().get("id")));

        List<LegacyImportWorkbook.ParsedRow> parsedRows = LegacyImportWorkbook.parse(content);
        long batchId = insertBatch(principal, shopId, safeFileName(fileName), fileHash);
        String selectedShopCode = jdbcTemplate.queryForObject(
            "SELECT shop_code FROM shop WHERE id = ? AND tenant_id = ?", String.class, shopId, principal.tenantId()
        );
        int ready = 0;
        int conflicts = 0;
        int errors = 0;
        for (LegacyImportWorkbook.ParsedRow parsed : parsedRows) {
            RowDecision decision = classify(principal, batchId, selectedShopCode, parsed);
            insertRow(principal, batchId, parsed, decision);
            if (decision.status().startsWith("READY") || "SKIPPED".equals(decision.status())) ready++;
            else if ("CONFLICT".equals(decision.status())) conflicts++;
            else errors++;
        }
        String status = errors + conflicts > 0 ? "BLOCKED" : "PREFLIGHTED";
        jdbcTemplate.update(
            """
            UPDATE legacy_import_batch
            SET status = ?, total_rows = ?, ready_rows = ?, conflict_rows = ?, error_rows = ?
            WHERE id = ? AND tenant_id = ?
            """,
            status, parsedRows.size(), ready, conflicts, errors, batchId, principal.tenantId()
        );
        audit(principal, shopId, "LEGACY_IMPORT_PREFLIGHT", batchId, Map.of(
            "totalRows", parsedRows.size(), "readyRows", ready, "conflictRows", conflicts, "errorRows", errors
        ));
        return batch(principal, batchId);
    }

    public Map<String, Object> batches(TenantPrincipal principal, long shopId, int page, int pageSize) {
        requireView(principal);
        accessService.requireShopAccess(principal, shopId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT id, batch_no AS batchNo, file_name AS fileName, file_sha256 AS fileSha256,
                   status, total_rows AS totalRows, ready_rows AS readyRows,
                   conflict_rows AS conflictRows, error_rows AS errorRows,
                   imported_members AS importedMembers, imported_cards AS importedCards,
                   started_at AS startedAt, completed_at AS completedAt, created_at AS createdAt
            FROM legacy_import_batch
            WHERE tenant_id = ? AND shop_id = ?
            ORDER BY id DESC LIMIT ? OFFSET ?
            """,
            principal.tenantId(), shopId, safeSize, (safePage - 1) * safeSize
        );
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM legacy_import_batch WHERE tenant_id = ? AND shop_id = ?",
            Long.class, principal.tenantId(), shopId
        );
        return Map.of("records", records, "total", total == null ? 0 : total, "page", safePage, "pageSize", safeSize);
    }

    public Map<String, Object> batch(TenantPrincipal principal, long batchId) {
        requireView(principal);
        Map<String, Object> header = requireBatch(principal, batchId);
        accessService.requireShopAccess(principal, number(header.get("shopId")));
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("rows", jdbcTemplate.queryForList(
            """
            SELECT id, sheet_name AS sheetName, source_row_number AS rowNumber, record_type AS recordType,
                   source_system AS sourceSystem, source_record_no AS sourceRecordNo,
                   masked_subject AS maskedSubject, status, match_type AS matchType,
                   matched_member_id AS matchedMemberId, result_entity_type AS resultEntityType,
                   result_entity_id AS resultEntityId, issue_code AS issueCode, issue_message AS issueMessage
            FROM legacy_import_row
            WHERE tenant_id = ? AND batch_id = ?
            ORDER BY FIELD(status, 'CONFLICT', 'ERROR', 'READY_NEW', 'READY_MATCHED', 'SKIPPED', 'IMPORTED'), id
            LIMIT 500
            """,
            principal.tenantId(), batchId
        ));
        return result;
    }

    public byte[] issueReport(TenantPrincipal principal, long batchId) {
        Map<String, Object> batch = requireBatch(principal, batchId);
        requireView(principal);
        accessService.requireShopAccess(principal, number(batch.get("shopId")));
        StringBuilder csv = new StringBuilder("\uFEFF工作表,行号,记录类型,来源记录号,状态,问题代码,问题说明\r\n");
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            """
            SELECT sheet_name, source_row_number AS sourceRowNumber, record_type, source_record_no, status, issue_code, issue_message
            FROM legacy_import_row WHERE tenant_id = ? AND batch_id = ? AND status IN ('CONFLICT', 'ERROR')
            ORDER BY id
            """, principal.tenantId(), batchId
        )) {
            csv.append(csv(row.get("sheet_name"))).append(',')
                .append(row.get("sourceRowNumber")).append(',')
                .append(csv(row.get("record_type"))).append(',')
                .append(csv(row.get("source_record_no"))).append(',')
                .append(csv(row.get("status"))).append(',')
                .append(csv(row.get("issue_code"))).append(',')
                .append(csv(row.get("issue_message"))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public Map<String, Object> execute(
        TenantPrincipal principal,
        long batchId,
        long shopId,
        String idempotencyKey
    ) {
        if (!principal.roles().contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("正式数据导入仅超级管理员可操作");
        }
        accessService.requireManagementPermission(principal, "import:execute");
        accessService.requireShopAccess(principal, shopId);
        String key = required(idempotencyKey, "幂等键");
        String requestHash = sha256((batchId + ":" + shopId + ":" + key).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> header = requireBatch(principal, batchId);
        if (number(header.get("shopId")) != shopId) throw new ApiException(HttpStatus.CONFLICT, "批次门店与请求门店不一致");
        if ("COMPLETED".equals(header.get("status"))) {
            if (!requestHash.equals(header.get("requestHash"))) throw new ApiException(HttpStatus.CONFLICT, "批次已由不同幂等请求执行");
            return batch(principal, batchId);
        }
        if (header.get("requestHash") != null && !requestHash.equals(header.get("requestHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "批次幂等键已用于不同请求");
        }
        jdbcTemplate.update(
            "UPDATE legacy_import_batch SET status = 'RUNNING', request_hash = ?, started_at = CURRENT_TIMESTAMP(3), executed_by = ? WHERE id = ? AND tenant_id = ?",
            requestHash, principal.accountId(), batchId, principal.tenantId()
        );

        int importedMembers = 0;
        for (Map<String, Object> row : executableRows(principal, batchId, "MEMBER")) {
            if ("SKIPPED".equals(row.get("status"))) continue;
            Map<String, String> payload = payload(row.get("payloadJson"));
            Long memberId = nullableNumber(row.get("matchedMemberId"));
            if (memberId == null) {
                Map<String, Object> created = memberService.create(principal, new MemberCreateRequest(
                    shopId, payload.get("name"), payload.get("phone"), emptyToNull(payload.get("gender")),
                    localDateOrNull(payload.get("birthday")), "LEGACY_IMPORT", "SC7 历史资料导入"
                ));
                memberId = number(created.get("id"));
                if (!Boolean.TRUE.equals(created.get("linkedExisting"))) importedMembers++;
            } else {
                jdbcTemplate.update(
                    """
                    UPDATE member
                    SET gender = CASE WHEN gender IS NULL OR gender = '' THEN ? ELSE gender END,
                        birthday = COALESCE(birthday, ?),
                        source = CASE WHEN source IS NULL OR source = '' THEN 'LEGACY_IMPORT' ELSE source END
                    WHERE id = ? AND tenant_id = ?
                    """,
                    emptyToNull(payload.get("gender")), localDateOrNull(payload.get("birthday")),
                    memberId, principal.tenantId()
                );
                jdbcTemplate.update(
                    """
                    INSERT INTO member_shop_profile (tenant_id, member_id, shop_id, first_visit_at, source, status)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), 'LEGACY_IMPORT', 'ACTIVE')
                    ON DUPLICATE KEY UPDATE status = 'ACTIVE'
                    """, principal.tenantId(), memberId, shopId
                );
            }
            jdbcTemplate.update(
                """
                INSERT INTO legacy_member_source_map (tenant_id, source_system, original_member_no, member_id, batch_id)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE member_id = IF(member_id = VALUES(member_id), member_id, member_id)
                """,
                principal.tenantId(), payload.get("sourceSystem"), payload.get("originalMemberNo"), memberId, batchId
            );
            jdbcTemplate.update(
                "UPDATE legacy_import_row SET status = 'IMPORTED', matched_member_id = ?, result_entity_type = 'MEMBER', result_entity_id = ? WHERE id = ?",
                memberId, memberId, row.get("id")
            );
        }

        int importedCards = 0;
        for (Map<String, Object> row : executableCardRows(principal, batchId)) {
            if ("SKIPPED".equals(row.get("status"))) continue;
            long instanceId = importCard(principal, batchId, shopId, row, requestHash);
            jdbcTemplate.update(
                "UPDATE legacy_import_row SET status = 'IMPORTED', result_entity_type = 'PACKAGE_INSTANCE', result_entity_id = ? WHERE id = ?",
                instanceId, row.get("id")
            );
            importedCards++;
        }
        jdbcTemplate.update(
            """
            UPDATE legacy_import_batch
            SET status = 'COMPLETED', imported_members = ?, imported_cards = ?, completed_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND tenant_id = ?
            """, importedMembers, importedCards, batchId, principal.tenantId()
        );
        audit(principal, shopId, "LEGACY_IMPORT_EXECUTE", batchId, Map.of(
            "importedMembers", importedMembers, "importedCards", importedCards
        ));
        return batch(principal, batchId);
    }

    private RowDecision classify(
        TenantPrincipal principal,
        long batchId,
        String selectedShopCode,
        LegacyImportWorkbook.ParsedRow row
    ) {
        Map<String, String> values = row.values();
        if ("MEMBER".equals(row.recordType())) return classifyMember(principal, selectedShopCode, values);
        if ("COMBO_DETAIL".equals(row.recordType())) return classifyDetail(principal, batchId, values);
        return classifyCard(principal, batchId, selectedShopCode, row.recordType(), values);
    }

    private RowDecision classifyMember(TenantPrincipal principal, String shopCode, Map<String, String> values) {
        String missing = missing(values, "sourceSystem", "originalMemberNo", "name", "phone", "shopCode");
        if (missing != null) return RowDecision.error("REQUIRED_FIELD", missing + "不能为空");
        if (!shopCode.equals(values.get("shopCode"))) return RowDecision.error("SHOP_SCOPE", "门店编码不属于当前预检门店");
        String normalizedPhone = normalizePhone(values.get("phone"));
        if (normalizedPhone.length() < 6) return RowDecision.error("PHONE_FORMAT", "手机号格式不正确");
        List<Map<String, Object>> mapped = jdbcTemplate.queryForList(
            """
            SELECT m.id, m.name, m.phone FROM legacy_member_source_map lsm
            JOIN member m ON m.id = lsm.member_id AND m.tenant_id = lsm.tenant_id
            WHERE lsm.tenant_id = ? AND lsm.source_system = ? AND lsm.original_member_no = ?
            """, principal.tenantId(), values.get("sourceSystem"), values.get("originalMemberNo")
        );
        if (!mapped.isEmpty()) return compareMember(mapped.getFirst(), values, "SOURCE_KEY");
        List<Map<String, Object>> byPhone = jdbcTemplate.queryForList(
            """
            SELECT id, name, phone FROM member
            WHERE tenant_id = ? AND (
              REGEXP_REPLACE(phone, '[^0-9]', '') = ? OR
              REGEXP_REPLACE(phone, '[^0-9]', '') = CONCAT('86', ?)
            ) ORDER BY id
            """, principal.tenantId(), normalizedPhone, normalizedPhone
        );
        if (byPhone.size() > 1) return RowDecision.conflict("AMBIGUOUS_PHONE", "标准化手机号匹配到多个会员，需人工处理");
        if (byPhone.size() == 1) return compareMember(byPhone.getFirst(), values, "PHONE");
        return new RowDecision("READY_NEW", "NEW", null, null, null);
    }

    private RowDecision compareMember(Map<String, Object> member, Map<String, String> values, String matchType) {
        if (!String.valueOf(member.get("name")).trim().equals(values.get("name").trim())
            || !normalizePhone(String.valueOf(member.get("phone"))).equals(normalizePhone(values.get("phone")))) {
            return RowDecision.conflict("PROFILE_CONFLICT", "系统已有姓名或手机号与导入资料不同，未覆盖现有资料");
        }
        return new RowDecision("READY_MATCHED", matchType, number(member.get("id")), null, null);
    }

    private RowDecision classifyCard(
        TenantPrincipal principal,
        long batchId,
        String shopCode,
        String recordType,
        Map<String, String> values
    ) {
        String missing = missing(values, "sourceSystem", "originalCardNo", "originalMemberNo", "shopCode", "packageCode", "purchaseDate", "expiresAt");
        if (missing != null) return RowDecision.error("REQUIRED_FIELD", missing + "不能为空");
        if (!shopCode.equals(values.get("shopCode"))) return RowDecision.error("SHOP_SCOPE", "门店编码不属于当前预检门店");
        try {
            LocalDate purchase = LocalDate.parse(values.get("purchaseDate"));
            LocalDate expiry = LocalDate.parse(values.get("expiresAt"));
            if (expiry.isBefore(purchase)) return RowDecision.error("DATE_RANGE", "到期日不能早于原购买日");
            validateCardAmounts(recordType, values);
        } catch (RuntimeException exception) {
            return RowDecision.error("VALUE_FORMAT", "日期、金额或次数格式不正确");
        }
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM package_instance WHERE tenant_id = ? AND legacy_source_system = ? AND legacy_original_card_no = ?",
            Integer.class, principal.tenantId(), values.get("sourceSystem"), values.get("originalCardNo")
        );
        if (existing != null && existing > 0) return new RowDecision("SKIPPED", "LEGACY_CARD", null, null, null);
        String expectedType = switch (recordType) {
            case "COMBO_CARD" -> "COMBO_TIMES";
            case "STORED_VALUE_CARD" -> "STORED_VALUE";
            case "DISCOUNT_CARD" -> "DISCOUNT";
            default -> throw new IllegalStateException("未知卡类型");
        };
        Integer products = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM package_product WHERE tenant_id = ? AND package_code = ? AND card_type = ? AND status = 'ACTIVE'",
            Integer.class, principal.tenantId(), values.get("packageCode"), expectedType
        );
        if (products == null || products != 1) return RowDecision.error("PACKAGE_PRODUCT", "卡产品编码不存在、未启用或类型不一致");
        Integer memberSources = jdbcTemplate.queryForObject(
            """
            SELECT (
              SELECT COUNT(*) FROM legacy_member_source_map
              WHERE tenant_id = ? AND source_system = ? AND original_member_no = ?
            ) + (
              SELECT COUNT(*) FROM legacy_import_row
              WHERE tenant_id = ? AND batch_id = ? AND record_type = 'MEMBER'
                AND source_system = ? AND source_record_no = ? AND status IN ('READY_NEW', 'READY_MATCHED', 'IMPORTED')
            )
            """, Integer.class,
            principal.tenantId(), values.get("sourceSystem"), values.get("originalMemberNo"),
            principal.tenantId(), batchId, values.get("sourceSystem"), values.get("originalMemberNo")
        );
        if (memberSources == null || memberSources == 0) return RowDecision.conflict("MEMBER_UNMATCHED", "未找到可唯一匹配的来源会员");
        if (memberSources > 1) return RowDecision.conflict("MEMBER_AMBIGUOUS", "来源会员存在多条候选记录");
        return new RowDecision("READY_MATCHED", "SOURCE_MEMBER", null, null, null);
    }

    private RowDecision classifyDetail(TenantPrincipal principal, long batchId, Map<String, String> values) {
        String missing = missing(values, "sourceSystem", "originalCardNo", "serviceCode", "totalQuantity", "remainingQuantity");
        if (missing != null) return RowDecision.error("REQUIRED_FIELD", missing + "不能为空");
        try {
            BigDecimal total = positive(values.get("totalQuantity"));
            BigDecimal remaining = nonNegative(values.get("remainingQuantity"));
            if (remaining.compareTo(total) > 0) return RowDecision.error("BALANCE_RANGE", "护理剩余次数不能大于总次数");
        } catch (RuntimeException exception) {
            return RowDecision.error("VALUE_FORMAT", "护理次数格式不正确");
        }
        Integer serviceCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM service_item WHERE tenant_id = ? AND service_code = ? AND status = 'ACTIVE'",
            Integer.class, principal.tenantId(), values.get("serviceCode")
        );
        if (serviceCount == null || serviceCount != 1) return RowDecision.error("SERVICE_ITEM", "护理项目编码不存在或未启用");
        Integer cardCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM legacy_import_row
            WHERE tenant_id = ? AND batch_id = ? AND record_type = 'COMBO_CARD'
              AND source_system = ? AND source_record_no = ? AND status IN ('READY_MATCHED', 'SKIPPED')
            """, Integer.class, principal.tenantId(), batchId, values.get("sourceSystem"), values.get("originalCardNo")
        );
        if (cardCount == null || cardCount != 1) return RowDecision.error("CARD_PARENT", "组合卡护理明细找不到唯一的组合卡主记录");
        return new RowDecision("READY_MATCHED", "CARD_DETAIL", null, null, null);
    }

    private void validateCardAmounts(String type, Map<String, String> values) {
        if ("COMBO_CARD".equals(type)) {
            BigDecimal total = positive(values.get("totalQuantity"));
            if (nonNegative(values.get("remainingQuantity")).compareTo(total) > 0) throw new IllegalArgumentException();
        } else if ("STORED_VALUE_CARD".equals(type)) {
            BigDecimal amount = nonNegative(values.get("principalRemaining")).add(nonNegative(values.get("giftRemaining")));
            if (amount.signum() <= 0) throw new IllegalArgumentException();
        } else if (nonNegative(values.get("remainingUses")).signum() <= 0) {
            throw new IllegalArgumentException();
        }
    }

    private long importCard(
        TenantPrincipal principal,
        long batchId,
        long shopId,
        Map<String, Object> row,
        String requestHash
    ) {
        Map<String, String> values = payload(row.get("payloadJson"));
        Long existing = nullableQueryLong(
            "SELECT id FROM package_instance WHERE tenant_id = ? AND legacy_source_system = ? AND legacy_original_card_no = ?",
            principal.tenantId(), values.get("sourceSystem"), values.get("originalCardNo")
        );
        if (existing != null) return existing;
        Long memberId = nullableQueryLong(
            "SELECT member_id FROM legacy_member_source_map WHERE tenant_id = ? AND source_system = ? AND original_member_no = ?",
            principal.tenantId(), values.get("sourceSystem"), values.get("originalMemberNo")
        );
        if (memberId == null) throw new ApiException(HttpStatus.CONFLICT, "来源会员尚未完成导入");
        Map<String, Object> product = jdbcTemplate.queryForMap(
            "SELECT id, card_type AS cardType FROM package_product WHERE tenant_id = ? AND package_code = ? AND status = 'ACTIVE'",
            principal.tenantId(), values.get("packageCode")
        );
        String recordType = String.valueOf(row.get("recordType"));
        BigDecimal total;
        BigDecimal remaining;
        BigDecimal principalRemaining = BigDecimal.ZERO;
        BigDecimal giftRemaining = BigDecimal.ZERO;
        if ("COMBO_CARD".equals(recordType)) {
            total = positive(values.get("totalQuantity"));
            remaining = nonNegative(values.get("remainingQuantity"));
        } else if ("STORED_VALUE_CARD".equals(recordType)) {
            principalRemaining = nonNegative(values.get("principalRemaining"));
            giftRemaining = nonNegative(values.get("giftRemaining"));
            total = principalRemaining.add(giftRemaining);
            remaining = total;
        } else {
            total = nonNegative(values.get("remainingUses"));
            remaining = total;
        }
        LocalDate purchaseDate = LocalDate.parse(values.get("purchaseDate"));
        LocalDate expiry = LocalDate.parse(values.get("expiresAt"));
        String issueKey = bounded("LEGACY-CARD-" + values.get("sourceSystem") + "-" + values.get("originalCardNo"), 80);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        BigDecimal finalTotal = total;
        BigDecimal finalRemaining = remaining;
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO package_instance (
                    tenant_id, shop_id, member_id, package_product_id, instance_no, card_type,
                    source_order_id, source_type, source_reference, legacy_source_system,
                    legacy_original_card_no, legacy_original_purchase_date, purchase_price,
                    valid_from, valid_until, total_quantity, remaining_quantity, frozen_quantity,
                    usage_count, status, version, issue_idempotency_key, issue_request_hash,
                    created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, 'LEGACY_IMPORT', ?, ?, ?, ?, 0, ?, ?, ?, ?, 0, 0, ?, 0, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setLong(3, memberId);
            statement.setLong(4, number(product.get("id")));
            statement.setString(5, bounded("PENDING-" + issueKey, 48));
            statement.setString(6, String.valueOf(product.get("cardType")));
            statement.setString(7, values.get("originalCardNo"));
            statement.setString(8, values.get("sourceSystem"));
            statement.setString(9, values.get("originalCardNo"));
            statement.setObject(10, purchaseDate);
            statement.setObject(11, purchaseDate);
            statement.setObject(12, expiry);
            statement.setBigDecimal(13, finalTotal);
            statement.setBigDecimal(14, finalRemaining);
            statement.setString(15, finalRemaining.signum() == 0 ? "EXHAUSTED" : "ACTIVE");
            statement.setString(16, issueKey);
            statement.setString(17, requestHash);
            statement.setLong(18, principal.accountId());
            statement.setLong(19, principal.accountId());
            return statement;
        }, keyHolder);
        long instanceId = keyHolder.getKey().longValue();
        jdbcTemplate.update(
            "UPDATE package_instance SET instance_no = ? WHERE id = ?",
            "LI%03d%012d".formatted(principal.tenantId(), instanceId), instanceId
        );
        if ("COMBO_CARD".equals(recordType)) importComboItems(principal, batchId, instanceId, values, total, remaining);
        jdbcTemplate.update(
            """
            INSERT INTO package_ledger (
                tenant_id, shop_id, package_instance_id, entry_type, quantity_delta,
                balance_after, business_key, idempotency_key, request_hash, reason, created_by
            ) VALUES (?, ?, ?, 'LEGACY_IMPORT', ?, ?, ?, ?, ?, 'SC7 历史卡独立导入', ?)
            """,
            principal.tenantId(), shopId, instanceId, total, remaining,
            "LEGACY_IMPORT:" + instanceId, bounded("LEGACY-PACKAGE-" + instanceId, 80), requestHash, principal.accountId()
        );
        if ("STORED_VALUE_CARD".equals(recordType)) {
            importStoredValue(principal, shopId, instanceId, purchaseDate, expiry, principalRemaining, giftRemaining, requestHash);
        }
        return instanceId;
    }

    private void importComboItems(
        TenantPrincipal principal,
        long batchId,
        long instanceId,
        Map<String, String> card,
        BigDecimal cardTotal,
        BigDecimal cardRemaining
    ) {
        List<Map<String, Object>> details = jdbcTemplate.queryForList(
            """
            SELECT payload_json AS payloadJson FROM legacy_import_row
            WHERE tenant_id = ? AND batch_id = ? AND record_type = 'COMBO_DETAIL'
              AND source_system = ? AND source_record_no = ? AND status IN ('READY_MATCHED', 'IMPORTED')
            ORDER BY source_row_number
            """, principal.tenantId(), batchId, card.get("sourceSystem"), card.get("originalCardNo")
        );
        if (!details.isEmpty()) {
            for (Map<String, Object> detailRow : details) {
                Map<String, String> detail = payload(detailRow.get("payloadJson"));
                Map<String, Object> service = jdbcTemplate.queryForMap(
                    "SELECT id, name FROM service_item WHERE tenant_id = ? AND service_code = ? AND status = 'ACTIVE'",
                    principal.tenantId(), detail.get("serviceCode")
                );
                insertComboItem(principal, instanceId, number(service.get("id")), String.valueOf(service.get("name")),
                    positive(detail.get("totalQuantity")), nonNegative(detail.get("remainingQuantity")));
            }
            return;
        }
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            """
            SELECT ppi.service_id AS serviceId, ppi.service_name_snapshot AS serviceName,
                   ppi.quantity_total AS quantityTotal
            FROM package_instance pi JOIN package_product_item ppi ON ppi.package_product_id = pi.package_product_id
            WHERE pi.id = ? ORDER BY ppi.sort_order, ppi.id
            """, instanceId
        );
        BigDecimal productTotal = products.stream().map(item -> decimal(item.get("quantityTotal"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (Map<String, Object> item : products) {
            BigDecimal share = decimal(item.get("quantityTotal")).divide(productTotal, MathContext.DECIMAL64);
            insertComboItem(principal, instanceId, number(item.get("serviceId")), String.valueOf(item.get("serviceName")),
                cardTotal.multiply(share), cardRemaining.multiply(share));
        }
    }

    private void insertComboItem(
        TenantPrincipal principal, long instanceId, long serviceId, String name, BigDecimal total, BigDecimal remaining
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO package_instance_item (
                tenant_id, package_instance_id, service_id, service_name_snapshot,
                total_quantity, remaining_quantity, frozen_quantity
            ) VALUES (?, ?, ?, ?, ?, ?, 0)
            """, principal.tenantId(), instanceId, serviceId, name, total, remaining
        );
    }

    private void importStoredValue(
        TenantPrincipal principal, long shopId, long instanceId, LocalDate from, LocalDate until,
        BigDecimal principalAmount, BigDecimal giftAmount, String requestHash
    ) {
        String batchNo = "LVB%03d%012d".formatted(principal.tenantId(), instanceId);
        jdbcTemplate.update(
            """
            INSERT INTO stored_value_batch (
                tenant_id, shop_id, package_instance_id, batch_no,
                principal_total, gift_total, principal_remaining, gift_remaining,
                valid_from, valid_until, status, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
            """, principal.tenantId(), shopId, instanceId, batchNo,
            principalAmount, giftAmount, principalAmount, giftAmount, from, until, principal.accountId()
        );
        Long batchId = nullableQueryLong("SELECT id FROM stored_value_batch WHERE package_instance_id = ?", instanceId);
        jdbcTemplate.update(
            """
            INSERT INTO stored_value_ledger (
                tenant_id, shop_id, batch_id, entry_type, principal_delta, gift_delta,
                principal_after, gift_after, business_key, idempotency_key, request_hash, reason, created_by
            ) VALUES (?, ?, ?, 'LEGACY_IMPORT', ?, ?, ?, ?, ?, ?, ?, 'SC7 历史储值卡导入', ?)
            """, principal.tenantId(), shopId, batchId, principalAmount, giftAmount, principalAmount, giftAmount,
            "LEGACY_IMPORT:" + batchId, bounded("LEGACY-STORED-" + instanceId, 80), requestHash, principal.accountId()
        );
    }

    private List<Map<String, Object>> executableRows(TenantPrincipal principal, long batchId, String recordType) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, record_type AS recordType, payload_json AS payloadJson, status,
                   matched_member_id AS matchedMemberId
            FROM legacy_import_row
            WHERE tenant_id = ? AND batch_id = ? AND record_type = ?
              AND status IN ('READY_NEW', 'READY_MATCHED', 'SKIPPED') ORDER BY id
            """, principal.tenantId(), batchId, recordType
        );
    }

    private List<Map<String, Object>> executableCardRows(TenantPrincipal principal, long batchId) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, record_type AS recordType, payload_json AS payloadJson, status
            FROM legacy_import_row
            WHERE tenant_id = ? AND batch_id = ?
              AND record_type IN ('COMBO_CARD', 'STORED_VALUE_CARD', 'DISCOUNT_CARD')
              AND status IN ('READY_MATCHED', 'SKIPPED') ORDER BY id
            """, principal.tenantId(), batchId
        );
    }

    private long insertBatch(TenantPrincipal principal, long shopId, String fileName, String hash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO legacy_import_batch (tenant_id, shop_id, batch_no, file_name, file_sha256, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, "PENDING-" + hash.substring(0, 16));
            statement.setString(4, fileName);
            statement.setString(5, hash);
            statement.setLong(6, principal.accountId());
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        jdbcTemplate.update("UPDATE legacy_import_batch SET batch_no = ? WHERE id = ?", "LIB%03d%012d".formatted(principal.tenantId(), id), id);
        return id;
    }

    private void insertRow(
        TenantPrincipal principal,
        long batchId,
        LegacyImportWorkbook.ParsedRow row,
        RowDecision decision
    ) {
        String sourceSystem = emptyToNull(row.values().get("sourceSystem"));
        String sourceNo = emptyToNull(row.values().get(
            "MEMBER".equals(row.recordType()) ? "originalMemberNo" : "originalCardNo"
        ));
        String subject = "MEMBER".equals(row.recordType())
            ? maskPhone(row.values().get("phone"))
            : bounded(sourceNo == null ? "未填写卡号" : sourceNo, 120);
        jdbcTemplate.update(
            """
            INSERT INTO legacy_import_row (
                tenant_id, batch_id, sheet_name, source_row_number, record_type,
                source_system, source_record_no, masked_subject, payload_json,
                status, match_type, matched_member_id, issue_code, issue_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal.tenantId(), batchId, row.sheetName(), row.rowNumber(), row.recordType(),
            sourceSystem, sourceNo, subject, json(row.values()), decision.status(), decision.matchType(),
            decision.memberId(), decision.issueCode(), decision.issueMessage()
        );
    }

    private Map<String, Object> requireBatch(TenantPrincipal principal, long batchId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, shop_id AS shopId, batch_no AS batchNo, file_name AS fileName,
                   file_sha256 AS fileSha256, status, total_rows AS totalRows,
                   ready_rows AS readyRows, conflict_rows AS conflictRows, error_rows AS errorRows,
                   imported_members AS importedMembers, imported_cards AS importedCards,
                   request_hash AS requestHash, started_at AS startedAt, completed_at AS completedAt,
                   created_at AS createdAt
            FROM legacy_import_batch WHERE id = ? AND tenant_id = ?
            """, batchId, principal.tenantId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "导入批次不存在");
        return rows.getFirst();
    }

    private void audit(TenantPrincipal principal, long shopId, String action, long batchId, Map<String, Object> metadata) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data)
            VALUES (?, ?, ?, ?, 'LEGACY_IMPORT_BATCH', ?, CAST(? AS JSON))
            """, principal.tenantId(), shopId, principal.accountId(), action, batchId, json(metadata)
        );
    }

    private void requireManagementRole(TenantPrincipal principal) {
        if (principal.roles().stream().noneMatch(Set.of("ADMIN", "SUPER_ADMIN")::contains)) {
            throw new AccessDeniedException("只有运营管理员可以访问数据导入");
        }
    }

    private Map<String, String> payload(Object value) {
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() { });
        } catch (JacksonException exception) {
            throw new IllegalStateException("导入预检数据损坏", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("导入数据序列化失败", exception);
        }
    }

    private String normalizePhone(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return digits.startsWith("86") && digits.length() == 13 ? digits.substring(2) : digits;
    }

    private String maskPhone(String value) {
        String digits = normalizePhone(value);
        if (digits.length() < 7) return "***";
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private String missing(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.get(key) == null || values.get(key).isBlank()) return key;
        return null;
    }

    private BigDecimal positive(String value) {
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.signum() <= 0) throw new IllegalArgumentException();
        return decimal;
    }

    private BigDecimal nonNegative(String value) {
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.signum() < 0) throw new IllegalArgumentException();
        return decimal;
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private LocalDate localDateOrNull(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        return value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String bounded(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private String safeFileName(String value) {
        String safe = value.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "");
        return bounded(safe, 255);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private Long nullableQueryLong(String sql, Object... args) {
        List<Long> values = jdbcTemplate.queryForList(sql, Long.class, args);
        return values.isEmpty() ? null : values.getFirst();
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalStateException("导入数据主键缺失");
        return number.longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String csv(Object value) {
        String safe = value == null ? "" : String.valueOf(value);
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private record RowDecision(String status, String matchType, Long memberId, String issueCode, String issueMessage) {
        static RowDecision error(String code, String message) {
            return new RowDecision("ERROR", null, null, code, message);
        }

        static RowDecision conflict(String code, String message) {
            return new RowDecision("CONFLICT", null, null, code, message);
        }
    }
}
