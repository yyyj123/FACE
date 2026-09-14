package com.face.api;

import com.annotation.IgnoreAuth;
import com.utils.PageUtils;
import com.utils.R;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/care-packages")
public class CarePackageController {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CarePackageController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @IgnoreAuth
    @GetMapping
    public R list(@RequestParam Map<String, Object> params) {
        int page = positiveInt(params.get("page"), 1);
        int limit = Math.min(positiveInt(params.get("limit"), 20), 100);
        List<Object> args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE p.shop_id = 1 AND p.status = 'ACTIVE' ");
        appendLike(where, args, "p.package_name", params.get("qichexinghao"));
        appendLike(where, args, "p.package_type", params.get("qicheleixing"));
        appendEqual(where, args, "p.brand_name", params.get("pinpai"));
        appendLike(where, args, "p.included_services", params.get("donglizongcheng"));
        appendLike(where, args, "p.highlights", params.get("waixingshiyang"));

        Integer total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM care_package p" + where,
            args.toArray(),
            Integer.class
        );
        String sort = sortColumn(stringValue(params.get("sidx")));
        String order = "asc".equalsIgnoreCase(stringValue(params.get("order"))) ? "ASC" : "DESC";
        List<Object> pageArgs = new ArrayList<Object>(args);
        pageArgs.add(limit);
        pageArgs.add((page - 1) * limit);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectSql() + where + " ORDER BY " + sort + " " + order + " LIMIT ? OFFSET ?",
            pageArgs.toArray()
        );
        return R.ok().put("data", new PageUtils(rows, total == null ? 0 : total, limit, page));
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public R detail(@PathVariable("id") Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            selectSql() + " WHERE p.id = ? AND p.shop_id = 1 AND p.status = 'ACTIVE'",
            id
        );
        if (rows.isEmpty()) return R.error(404, "护理套餐不存在或已下架");
        return R.ok().put("data", rows.get(0));
    }

    @IgnoreAuth
    @PostMapping("/{id}/view")
    public R recordView(@PathVariable("id") Long id) {
        int changed = jdbcTemplate.update(
            "UPDATE care_package SET click_count = click_count + 1 WHERE id = ? AND status = 'ACTIVE'",
            id
        );
        return changed > 0 ? R.ok() : R.error(404, "护理套餐不存在或已下架");
    }

    private String selectSql() {
        return "SELECT p.id, p.package_name AS qichexinghao, p.package_type AS qicheleixing, " +
            "p.brand_name AS pinpai, p.duration_text AS baigonglijiasu, " +
            "p.recommended_interval AS zuigaoshisu, p.validity_text AS xuhanggonglishu, " +
            "p.applicable_skin_types AS xiaolv, p.price AS jiage, p.service_count AS zuoweishu, " +
            "p.included_services AS donglizongcheng, p.booking_method AS chongdianchatou, " +
            "p.cover_url AS fengmian, p.highlights AS waixingshiyang, " +
            "p.usage_instructions AS chongdianfangan, p.precautions AS jishuguige, " +
            "p.description AS xiangxijieshao, p.created_at AS addtime, p.click_count AS clicknum, " +
            "(SELECT COUNT(*) FROM storeup s WHERE s.refid = p.id AND s.tablename = 'xinnengyuanqiche' " +
            "AND s.type = '1') AS storeupnum FROM care_package p";
    }

    private void appendLike(StringBuilder where, List<Object> args, String column, Object value) {
        String text = stringValue(value).replace("%", "");
        if (StringUtils.isNotBlank(text)) {
            where.append(" AND ").append(column).append(" LIKE ? ");
            args.add("%" + text + "%");
        }
    }

    private void appendEqual(StringBuilder where, List<Object> args, String column, Object value) {
        String text = stringValue(value);
        if (StringUtils.isNotBlank(text)) {
            where.append(" AND ").append(column).append(" = ? ");
            args.add(text);
        }
    }

    private String sortColumn(String requested) {
        Map<String, String> allowed = new HashMap<String, String>();
        allowed.put("id", "p.id");
        allowed.put("addtime", "p.created_at");
        allowed.put("jiage", "p.price");
        allowed.put("clicknum", "p.click_count");
        allowed.put("storeupnum", "storeupnum");
        return allowed.containsKey(requested) ? allowed.get(requested) : "p.id";
    }

    private int positiveInt(Object value, int fallback) {
        try {
            int parsed = Integer.parseInt(stringValue(value));
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
