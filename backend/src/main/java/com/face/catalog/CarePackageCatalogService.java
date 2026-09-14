package com.face.catalog;

import com.entity.XinnengyuanqicheEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Service
public class CarePackageCatalogService {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CarePackageCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS care_package (" +
            "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, " +
            "shop_id BIGINT UNSIGNED NOT NULL DEFAULT 1, " +
            "package_name VARCHAR(120) NOT NULL, " +
            "package_type VARCHAR(80) NULL, " +
            "brand_name VARCHAR(120) NULL, " +
            "duration_text VARCHAR(80) NULL, " +
            "recommended_interval VARCHAR(80) NULL, " +
            "validity_text VARCHAR(80) NULL, " +
            "applicable_skin_types VARCHAR(255) NULL, " +
            "price DECIMAL(10,2) NOT NULL DEFAULT 0, " +
            "service_count INT UNSIGNED NULL, " +
            "included_services TEXT NULL, " +
            "booking_method VARCHAR(255) NULL, " +
            "cover_url VARCHAR(500) NULL, " +
            "highlights TEXT NULL, " +
            "usage_instructions TEXT NULL, " +
            "precautions TEXT NULL, " +
            "description MEDIUMTEXT NULL, " +
            "click_count INT NOT NULL DEFAULT 0, " +
            "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
            "created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), " +
            "updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), " +
            "PRIMARY KEY (id), " +
            "KEY idx_care_package_shop_status (shop_id, status), " +
            "CONSTRAINT fk_care_package_shop FOREIGN KEY (shop_id) REFERENCES shop(id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='护理套餐'"
        );

        Integer legacyTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = DATABASE() AND table_name = 'xinnengyuanqiche'",
            Integer.class
        );
        if (legacyTableCount == null || legacyTableCount == 0) {
            return;
        }

        // Only migrate real beauty-package rows. Legacy vehicle demo rows all use
        // the EV charging placeholder and must never enter the FACE catalog.
        jdbcTemplate.update(
            "INSERT INTO care_package " +
            "(id, shop_id, package_name, package_type, brand_name, duration_text, recommended_interval, " +
            "validity_text, applicable_skin_types, price, service_count, included_services, booking_method, " +
            "cover_url, highlights, usage_instructions, precautions, description, click_count, status, created_at) " +
            "SELECT x.id, 1, x.qichexinghao, x.qicheleixing, x.pinpai, x.baigonglijiasu, x.zuigaoshisu, " +
            "x.xuhanggonglishu, x.xiaolv, COALESCE(x.jiage, 0), x.zuoweishu, x.donglizongcheng, " +
            "x.chongdianchatou, NULLIF(x.fengmian, ''), x.waixingshiyang, x.chongdianfangan, " +
            "x.jishuguige, x.xiangxijieshao, COALESCE(x.clicknum, 0), 'ACTIVE', x.addtime " +
            "FROM xinnengyuanqiche x " +
            "WHERE x.qichexinghao IS NOT NULL AND x.qichexinghao <> '' " +
            "AND (x.fengmian IS NULL OR x.fengmian = '' OR x.fengmian NOT LIKE '%ev-charging%') " +
            "ON DUPLICATE KEY UPDATE package_name = VALUES(package_name), package_type = VALUES(package_type), " +
            "brand_name = VALUES(brand_name), duration_text = VALUES(duration_text), " +
            "recommended_interval = VALUES(recommended_interval), validity_text = VALUES(validity_text), " +
            "applicable_skin_types = VALUES(applicable_skin_types), price = VALUES(price), " +
            "service_count = VALUES(service_count), included_services = VALUES(included_services), " +
            "booking_method = VALUES(booking_method), cover_url = VALUES(cover_url), highlights = VALUES(highlights), " +
            "usage_instructions = VALUES(usage_instructions), precautions = VALUES(precautions), " +
            "description = VALUES(description), status = 'ACTIVE'"
        );
    }

    public List<Long> activeIds() {
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM care_package WHERE shop_id = 1 AND status = 'ACTIVE' ORDER BY id",
            Long.class
        );
        return ids == null ? Collections.<Long>emptyList() : ids;
    }

    public void syncLegacyToCatalog(XinnengyuanqicheEntity item) {
        if (item == null || item.getId() == null || StringUtils.isBlank(item.getQichexinghao())) {
            throw new IllegalArgumentException("套餐名称不能为空");
        }
        jdbcTemplate.update(
            "INSERT INTO care_package " +
            "(id, shop_id, package_name, package_type, brand_name, duration_text, recommended_interval, " +
            "validity_text, applicable_skin_types, price, service_count, included_services, booking_method, " +
            "cover_url, highlights, usage_instructions, precautions, description, click_count, status) " +
            "VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE') " +
            "ON DUPLICATE KEY UPDATE package_name = VALUES(package_name), package_type = VALUES(package_type), " +
            "brand_name = VALUES(brand_name), duration_text = VALUES(duration_text), " +
            "recommended_interval = VALUES(recommended_interval), validity_text = VALUES(validity_text), " +
            "applicable_skin_types = VALUES(applicable_skin_types), price = VALUES(price), " +
            "service_count = VALUES(service_count), included_services = VALUES(included_services), " +
            "booking_method = VALUES(booking_method), cover_url = VALUES(cover_url), highlights = VALUES(highlights), " +
            "usage_instructions = VALUES(usage_instructions), precautions = VALUES(precautions), " +
            "description = VALUES(description), click_count = VALUES(click_count), status = 'ACTIVE'",
            item.getId(),
            item.getQichexinghao(),
            item.getQicheleixing(),
            item.getPinpai(),
            item.getBaigonglijiasu(),
            item.getZuigaoshisu(),
            item.getXuhanggonglishu(),
            item.getXiaolv(),
            item.getJiage() == null ? 0D : item.getJiage(),
            item.getZuoweishu(),
            item.getDonglizongcheng(),
            item.getChongdianchatou(),
            StringUtils.defaultIfBlank(item.getFengmian(), null),
            item.getWaixingshiyang(),
            item.getChongdianfangan(),
            item.getJishuguige(),
            item.getXiangxijieshao(),
            item.getClicknum() == null ? 0 : item.getClicknum()
        );
    }

    public void deactivate(Long[] ids) {
        if (ids == null || ids.length == 0) return;
        String placeholders = String.join(",", Collections.nCopies(ids.length, "?"));
        jdbcTemplate.update(
            "UPDATE care_package SET status = 'INACTIVE' WHERE id IN (" + placeholders + ")",
            (Object[]) ids
        );
    }
}
