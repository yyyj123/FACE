package com.face.platform.transaction;

import com.face.platform.analytics.AnalyticsQueryPolicy;
import com.face.platform.analytics.TransactionAnalyticsQueryPort;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionAnalyticsQueryService implements TransactionAnalyticsQueryPort {

    private static final String ELIGIBLE_ORDER_STATUSES =
        "('PAID','PARTIALLY_REFUNDED','REFUNDED')";

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    public TransactionAnalyticsQueryService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> overview(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType
    ) {
        AnalyticsQueryPolicy.DateRange range = AnalyticsQueryPolicy.dateRange(fromDate, toDate);
        String normalizedType = AnalyticsQueryPolicy.itemType(itemType);
        List<Long> scope = resolveScope(principal, shopId);
        if (scope.isEmpty()) {
            return emptyOverview(range, normalizedType);
        }

        QueryScope queryScope = queryScope(principal, scope, range);
        Map<String, Object> summary = summary(queryScope);
        AnalyticsQueryPolicy.DateRange previousRange =
            AnalyticsQueryPolicy.previousRange(range);
        Map<String, Object> comparison = comparison(
            previousRange,
            summary(queryScope(principal, scope, previousRange))
        );
        List<Map<String, Object>> shopBreakdown = jdbcTemplate.queryForList(
            """
            SELECT shop_id AS shopId,
                   COUNT(*) AS orderCount,
                   COUNT(DISTINCT member_id) AS consumingCustomerCount,
                   COALESCE(SUM(subtotal_amount), 0) AS grossOrderAmount,
                   COALESCE(SUM(discount_amount), 0) AS discountAmount,
                   COALESCE(SUM(paid_amount), 0) AS collectedAmount,
                   COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                   COALESCE(SUM(paid_amount - refunded_amount), 0) AS netCollectedAmount
            FROM sales_order
            %s
            GROUP BY shop_id
            ORDER BY netCollectedAmount DESC, shop_id
            """.formatted(queryScope.orderWhere()),
            queryScope.orderArgs().toArray()
        );

        ItemQuery itemQuery = itemQuery(queryScope, normalizedType);
        List<Map<String, Object>> typeBreakdown = jdbcTemplate.queryForList(
            """
            SELECT soi.item_type AS itemType,
                   COUNT(DISTINCT so.id) AS orderCount,
                   COUNT(DISTINCT so.member_id) AS consumingCustomerCount,
                   COALESCE(SUM(soi.quantity), 0) AS quantity,
                   COALESCE(SUM(soi.line_amount), 0) AS lineSalesAmountBeforeRefund
            FROM sales_order_item soi
            JOIN sales_order so ON so.id = soi.order_id
            %s
            GROUP BY soi.item_type
            ORDER BY FIELD(soi.item_type, 'SERVICE', 'PRODUCT')
            """.formatted(itemQuery.where()),
            itemQuery.args().toArray()
        );
        List<Map<String, Object>> categoryComposition = jdbcTemplate.queryForList(
            """
            SELECT soi.item_type AS itemType,
                   COALESCE(NULLIF(soi.category_name_snapshot, ''), '未归类') AS categoryName,
                   COUNT(DISTINCT so.id) AS orderCount,
                   COUNT(DISTINCT so.member_id) AS consumingCustomerCount,
                   COALESCE(SUM(soi.quantity), 0) AS quantity,
                   COALESCE(SUM(soi.line_amount), 0) AS lineSalesAmountBeforeRefund
            FROM sales_order_item soi
            JOIN sales_order so ON so.id = soi.order_id
            %s
            GROUP BY soi.item_type, COALESCE(NULLIF(soi.category_name_snapshot, ''), '未归类')
            ORDER BY lineSalesAmountBeforeRefund DESC, categoryName
            """.formatted(itemQuery.where()),
            itemQuery.args().toArray()
        );
        ItemQuery productItemQuery = itemQuery(queryScope, "PRODUCT");
        List<Map<String, Object>> brandComposition = "SERVICE".equals(normalizedType)
            ? List.of()
            : jdbcTemplate.queryForList(
                """
                SELECT COALESCE(NULLIF(soi.brand_name_snapshot, ''), '未标注品牌') AS brandName,
                       COUNT(DISTINCT so.id) AS orderCount,
                       COUNT(DISTINCT so.member_id) AS consumingCustomerCount,
                       COALESCE(SUM(soi.quantity), 0) AS quantity,
                       COALESCE(SUM(soi.line_amount), 0) AS lineSalesAmountBeforeRefund
                FROM sales_order_item soi
                JOIN sales_order so ON so.id = soi.order_id
                %s
                GROUP BY COALESCE(NULLIF(soi.brand_name_snapshot, ''), '未标注品牌')
                ORDER BY lineSalesAmountBeforeRefund DESC, brandName
                LIMIT 20
                """.formatted(productItemQuery.where()),
                productItemQuery.args().toArray()
            );
        List<Map<String, Object>> ranking = jdbcTemplate.queryForList(
            """
            SELECT soi.item_type AS itemType,
                   COALESCE(soi.service_id, soi.product_id) AS itemId,
                   soi.item_name_snapshot AS itemName,
                   COALESCE(NULLIF(soi.category_name_snapshot, ''), '未归类') AS categoryName,
                   NULLIF(soi.brand_name_snapshot, '') AS brandName,
                   COUNT(DISTINCT so.id) AS orderCount,
                   COUNT(DISTINCT so.member_id) AS consumingCustomerCount,
                   COALESCE(SUM(soi.quantity), 0) AS quantity,
                   COALESCE(SUM(soi.line_amount), 0) AS lineSalesAmountBeforeRefund
            FROM sales_order_item soi
            JOIN sales_order so ON so.id = soi.order_id
            %s
            GROUP BY soi.item_type,
                     COALESCE(soi.service_id, soi.product_id),
                     soi.item_name_snapshot,
                     COALESCE(NULLIF(soi.category_name_snapshot, ''), '未归类'),
                     NULLIF(soi.brand_name_snapshot, '')
            ORDER BY lineSalesAmountBeforeRefund DESC, quantity DESC, itemName
            LIMIT 20
            """.formatted(itemQuery.where()),
            itemQuery.args().toArray()
        );
        List<Map<String, Object>> trend = jdbcTemplate.queryForList(
            """
            SELECT business_date AS businessDate,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(paid_amount), 0) AS collectedAmount,
                   COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                   COALESCE(SUM(paid_amount - refunded_amount), 0) AS netCollectedAmount
            FROM sales_order
            %s
            GROUP BY business_date
            ORDER BY business_date
            """.formatted(queryScope.orderWhere()),
            queryScope.orderArgs().toArray()
        );

        Map<String, Object> result = baseResult(range, normalizedType, scope);
        result.put("summary", summary);
        result.put("comparison", comparison);
        result.put("shopBreakdown", shopBreakdown);
        result.put("typeBreakdown", typeBreakdown);
        result.put("categoryComposition", categoryComposition);
        result.put("brandComposition", brandComposition);
        result.put("ranking", ranking);
        result.put("trend", trend);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> lines(
        TenantPrincipal principal,
        Long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String itemType,
        int page,
        int pageSize
    ) {
        AnalyticsQueryPolicy.DateRange range = AnalyticsQueryPolicy.dateRange(fromDate, toDate);
        String normalizedType = AnalyticsQueryPolicy.itemType(itemType);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        List<Long> scope = resolveScope(principal, shopId);
        if (scope.isEmpty()) {
            Map<String, Object> result = baseResult(range, normalizedType, scope);
            result.put("records", List.of());
            result.put("total", 0L);
            result.put("page", safePage);
            result.put("pageSize", safePageSize);
            return result;
        }

        QueryScope queryScope = queryScope(principal, scope, range);
        ItemQuery itemQuery = itemQuery(queryScope, normalizedType);
        Long total = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM sales_order_item soi
            JOIN sales_order so ON so.id = soi.order_id
            %s
            """.formatted(itemQuery.where()),
            Long.class,
            itemQuery.args().toArray()
        );
        List<Object> listArgs = new ArrayList<>(itemQuery.args());
        listArgs.add(safePageSize);
        listArgs.add((safePage - 1) * safePageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT so.id AS orderId,
                   so.order_no AS orderNo,
                   so.shop_id AS shopId,
                   so.business_date AS businessDate,
                   so.status AS orderStatus,
                   so.refunded_amount AS orderRefundedAmount,
                   soi.id AS orderItemId,
                   soi.item_type AS itemType,
                   soi.item_name_snapshot AS itemName,
                   COALESCE(NULLIF(soi.category_name_snapshot, ''), '未归类') AS categoryName,
                   NULLIF(soi.brand_name_snapshot, '') AS brandName,
                   soi.quantity,
                   soi.unit_price AS unitPrice,
                   soi.discount_amount AS discountAmount,
                   soi.line_amount AS lineSalesAmountBeforeRefund,
                   soi.dimension_snapshot_quality AS dimensionSnapshotQuality
            FROM sales_order_item soi
            JOIN sales_order so ON so.id = soi.order_id
            %s
            ORDER BY so.business_date DESC, so.id DESC, soi.id
            LIMIT ? OFFSET ?
            """.formatted(itemQuery.where()),
            listArgs.toArray()
        );

        Map<String, Object> result = baseResult(range, normalizedType, scope);
        result.put("records", records);
        result.put("total", total == null ? 0L : total);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        return result;
    }

    private List<Long> resolveScope(TenantPrincipal principal, Long shopId) {
        List<Long> accessible = tenantAccessService.accessibleShopIds(principal, "analytics:view");
        if (accessible.isEmpty()) {
            tenantAccessService.requireManagementPermission(principal, "analytics:view");
        }
        if (shopId == null) {
            return accessible;
        }
        tenantAccessService.requireShopPermission(principal, shopId, "analytics:view");
        return List.of(shopId);
    }

    private QueryScope queryScope(
        TenantPrincipal principal,
        List<Long> scope,
        AnalyticsQueryPolicy.DateRange range
    ) {
        String placeholders = String.join(",", java.util.Collections.nCopies(scope.size(), "?"));
        String where = """
            WHERE tenant_id = ?
              AND shop_id IN (%s)
              AND business_date BETWEEN ? AND ?
              AND status IN %s
            """.formatted(placeholders, ELIGIBLE_ORDER_STATUSES);
        List<Object> args = new ArrayList<>();
        args.add(principal.tenantId());
        args.addAll(scope);
        args.add(range.fromDate());
        args.add(range.toDate());
        return new QueryScope(where, args);
    }

    private ItemQuery itemQuery(QueryScope scope, String itemType) {
        String where = scope.orderWhere()
            .replace("tenant_id", "so.tenant_id")
            .replace("shop_id", "so.shop_id")
            .replace("business_date", "so.business_date")
            .replace("status", "so.status");
        List<Object> args = new ArrayList<>(scope.orderArgs());
        if (!"ALL".equals(itemType)) {
            where += " AND soi.item_type = ?";
            args.add(itemType);
        }
        return new ItemQuery(where, args);
    }

    private Map<String, Object> emptyOverview(
        AnalyticsQueryPolicy.DateRange range,
        String itemType
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderCount", 0L);
        summary.put("consumingCustomerCount", 0L);
        summary.put("grossOrderAmount", 0);
        summary.put("discountAmount", 0);
        summary.put("collectedAmount", 0);
        summary.put("refundedAmount", 0);
        summary.put("netCollectedAmount", 0);
        Map<String, Object> result = baseResult(range, itemType, List.of());
        result.put("summary", summary);
        result.put(
            "comparison",
            comparison(AnalyticsQueryPolicy.previousRange(range), new LinkedHashMap<>(summary))
        );
        result.put("shopBreakdown", List.of());
        result.put("typeBreakdown", List.of());
        result.put("categoryComposition", List.of());
        result.put("brandComposition", List.of());
        result.put("ranking", List.of());
        result.put("trend", List.of());
        return result;
    }

    private Map<String, Object> baseResult(
        AnalyticsQueryPolicy.DateRange range,
        String itemType,
        List<Long> scope
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromDate", range.fromDate());
        result.put("toDate", range.toDate());
        result.put("itemType", itemType);
        result.put("shopIds", scope);
        result.put("asOf", Instant.now());
        Map<String, Object> dataQuality = new LinkedHashMap<>();
        dataQuality.put("metricVersion", "M6-04-v1");
        dataQuality.put("businessDateBasis", "ORDER_BUSINESS_DATE");
        dataQuality.put("refundAllocation", "ORDER_LEVEL_ONLY");
        dataQuality.put(
            "lineSalesDefinition",
            "已支付订单行成交额，未按品项分摊退款"
        );
        dataQuality.put(
            "dimensionSnapshot",
            "新订单使用成交时快照，历史订单可能来自当前主数据回填"
        );
        dataQuality.put(
            "brandSalesDefinition",
            "品牌构成仅统计零售商品的退款前订单行成交额，未标注品牌单独列示"
        );
        dataQuality.put(
            "shopComparisonDefinition",
            "门店对比仅汇总当前账号授权范围内的有效订单，退款按订单级冲正"
        );
        dataQuality.put(
            "periodComparisonDefinition",
            "上一周期与当前周期天数相同且紧邻当前周期，均使用相同授权门店和订单级口径"
        );
        result.put("dataQuality", dataQuality);
        return result;
    }

    private Map<String, Object> summary(QueryScope queryScope) {
        return jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS orderCount,
                   COUNT(DISTINCT member_id) AS consumingCustomerCount,
                   COALESCE(SUM(subtotal_amount), 0) AS grossOrderAmount,
                   COALESCE(SUM(discount_amount), 0) AS discountAmount,
                   COALESCE(SUM(paid_amount), 0) AS collectedAmount,
                   COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                   COALESCE(SUM(paid_amount - refunded_amount), 0) AS netCollectedAmount
            FROM sales_order
            %s
            """.formatted(queryScope.orderWhere()),
            queryScope.orderArgs().toArray()
        );
    }

    private Map<String, Object> comparison(
        AnalyticsQueryPolicy.DateRange previousRange,
        Map<String, Object> previousSummary
    ) {
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("previousFromDate", previousRange.fromDate());
        comparison.put("previousToDate", previousRange.toDate());
        comparison.put("previousSummary", previousSummary);
        return comparison;
    }

    private record QueryScope(String orderWhere, List<Object> orderArgs) {
    }

    private record ItemQuery(String where, List<Object> args) {
    }
}
