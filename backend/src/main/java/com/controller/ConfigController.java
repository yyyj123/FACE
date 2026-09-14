
package com.controller;


import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.annotation.IgnoreAuth;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.ConfigEntity;
import com.service.ConfigService;
import com.utils.MPUtil;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.ValidatorUtils;

/**
 * 登录相关
 */
@RequestMapping("config")
@RestController
public class ConfigController{
	
	@Autowired
	private ConfigService configService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
     * 列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,ConfigEntity config){
        return R.ok().put("data", bannerPage(params, config));
    }
    
	/**
     * 列表
     */
    @IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,ConfigEntity config){
        return R.ok().put("data", bannerPage(params, config));
    }

    @IgnoreAuth
    @RequestMapping("/homepage-title")
    public R homepageTitle(){
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT value FROM config WHERE name = 'homepageHeroTitle' ORDER BY id DESC LIMIT 1"
        );
        String title = rows.isEmpty() ? "为每一次护理预留从容" : stringValue(rows.get(0).get("value"));
        if (title.trim().isEmpty()) title = "为每一次护理预留从容";
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("title", title);
        return R.ok().put("data", data);
    }

    @PostMapping("/homepage-title/update")
    public R updateHomepageTitle(@RequestBody Map<String, Object> body){
        String title = body.get("title") == null ? "" : body.get("title").toString().trim();
        if (title.isEmpty()) return R.error("首页主标题不能为空");
        if (title.length() > 40) return R.error("首页主标题不能超过40个字");
        int updated = jdbcTemplate.update(
            "UPDATE config SET value = ? WHERE name = 'homepageHeroTitle'",
            title
        );
        if (updated == 0) {
            jdbcTemplate.update(
                "INSERT INTO config (name, value, url) VALUES ('homepageHeroTitle', ?, '')",
                title
            );
        }
        return R.ok();
    }

    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") String id){
        return R.ok().put("data", bannerConfig(Long.valueOf(id)));
    }
    
    /**
     * 详情
     */
    @IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") String id){
        return R.ok().put("data", bannerConfig(Long.valueOf(id)));
    }
    
    /**
     * 根据name获取信息
     */
    @RequestMapping("/info")
    public R infoByName(@RequestParam String name){
        ConfigEntity config = configService.selectOne(new EntityWrapper<ConfigEntity>().eq("name", "faceFile"));
        return R.ok().put("data", config);
    }
    
    /**
     * 保存
     */
    @PostMapping("/save")
    public R save(@RequestBody ConfigEntity config){
        Long shopId = jdbcTemplate.queryForObject("SELECT id FROM shop ORDER BY id LIMIT 1", Long.class);
        Integer nextSort = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sort_order), 0) + 10 FROM banner WHERE shop_id = ?",
            Integer.class, shopId
        );
        jdbcTemplate.update(
            "INSERT INTO banner (shop_id, title, image_url, target_type, target_value, sort_order, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')",
            shopId, config.getName(), config.getValue(), targetType(config.getUrl()), config.getUrl(), nextSort
        );
        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody ConfigEntity config){
        jdbcTemplate.update(
            "UPDATE banner SET title = ?, image_url = ?, target_type = ?, target_value = ?, status = 'ACTIVE' WHERE id = ?",
            config.getName(), config.getValue(), targetType(config.getUrl()), config.getUrl(), config.getId()
        );
        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        if (ids != null) {
            for (Long id : ids) {
                jdbcTemplate.update("DELETE FROM banner WHERE id = ?", id);
            }
        }
        return R.ok();
    }

    private PageUtils bannerPage(Map<String, Object> params, ConfigEntity config) {
        int current = intParam(params, "page", 1);
        int limit = intParam(params, "limit", 10);
        int offset = (current - 1) * limit;
        String title = config == null ? null : config.getName();
        boolean filterTitle = title != null && !title.trim().isEmpty() && !title.contains("picture");
        String where = " FROM banner WHERE shop_id = 1";
        Object[] countArgs = filterTitle ? new Object[]{"%" + title.replace("%", "") + "%"} : new Object[]{};
        if (filterTitle) where += " AND title LIKE ?";
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, countArgs, Integer.class);
        String sql = "SELECT id, title AS name, image_url AS value, target_value AS url" + where +
            " ORDER BY sort_order ASC, id ASC LIMIT ? OFFSET ?";
        Object[] queryArgs;
        if (filterTitle) {
            queryArgs = new Object[]{countArgs[0], limit, offset};
        } else {
            queryArgs = new Object[]{limit, offset};
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, queryArgs);
        return new PageUtils(rows, total == null ? 0 : total, limit, current);
    }

    private ConfigEntity bannerConfig(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, title, image_url, target_value FROM banner WHERE id = ? LIMIT 1", id
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        ConfigEntity config = new ConfigEntity();
        config.setId(((Number) row.get("id")).longValue());
        config.setName(stringValue(row.get("title")));
        config.setValue(stringValue(row.get("image_url")));
        config.setUrl(stringValue(row.get("target_value")));
        return config;
    }

    private int intParam(Map<String, Object> params, String name, int fallback) {
        Object value = params.get(name);
        if (value == null) return fallback;
        try {
            return Math.max(1, Integer.parseInt(value.toString()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String targetType(String url) {
        return url == null || url.trim().isEmpty() ? "NONE" : "URL";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
