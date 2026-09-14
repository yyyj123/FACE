package com.face.platform.analytics;

import com.face.platform.api.ApiException;
import com.face.platform.idempotency.RequestHash;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.dao.DuplicateKeyException;
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

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsReportSnapshotService {

    private static final Duration RETENTION = Duration.ofDays(30);
    private static final String EXPORT_PERMISSION = "analytics:export";
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final TransactionAnalyticsQueryPort analytics;
    private final ObjectMapper objectMapper;

    public AnalyticsReportSnapshotService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        TransactionAnalyticsQueryPort analytics,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.analytics = analytics;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        String idempotencyKey,
        CreateRequest request
    ) {
        String safeKey = idempotencyKey(idempotencyKey);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "报表请求不能为空");
        }
        AnalyticsReportPolicy.ValidatedRequest validated = AnalyticsReportPolicy.validate(
            request.shopId(), request.fromDate(), request.toDate(), request.itemType(), request.format()
        );
        List<Long> scope = exportScope(principal, validated.shopId());
        String requestHash = RequestHash.of(
            validated.reportType(),
            validated.format(),
            validated.fromDate(),
            validated.toDate(),
            validated.itemType(),
            scope
        );

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            """
            SELECT id, report_type, format,
                   CASE WHEN expires_at <= CURRENT_TIMESTAMP(3) THEN 'EXPIRED' ELSE status END AS status,
                   shop_ids_json,
                   from_date, to_date, item_type, metric_version,
                   request_hash, content_sha256, content_bytes, row_count,
                   ready_at, expires_at, created_at
            FROM report_snapshot
            WHERE tenant_id = ? AND requested_by_account_id = ? AND idempotency_key = ?
            FOR UPDATE
            """,
            principal.tenantId(), principal.accountId(), safeKey
        );
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            if (!requestHash.equals(text(row, "request_hash", "requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "该幂等键已用于不同的报表请求");
            }
            return metadata(row);
        }

        Long queryShopId = validated.shopId();
        if (queryShopId == null && scope.size() == 1) queryShopId = scope.getFirst();
        Map<String, Object> overview = analytics.overview(
            principal,
            queryShopId,
            validated.fromDate(),
            validated.toDate(),
            validated.itemType()
        );
        List<Long> actualScope = longList(overview.get("shopIds"));
        if (!actualScope.equals(scope)) {
            throw new ApiException(HttpStatus.CONFLICT, "报表查询范围与导出授权范围不一致");
        }
        AnalyticsReportCsv.GeneratedCsv csv = AnalyticsReportCsv.generate(overview);
        String metricVersion = metricVersion(overview);
        Instant readyAt = Instant.now();
        Instant expiresAt = readyAt.plus(RETENTION);
        String scopeJson = writeJson(scope);

        KeyHolder holder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                    """
                    INSERT INTO report_snapshot (
                        tenant_id, requested_by_account_id, report_type, format, status,
                        shop_ids_json, from_date, to_date, item_type, metric_version,
                        idempotency_key, request_hash, content, content_sha256,
                        content_bytes, row_count, ready_at, expires_at
                    ) VALUES (?, ?, ?, ?, 'READY', CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, principal.tenantId());
                statement.setLong(2, principal.accountId());
                statement.setString(3, validated.reportType());
                statement.setString(4, validated.format());
                statement.setString(5, scopeJson);
                statement.setObject(6, validated.fromDate());
                statement.setObject(7, validated.toDate());
                statement.setString(8, validated.itemType());
                statement.setString(9, metricVersion);
                statement.setString(10, safeKey);
                statement.setString(11, requestHash);
                statement.setBytes(12, csv.bytes());
                statement.setString(13, csv.sha256());
                statement.setLong(14, csv.bytes().length);
                statement.setInt(15, csv.rowCount());
                statement.setTimestamp(16, Timestamp.from(readyAt));
                statement.setTimestamp(17, Timestamp.from(expiresAt));
                return statement;
            }, holder);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "报表快照正在由同一幂等请求创建");
        }
        Number generated = holder.getKey();
        if (generated == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "报表快照创建失败");
        }
        long reportId = generated.longValue();
        audit(
            principal,
            scope.size() == 1 ? scope.getFirst() : null,
            reportId,
            "ANALYTICS_REPORT_CREATED",
            csv.sha256(),
            csv.bytes().length
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", reportId);
        result.put("reportType", validated.reportType());
        result.put("format", validated.format());
        result.put("status", "READY");
        result.put("shopIds", scope);
        result.put("fromDate", validated.fromDate());
        result.put("toDate", validated.toDate());
        result.put("itemType", validated.itemType());
        result.put("metricVersion", metricVersion);
        result.put("contentSha256", csv.sha256());
        result.put("contentBytes", csv.bytes().length);
        result.put("rowCount", csv.rowCount());
        result.put("readyAt", readyAt);
        result.put("expiresAt", expiresAt);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(TenantPrincipal principal, int page, int pageSize) {
        accessService.requirePermission(principal, EXPORT_PERMISSION);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<Long> currentScope = currentExportScope(principal);
        String placeholders = String.join(",", java.util.Collections.nCopies(currentScope.size(), "?"));
        String scopePredicate = """
             AND NOT EXISTS (
               SELECT 1
               FROM JSON_TABLE(
                 rs.shop_ids_json,
                 '$[*]' COLUMNS(shop_id BIGINT PATH '$')
               ) snapshot_scope
               WHERE snapshot_scope.shop_id NOT IN (%s)
             )
            """.formatted(placeholders);
        List<Object> listArgs = new ArrayList<>();
        listArgs.add(principal.tenantId());
        listArgs.add(principal.accountId());
        listArgs.addAll(currentScope);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT rs.id, rs.report_type, rs.format,
                   CASE WHEN expires_at <= CURRENT_TIMESTAMP(3) THEN 'EXPIRED' ELSE status END AS status,
                   rs.shop_ids_json, rs.from_date, rs.to_date, rs.item_type, rs.metric_version,
                   rs.content_sha256, rs.content_bytes, rs.row_count,
                   rs.ready_at, rs.expires_at, rs.created_at
            FROM report_snapshot rs
            WHERE rs.tenant_id = ? AND rs.requested_by_account_id = ?%s
            ORDER BY id DESC LIMIT ? OFFSET ?
            """.formatted(scopePredicate),
            listArgs.toArray()
        );
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(principal.tenantId());
        countArgs.add(principal.accountId());
        countArgs.addAll(currentScope);
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM report_snapshot rs
            WHERE rs.tenant_id = ? AND rs.requested_by_account_id = ?%s
            """.formatted(scopePredicate),
            Long.class,
            countArgs.toArray()
        );
        return Map.of(
            "records", records.stream().map(this::metadata).toList(),
            "total", total == null ? 0L : total,
            "page", safePage,
            "pageSize", safeSize
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(TenantPrincipal principal, long reportId) {
        accessService.requirePermission(principal, EXPORT_PERMISSION);
        Map<String, Object> row = requireRow(principal, reportId, false);
        if (!currentExportScope(principal).containsAll(readShopIds(row.get("shop_ids_json")))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "报表快照不存在");
        }
        return metadata(row);
    }

    @Transactional
    public Download download(TenantPrincipal principal, long reportId) {
        Map<String, Object> row = requireRow(principal, reportId, true);
        List<Long> scope = readShopIds(row.get("shop_ids_json"));
        List<Long> currentScope = new ArrayList<>(accessService.accessibleShopIds(principal, EXPORT_PERMISSION));
        currentScope.sort(Long::compareTo);
        if (!currentScope.containsAll(scope)) {
            throw new AccessDeniedException("当前账号已不再拥有该报表范围的导出权限");
        }
        Instant expiresAt = instant(row.get("expires_at"));
        if (!Instant.now().isBefore(expiresAt)) {
            throw new ApiException(HttpStatus.GONE, "报表快照已过期，请重新生成");
        }
        byte[] content = (byte[]) row.get("content");
        String sha256 = text(row, "content_sha256", "contentSha256");
        audit(
            principal,
            scope.size() == 1 ? scope.getFirst() : null,
            reportId,
            "ANALYTICS_REPORT_DOWNLOADED",
            sha256,
            content.length
        );
        return new Download(reportId, content, sha256, "sales-overview-" + reportId + ".csv");
    }

    private List<Long> exportScope(TenantPrincipal principal, Long requestedShopId) {
        List<Long> exportScope = new ArrayList<>(
            accessService.accessibleShopIds(principal, EXPORT_PERMISSION)
        );
        exportScope.sort(Long::compareTo);
        if (exportScope.isEmpty()) {
            accessService.requireManagementPermission(principal, EXPORT_PERMISSION);
            throw new AccessDeniedException("当前账号没有可导出的门店范围");
        }
        if (requestedShopId != null) {
            if (!exportScope.contains(requestedShopId)) {
                throw new AccessDeniedException("当前账号没有该门店的导出权限");
            }
            return List.of(requestedShopId);
        }
        List<Long> viewScope = new ArrayList<>(
            accessService.accessibleShopIds(principal, "analytics:view")
        );
        viewScope.sort(Long::compareTo);
        if (!viewScope.equals(exportScope)) {
            throw new ApiException(HttpStatus.CONFLICT, "全部门店的查看与导出范围不一致，请选择单店生成");
        }
        return List.copyOf(exportScope);
    }

    private List<Long> currentExportScope(TenantPrincipal principal) {
        List<Long> currentScope = new ArrayList<>(
            accessService.accessibleShopIds(principal, EXPORT_PERMISSION)
        );
        currentScope.sort(Long::compareTo);
        if (currentScope.isEmpty()) {
            throw new AccessDeniedException("当前账号没有可导出的门店范围");
        }
        return List.copyOf(currentScope);
    }

    private Map<String, Object> requireRow(
        TenantPrincipal principal,
        long reportId,
        boolean includeContent
    ) {
        if (reportId <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "报表标识不正确");
        String contentColumn = includeContent ? ", content" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, report_type, format,
                   CASE WHEN expires_at <= CURRENT_TIMESTAMP(3) THEN 'EXPIRED' ELSE status END AS status,
                   shop_ids_json, from_date, to_date, item_type, metric_version,
                   content_sha256, content_bytes, row_count, ready_at, expires_at, created_at%s
            FROM report_snapshot
            WHERE id = ? AND tenant_id = ? AND requested_by_account_id = ?
            """.formatted(contentColumn),
            reportId, principal.tenantId(), principal.accountId()
        );
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "报表快照不存在");
        return rows.getFirst();
    }

    private Map<String, Object> metadata(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "id", row, "id");
        put(result, "reportType", row, "report_type", "reportType");
        put(result, "format", row, "format");
        put(result, "status", row, "status");
        Object shops = first(row, "shop_ids_json", "shopIds");
        if (shops != null) result.put("shopIds", readShopIds(shops));
        put(result, "fromDate", row, "from_date", "fromDate");
        put(result, "toDate", row, "to_date", "toDate");
        put(result, "itemType", row, "item_type", "itemType");
        put(result, "metricVersion", row, "metric_version", "metricVersion");
        put(result, "contentSha256", row, "content_sha256", "contentSha256");
        put(result, "contentBytes", row, "content_bytes", "contentBytes");
        put(result, "rowCount", row, "row_count", "rowCount");
        put(result, "readyAt", row, "ready_at", "readyAt");
        put(result, "expiresAt", row, "expires_at", "expiresAt");
        put(result, "createdAt", row, "created_at", "createdAt");
        return result;
    }

    private void audit(
        TenantPrincipal principal,
        Long shopId,
        long reportId,
        String action,
        String contentHash,
        long contentBytes
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, ?, 'REPORT_SNAPSHOT', ?,
                      JSON_OBJECT('contentSha256', ?, 'contentBytes', ?))
            """,
            principal.tenantId(), shopId, principal.accountId(), action,
            reportId, contentHash, contentBytes
        );
    }

    private String metricVersion(Map<String, Object> overview) {
        Object quality = overview.get("dataQuality");
        if (!(quality instanceof Map<?, ?> map) || map.get("metricVersion") == null) {
            throw new ApiException(HttpStatus.CONFLICT, "经营指标版本缺失，禁止生成无版本报表");
        }
        return map.get("metricVersion").toString();
    }

    private String idempotencyKey(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > 100 || !safe.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key 格式不正确");
        }
        return safe;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "报表授权范围序列化失败");
        }
    }

    private List<Long> readShopIds(Object value) {
        if (value instanceof List<?> list) return longList(list);
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<List<Long>>() { });
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "报表授权范围数据损坏");
        }
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Long> result = list.stream().map(item -> {
            if (item instanceof Number number) return number.longValue();
            return Long.parseLong(item.toString());
        }).sorted().toList();
        return List.copyOf(result);
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(DATABASE_ZONE).toInstant();
        }
        return Instant.parse(value.toString());
    }

    private void put(Map<String, Object> target, String key, Map<String, Object> row, String... sourceKeys) {
        Object value = first(row, sourceKeys);
        if (value != null) target.put(key, value);
    }

    private Object first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) return row.get(key);
        }
        return null;
    }

    private String text(Map<String, Object> row, String... keys) {
        Object value = first(row, keys);
        return value == null ? null : value.toString();
    }

    public record CreateRequest(
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType,
        String format
    ) {
    }

    public record Download(long reportId, byte[] content, String sha256, String fileName) {
    }
}
