package com.controller;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;
import java.util.List;
import java.util.Collections;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import com.utils.ValidatorUtils;
import com.utils.DeSensUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.annotation.IgnoreAuth;

import com.entity.ShouhoufuwuEntity;
import com.entity.view.ShouhoufuwuView;

import com.service.ShouhoufuwuService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;
import com.service.StoreupService;
import com.entity.StoreupEntity;

/**
 * 售后服务
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/shouhoufuwu")
public class ShouhoufuwuController {
    @Autowired
    private ShouhoufuwuService shouhoufuwuService;

    @Autowired
    private StoreupService storeupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The customer site reads the new service catalog while the generated admin
     * screens still use the legacy shouhoufuwu table. Keep the compatibility
     * table refreshed so both clients manage the same projects.
     */
    private void syncCatalogToLegacy() {
        jdbcTemplate.update(
            "INSERT IGNORE INTO fuwufenlei (fuwufenlei) " +
            "SELECT name FROM service_category WHERE shop_id = 1 AND status = 'ACTIVE'"
        );
        jdbcTemplate.update(
            "INSERT INTO shouhoufuwu " +
            "(id, addtime, fuwumingcheng, fuwufenlei, jiage, fengmian, fabushijian, " +
            "fuwuxiangqing, weixiuzhanghao, weixiuxingming, clicktime, clicknum, storeupnum) " +
            "SELECT s.id, s.created_at, s.name, c.name, s.list_price, COALESCE(s.cover_url, ''), " +
            "s.created_at, COALESCE(s.description, ''), '', '', NULL, 0, " +
            "(SELECT COUNT(*) FROM storeup su WHERE su.refid = s.id " +
            "AND su.tablename = 'shouhoufuwu' AND su.type = '1') " +
            "FROM service_item s " +
            "JOIN service_category c ON c.id = s.category_id " +
            "WHERE s.shop_id = 1 AND s.status <> 'INACTIVE' " +
            "ON DUPLICATE KEY UPDATE " +
            "fuwumingcheng = VALUES(fuwumingcheng), " +
            "fuwufenlei = VALUES(fuwufenlei), " +
            "jiage = VALUES(jiage), " +
            "fengmian = VALUES(fengmian), " +
            "fabushijian = VALUES(fabushijian), " +
            "fuwuxiangqing = VALUES(fuwuxiangqing), " +
            "storeupnum = VALUES(storeupnum)"
        );
    }

    private void syncLegacyToCatalog(ShouhoufuwuEntity project) {
        if (project == null || project.getId() == null || StringUtils.isBlank(project.getFuwumingcheng())) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        String categoryName = StringUtils.defaultIfBlank(project.getFuwufenlei(), "未分类");
        jdbcTemplate.update(
            "INSERT INTO service_category (shop_id, name, sort_order, status) VALUES (1, ?, 0, 'ACTIVE') " +
            "ON DUPLICATE KEY UPDATE status = 'ACTIVE'",
            categoryName
        );
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM service_category WHERE shop_id = 1 AND name = ? LIMIT 1",
            new Object[]{categoryName},
            Long.class
        );
        jdbcTemplate.update(
            "INSERT INTO service_item " +
            "(id, shop_id, category_id, service_code, name, subtitle, cover_url, description, " +
            "duration_minutes, cleanup_minutes, list_price, member_price, is_featured, status) " +
            "VALUES (?, 1, ?, ?, ?, NULL, ?, ?, 60, 15, ?, NULL, 0, 'ACTIVE') " +
            "ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), name = VALUES(name), " +
            "cover_url = VALUES(cover_url), description = VALUES(description), " +
            "list_price = VALUES(list_price), status = 'ACTIVE'",
            project.getId(),
            categoryId,
            "ADMIN-" + project.getId(),
            project.getFuwumingcheng(),
            project.getFengmian(),
            project.getFuwuxiangqing(),
            project.getJiage() == null ? 0D : project.getJiage()
        );
    }



    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,ShouhoufuwuEntity shouhoufuwu,
			HttpServletRequest request){
            syncCatalogToLegacy();
			String tableName = request.getSession().getAttribute("tableName").toString();
		if(tableName.equals("weixiujishi")) {
			shouhoufuwu.setWeixiuzhanghao((String)request.getSession().getAttribute("username"));
		}
        EntityWrapper<ShouhoufuwuEntity> ew = new EntityWrapper<ShouhoufuwuEntity>();



		PageUtils page = shouhoufuwuService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, shouhoufuwu), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,ShouhoufuwuEntity shouhoufuwu, 
		HttpServletRequest request){
        EntityWrapper<ShouhoufuwuEntity> ew = new EntityWrapper<ShouhoufuwuEntity>();

		PageUtils page = shouhoufuwuService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, shouhoufuwu), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( ShouhoufuwuEntity shouhoufuwu){
       	EntityWrapper<ShouhoufuwuEntity> ew = new EntityWrapper<ShouhoufuwuEntity>();
      	ew.allEq(MPUtil.allEQMapPre( shouhoufuwu, "shouhoufuwu")); 
        return R.ok().put("data", shouhoufuwuService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(ShouhoufuwuEntity shouhoufuwu){
        EntityWrapper< ShouhoufuwuEntity> ew = new EntityWrapper< ShouhoufuwuEntity>();
 		ew.allEq(MPUtil.allEQMapPre( shouhoufuwu, "shouhoufuwu")); 
		ShouhoufuwuView shouhoufuwuView =  shouhoufuwuService.selectView(ew);
		return R.ok("查询售后服务成功").put("data", shouhoufuwuView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        syncCatalogToLegacy();
        ShouhoufuwuEntity shouhoufuwu = shouhoufuwuService.selectById(id);
		shouhoufuwu.setClicknum(shouhoufuwu.getClicknum()+1);
		shouhoufuwu.setClicktime(new Date());
		shouhoufuwuService.updateById(shouhoufuwu);
        shouhoufuwu = shouhoufuwuService.selectView(new EntityWrapper<ShouhoufuwuEntity>().eq("id", id));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(shouhoufuwu,deSens);
        return R.ok().put("data", shouhoufuwu);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        ShouhoufuwuEntity shouhoufuwu = shouhoufuwuService.selectById(id);
		shouhoufuwu.setClicknum(shouhoufuwu.getClicknum()+1);
		shouhoufuwu.setClicktime(new Date());
		shouhoufuwuService.updateById(shouhoufuwu);
        shouhoufuwu = shouhoufuwuService.selectView(new EntityWrapper<ShouhoufuwuEntity>().eq("id", id));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(shouhoufuwu,deSens);
        return R.ok().put("data", shouhoufuwu);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    @Transactional
    public R save(@RequestBody ShouhoufuwuEntity shouhoufuwu, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(shouhoufuwu);
        shouhoufuwuService.insert(shouhoufuwu);
        syncLegacyToCatalog(shouhoufuwu);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    @Transactional
    public R add(@RequestBody ShouhoufuwuEntity shouhoufuwu, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(shouhoufuwu);
        shouhoufuwuService.insert(shouhoufuwu);
        syncLegacyToCatalog(shouhoufuwu);
        return R.ok().put("data",shouhoufuwu.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody ShouhoufuwuEntity shouhoufuwu, HttpServletRequest request){
        //ValidatorUtils.validateEntity(shouhoufuwu);
        //全部更新
        shouhoufuwuService.updateById(shouhoufuwu);
        syncLegacyToCatalog(shouhoufuwu);

        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    @Transactional
    public R delete(@RequestBody Long[] ids){
        if (ids != null && ids.length > 0) {
            String placeholders = String.join(",", Collections.nCopies(ids.length, "?"));
            jdbcTemplate.update(
                "UPDATE service_item SET status = 'INACTIVE' WHERE id IN (" + placeholders + ")",
                (Object[]) ids
            );
        }
        shouhoufuwuService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	
	/**
     * 前台智能排序
     */
	@IgnoreAuth
    @RequestMapping("/autoSort")
    public R autoSort(@RequestParam Map<String, Object> params,ShouhoufuwuEntity shouhoufuwu, HttpServletRequest request,String pre){
        EntityWrapper<ShouhoufuwuEntity> ew = new EntityWrapper<ShouhoufuwuEntity>();
        Map<String, Object> newMap = new HashMap<String, Object>();
        Map<String, Object> param = new HashMap<String, Object>();
		Iterator<Map.Entry<String, Object>> it = param.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			String key = entry.getKey();
			String newKey = entry.getKey();
			if (pre.endsWith(".")) {
				newMap.put(pre + newKey, entry.getValue());
			} else if (StringUtils.isEmpty(pre)) {
				newMap.put(newKey, entry.getValue());
			} else {
				newMap.put(pre + "." + newKey, entry.getValue());
			}
		}
		params.put("sort", "clicknum");
        params.put("order", "desc");
		PageUtils page = shouhoufuwuService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, shouhoufuwu), params), params));
        return R.ok().put("data", page);
    }











}
