package com.face.platform.packageaccount;

import com.face.platform.api.ApiException;
import com.face.platform.masterdata.ServiceCatalogService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PackageCatalogService {

    private static final Set<String> CREATE_STATUSES = Set.of("DRAFT", "ACTIVE");

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;
    private final ServiceCatalogService serviceCatalogService;

    public PackageCatalogService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService,
        ServiceCatalogService serviceCatalogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.serviceCatalogService = serviceCatalogService;
    }

    public List<Map<String, Object>> list(
        TenantPrincipal principal,
        long shopId,
        String status
    ) {
        accessService.requireShopPermission(principal, shopId, "package:view");
        String normalizedStatus = normalizeOptionalStatus(status);
        List<Object> args = new ArrayList<>(List.of(principal.tenantId(), shopId));
        String statusClause = "";
        if (normalizedStatus != null) {
            statusClause = " AND pp.status = ?";
            args.add(normalizedStatus);
        }
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            """
            SELECT pp.id, pp.package_code AS packageCode, pp.name, pp.description,
                   pp.sale_price AS salePrice, pp.validity_days AS validityDays,
                   pp.status, pp.version, pp.shop_id AS shopId,
                   pp.created_at AS createdAt, pp.updated_at AS updatedAt
            FROM package_product pp
            WHERE pp.tenant_id = ?
              AND (pp.shop_id IS NULL OR pp.shop_id = ?)
              %s
            ORDER BY FIELD(pp.status, 'ACTIVE', 'DRAFT', 'INACTIVE'), pp.name, pp.id
            """.formatted(statusClause),
            args.toArray()
        );
        for (Map<String, Object> product : products) {
            product.put(
                "items",
                jdbcTemplate.queryForList(
                    """
                    SELECT id, service_id AS serviceId,
                           service_name_snapshot AS serviceName,
                           quantity_total AS quantity
                    FROM package_product_item
                    WHERE package_product_id = ?
                    ORDER BY sort_order, id
                    """,
                    number(product.get("id"))
                )
            );
        }
        return products;
    }

    @Transactional
    public Map<String, Object> create(
        TenantPrincipal principal,
        long shopId,
        String packageCode,
        String name,
        String description,
        BigDecimal salePrice,
        int validityDays,
        String status,
        List<PackageItemInput> items
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        String safeCode = required(packageCode, "套餐编号", 48).toUpperCase(Locale.ROOT);
        String safeName = required(name, "套餐名称", 120);
        String safeStatus = normalizeCreateStatus(status);
        BigDecimal safePrice = money(salePrice);
        if (validityDays <= 0 || validityDays > 3650) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐有效天数必须在1到3650之间");
        }
        List<ResolvedItem> resolvedItems = resolveItems(principal, shopId, items);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO package_product (
                    tenant_id, shop_id, package_code, name, description,
                    sale_price, validity_days, status, version,
                    created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, principal.tenantId());
            statement.setLong(2, shopId);
            statement.setString(3, safeCode);
            statement.setString(4, safeName);
            statement.setString(5, trimToNull(description));
            statement.setBigDecimal(6, safePrice);
            statement.setInt(7, validityDays);
            statement.setString(8, safeStatus);
            statement.setLong(9, principal.accountId());
            statement.setLong(10, principal.accountId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "套餐产品创建失败");
        }
        long packageProductId = key.longValue();
        insertItems(principal.tenantId(), packageProductId, resolvedItems);
        audit(
            principal, shopId, "PACKAGE_PRODUCT_CREATE",
            "PACKAGE_PRODUCT", packageProductId, safeStatus, 0
        );
        return productResult(packageProductId);
    }

    @Transactional
    public Map<String, Object> update(
        TenantPrincipal principal,
        long shopId,
        long packageProductId,
        String name,
        String description,
        BigDecimal salePrice,
        int validityDays,
        String status,
        List<PackageItemInput> items,
        int version
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        Map<String, Object> current = lockProduct(principal, shopId, packageProductId);
        if (number(current.get("version")) != version) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐产品版本已变化，请刷新后重试");
        }
        String currentStatus = current.get("status").toString();
        String targetStatus = normalizeUpdateStatus(status);
        if (!"DRAFT".equals(currentStatus)) {
            if (!("ACTIVE".equals(currentStatus) && "INACTIVE".equals(targetStatus))) {
                throw new ApiException(HttpStatus.CONFLICT, "已发布套餐只能执行下架");
            }
            int changed = jdbcTemplate.update(
                """
                UPDATE package_product
                SET status = 'INACTIVE', version = version + 1, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND shop_id = ?
                  AND status = 'ACTIVE' AND version = ?
                """,
                principal.accountId(),
                packageProductId,
                principal.tenantId(),
                shopId,
                version
            );
            requireChanged(changed);
            audit(
                principal, shopId, "PACKAGE_PRODUCT_DEACTIVATE",
                "PACKAGE_PRODUCT", packageProductId, "INACTIVE", version + 1
            );
            return productResult(packageProductId);
        }

        String safeName = required(name, "套餐名称", 120);
        BigDecimal safePrice = money(salePrice);
        if (validityDays <= 0 || validityDays > 3650) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐有效天数必须在1到3650之间");
        }
        List<ResolvedItem> resolvedItems = resolveItems(principal, shopId, items);
        int changed = jdbcTemplate.update(
            """
            UPDATE package_product
            SET name = ?, description = ?, sale_price = ?, validity_days = ?,
                status = ?, version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND status = 'DRAFT' AND version = ?
            """,
            safeName,
            trimToNull(description),
            safePrice,
            validityDays,
            targetStatus,
            principal.accountId(),
            packageProductId,
            principal.tenantId(),
            shopId,
            version
        );
        requireChanged(changed);
        jdbcTemplate.update(
            "DELETE FROM package_product_item WHERE package_product_id = ?",
            packageProductId
        );
        insertItems(principal.tenantId(), packageProductId, resolvedItems);
        audit(
            principal, shopId, "PACKAGE_PRODUCT_UPDATE",
            "PACKAGE_PRODUCT", packageProductId, targetStatus, version + 1
        );
        return productResult(packageProductId);
    }

    public Map<String, Object> orderLine(
        TenantPrincipal principal,
        long shopId,
        long packageProductId
    ) {
        accessService.requireShopPermission(principal, shopId, "order:manage");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, name, sale_price AS price
            FROM package_product
            WHERE id = ? AND tenant_id = ?
              AND (shop_id IS NULL OR shop_id = ?)
              AND status = 'ACTIVE'
            LIMIT 1
            """,
            packageProductId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "套餐产品不存在或已下架");
        }
        return rows.getFirst();
    }

    public Map<String, Object> requireActiveProduct(
        TenantPrincipal principal,
        long shopId,
        long packageProductId
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, name, sale_price AS salePrice, validity_days AS validityDays,
                   status, version
            FROM package_product
            WHERE id = ? AND tenant_id = ?
              AND (shop_id IS NULL OR shop_id = ?)
              AND status = 'ACTIVE'
            LIMIT 1
            """,
            packageProductId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "套餐产品不存在或未生效");
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put(
            "items",
            jdbcTemplate.queryForList(
                """
                SELECT id, service_id AS serviceId,
                       service_name_snapshot AS serviceName,
                       quantity_total AS quantity
                FROM package_product_item
                WHERE package_product_id = ?
                ORDER BY sort_order, id
                """,
                packageProductId
            )
        );
        return result;
    }

    private List<ResolvedItem> resolveItems(
        TenantPrincipal principal,
        long shopId,
        List<PackageItemInput> items
    ) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐至少包含一个服务项目");
        }
        List<Long> serviceIds = items.stream().map(PackageItemInput::serviceId).toList();
        if (serviceIds.stream().distinct().count() != serviceIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐服务项目不能重复");
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (Map<String, Object> row :
            serviceCatalogService.requirePackageServices(principal, shopId, serviceIds)) {
            names.put(number(row.get("id")), row.get("name").toString());
        }
        List<ResolvedItem> resolved = new ArrayList<>();
        for (PackageItemInput item : items) {
            BigDecimal quantity = packageQuantity(item.quantity());
            resolved.add(new ResolvedItem(item.serviceId(), names.get(item.serviceId()), quantity));
        }
        return resolved;
    }

    private void insertItems(
        long tenantId,
        long packageProductId,
        List<ResolvedItem> items
    ) {
        int sortOrder = 0;
        for (ResolvedItem item : items) {
            jdbcTemplate.update(
                """
                INSERT INTO package_product_item (
                    tenant_id, package_product_id, service_id,
                    service_name_snapshot, quantity_total, sort_order
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                packageProductId,
                item.serviceId(),
                item.serviceName(),
                item.quantity(),
                sortOrder++
            );
        }
    }

    private Map<String, Object> lockProduct(
        TenantPrincipal principal,
        long shopId,
        long packageProductId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, status, version
            FROM package_product
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            packageProductId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "套餐产品不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> productResult(long packageProductId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT id, package_code AS packageCode, name, sale_price AS salePrice,
                   validity_days AS validityDays, status, version
            FROM package_product
            WHERE id = ?
            """,
            packageProductId
        );
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId,
        String status,
        int version
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action, entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, ?, ?, ?, JSON_OBJECT('status', ?, 'version', ?))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityType,
            entityId,
            status,
            version
        );
    }

    private String normalizeOptionalStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "ACTIVE", "INACTIVE").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐状态不正确");
        }
        return status;
    }

    private String normalizeCreateStatus(String value) {
        String status = value == null ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!CREATE_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新套餐状态只能是草稿或生效");
        }
        return status;
    }

    private String normalizeUpdateStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "ACTIVE", "INACTIVE").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐状态不正确");
        }
        return status;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐价格不能为空");
        }
        try {
            BigDecimal result = value.setScale(2, RoundingMode.UNNECESSARY);
            if (result.signum() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "套餐价格不能为负数");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐价格最多两位小数");
        }
    }

    private BigDecimal packageQuantity(BigDecimal value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐项目次数不能为空");
        }
        try {
            BigDecimal result = value.setScale(4, RoundingMode.UNNECESSARY);
            if (result.signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "套餐项目次数必须大于零");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "套餐项目次数最多四位小数");
        }
    }

    private String required(String value, String label, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.length() > maxLength) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                label + "不能为空且不能超过" + maxLength + "个字符"
            );
        }
        return safe;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "套餐产品已被其他人修改");
        }
    }

    private long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("套餐产品数据不完整");
        }
        return number.longValue();
    }

    public record PackageItemInput(long serviceId, BigDecimal quantity) {
    }

    private record ResolvedItem(long serviceId, String serviceName, BigDecimal quantity) {
    }
}
