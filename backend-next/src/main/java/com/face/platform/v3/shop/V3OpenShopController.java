package com.face.platform.v3.shop;

import com.face.platform.shop.ShopContextService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/open")
public class V3OpenShopController {

    private final ShopContextService shopContextService;

    public V3OpenShopController(ShopContextService shopContextService) {
        this.shopContextService = shopContextService;
    }

    @GetMapping("/shop-context")
    public V3ApiResponse<Map<String, Object>> shopContext(HttpServletRequest request) {
        ShopContextService.ShopContext context = shopContextService.requirePublicShop(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shop_id", context.shopId());
        data.put("name", context.name());
        data.put("phone", context.phone());
        data.put("address", context.address());
        data.put("business_hours", context.businessHours());
        data.put("timezone", context.timezone());
        data.put("business_mode", context.businessMode());
        return V3ApiResponse.success(data, V3RequestSupport.requestId(request));
    }
}
