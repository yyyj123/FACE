package com.face.platform.transaction;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class TransactionPackageContextService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService accessService;

    public TransactionPackageContextService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService accessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public Map<String, Object> requirePaidPackageLine(
        TenantPrincipal principal,
        long shopId,
        long orderId,
        long memberId,
        long packageProductId
    ) {
        accessService.requireShopPermission(principal, shopId, "package:manage");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT so.id AS orderId, so.member_id AS memberId,
                   soi.id AS orderItemId, soi.quantity, soi.line_amount AS lineAmount
            FROM sales_order so
            JOIN sales_order_item soi
              ON soi.order_id = so.id
             AND soi.item_type = 'PACKAGE'
             AND soi.package_product_id = ?
            WHERE so.id = ?
              AND so.tenant_id = ?
              AND so.shop_id = ?
              AND so.member_id = ?
              AND so.status = 'PAID'
            LIMIT 1
            """,
            packageProductId,
            orderId,
            principal.tenantId(),
            shopId,
            memberId
        );
        if (rows.isEmpty()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "只有包含该套餐且已全额支付的会员订单可以发放套餐"
            );
        }
        Map<String, Object> context = rows.getFirst();
        BigDecimal quantity = decimal(context.get("quantity"));
        if (quantity.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "订单套餐数量不正确");
        }
        return context;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }
}
