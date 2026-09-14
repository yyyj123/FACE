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

import com.entity.XinnengyuanqicheEntity;
import com.entity.view.XinnengyuanqicheView;

import com.service.XinnengyuanqicheService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;
import com.service.StoreupService;
import com.entity.StoreupEntity;
import com.face.catalog.CarePackageCatalogService;

/**
 * 新能源汽车
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/xinnengyuanqiche")
public class XinnengyuanqicheController {
    @Autowired
    private XinnengyuanqicheService xinnengyuanqicheService;

    @Autowired
    private StoreupService storeupService;

    @Autowired
    private CarePackageCatalogService carePackageCatalogService;

    private EntityWrapper<XinnengyuanqicheEntity> packageScope(EntityWrapper<XinnengyuanqicheEntity> wrapper) {
        List<Long> activeIds = carePackageCatalogService.activeIds();
        if (activeIds.isEmpty()) {
            wrapper.eq("id", -1L);
        } else {
            wrapper.in("id", activeIds);
        }
        return wrapper;
    }

    private boolean isActivePackage(Long id) {
        return id != null && carePackageCatalogService.activeIds().contains(id);
    }


    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,XinnengyuanqicheEntity xinnengyuanqiche,
		HttpServletRequest request){
        EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());



		PageUtils page = xinnengyuanqicheService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, xinnengyuanqiche), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,XinnengyuanqicheEntity xinnengyuanqiche, 
		HttpServletRequest request){
        EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());

		PageUtils page = xinnengyuanqicheService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, xinnengyuanqiche), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( XinnengyuanqicheEntity xinnengyuanqiche){
       	EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
      	ew.allEq(MPUtil.allEQMapPre( xinnengyuanqiche, "xinnengyuanqiche")); 
        return R.ok().put("data", xinnengyuanqicheService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(XinnengyuanqicheEntity xinnengyuanqiche){
        EntityWrapper< XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
 		ew.allEq(MPUtil.allEQMapPre( xinnengyuanqiche, "xinnengyuanqiche")); 
		XinnengyuanqicheView xinnengyuanqicheView =  xinnengyuanqicheService.selectView(ew);
		return R.ok("查询新能源汽车成功").put("data", xinnengyuanqicheView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        if (!isActivePackage(id)) {
            return R.error(404, "护理套餐不存在或已下架");
        }
        XinnengyuanqicheEntity xinnengyuanqiche = xinnengyuanqicheService.selectById(id);
        if (xinnengyuanqiche == null) {
            return R.error(404, "护理套餐不存在");
        }
			xinnengyuanqiche.setClicknum((xinnengyuanqiche.getClicknum() == null ? 0 : xinnengyuanqiche.getClicknum())+1);
		xinnengyuanqiche.setClicktime(new Date());
		xinnengyuanqicheService.updateById(xinnengyuanqiche);
        xinnengyuanqiche = xinnengyuanqicheService.selectView(new EntityWrapper<XinnengyuanqicheEntity>().eq("id", id));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(xinnengyuanqiche,deSens);
        return R.ok().put("data", xinnengyuanqiche);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        if (!isActivePackage(id)) {
            return R.error(404, "护理套餐不存在或已下架");
        }
        XinnengyuanqicheEntity xinnengyuanqiche = xinnengyuanqicheService.selectById(id);
        if (xinnengyuanqiche == null) {
            return R.error(404, "护理套餐不存在");
        }
			xinnengyuanqiche.setClicknum((xinnengyuanqiche.getClicknum() == null ? 0 : xinnengyuanqiche.getClicknum())+1);
		xinnengyuanqiche.setClicktime(new Date());
		xinnengyuanqicheService.updateById(xinnengyuanqiche);
        xinnengyuanqiche = xinnengyuanqicheService.selectView(new EntityWrapper<XinnengyuanqicheEntity>().eq("id", id));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(xinnengyuanqiche,deSens);
        return R.ok().put("data", xinnengyuanqiche);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    @Transactional
    public R save(@RequestBody XinnengyuanqicheEntity xinnengyuanqiche, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(xinnengyuanqiche);
        xinnengyuanqicheService.insert(xinnengyuanqiche);
        carePackageCatalogService.syncLegacyToCatalog(xinnengyuanqiche);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    @Transactional
    public R add(@RequestBody XinnengyuanqicheEntity xinnengyuanqiche, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(xinnengyuanqiche);
        xinnengyuanqicheService.insert(xinnengyuanqiche);
        carePackageCatalogService.syncLegacyToCatalog(xinnengyuanqiche);
        return R.ok().put("data",xinnengyuanqiche.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody XinnengyuanqicheEntity xinnengyuanqiche, HttpServletRequest request){
        //ValidatorUtils.validateEntity(xinnengyuanqiche);
        //全部更新
        xinnengyuanqicheService.updateById(xinnengyuanqiche);
        XinnengyuanqicheEntity saved = xinnengyuanqicheService.selectById(xinnengyuanqiche.getId());
        carePackageCatalogService.syncLegacyToCatalog(saved);

        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    @Transactional
    public R delete(@RequestBody Long[] ids){
        carePackageCatalogService.deactivate(ids);
        xinnengyuanqicheService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	
	/**
     * 前台智能排序
     */
	@IgnoreAuth
    @RequestMapping("/autoSort")
    public R autoSort(@RequestParam Map<String, Object> params,XinnengyuanqicheEntity xinnengyuanqiche, HttpServletRequest request,String pre){
        EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
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
		PageUtils page = xinnengyuanqicheService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, xinnengyuanqiche), params), params));
        return R.ok().put("data", page);
    }








        /**
     * （按值统计）
     */
    @RequestMapping("/value/{xColumnName}/{yColumnName}")
    public R value(@PathVariable("yColumnName") String yColumnName, @PathVariable("xColumnName") String xColumnName,HttpServletRequest request) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("value_xinnengyuanqiche_" + xColumnName + "_" + yColumnName + "_timeType.json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("xColumn", xColumnName);
        params.put("yColumn", yColumnName);
        EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
            List<Map<String, Object>> result = xinnengyuanqicheService.selectValue(params, ew);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for(Map<String, Object> m : result) {
            for(String k : m.keySet()) {
                if(m.get(k) instanceof Date) {
                    m.put(k, sdf.format((Date)m.get(k)));
                }
            }
        }
        Collections.sort(result, (map1, map2) -> {
            // 假设 total 总是存在并且是数值类型
            Number total1 = (Number) map1.get("total");
            Number total2 = (Number) map2.get("total");
            return Double.compare(total2.doubleValue(), total1.doubleValue());
        });
        return R.ok().put("data", result);
        }
    }
    
    /**
     * （按值统计(多)）
     */
    @RequestMapping("/valueMul/{xColumnName}")
    public R valueMul(@PathVariable("xColumnName") String xColumnName,@RequestParam String yColumnNameMul,HttpServletRequest request)  throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("value_xinnengyuanqiche_" + xColumnName + "_" + yColumnNameMul + "_timeType.json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
        String[] yColumnNames = yColumnNameMul.split(",");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("xColumn", xColumnName);
        List<List<Map<String, Object>>> result2 = new ArrayList<List<Map<String,Object>>>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
        for(int i=0;i<yColumnNames.length;i++) {
            params.put("yColumn", yColumnNames[i]);
            List<Map<String, Object>> result = xinnengyuanqicheService.selectValue(params, ew);
            for(Map<String, Object> m : result) {
                for(String k : m.keySet()) {
                    if(m.get(k) instanceof Date) {
                        m.put(k, sdf.format((Date)m.get(k)));
                    }
                }
            }
            result2.add(result);
        }
        return R.ok().put("data", result2);
    }
}
    
    /**
     * （按值统计）时间统计类型
     */
    @RequestMapping("/value/{xColumnName}/{yColumnName}/{timeStatType}")
    public R valueDay(@PathVariable("yColumnName") String yColumnName, @PathVariable("xColumnName") String xColumnName, @PathVariable("timeStatType") String timeStatType,HttpServletRequest request) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("value_xinnengyuanqiche_" + xColumnName + "_" + yColumnName + "_"+timeStatType+".json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xColumn", xColumnName);
            params.put("yColumn", yColumnName);
            params.put("timeStatType", timeStatType);
            EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
                    List<Map<String, Object>> result = xinnengyuanqicheService.selectTimeStatValue(params, ew);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for(Map<String, Object> m : result) {
                for(String k : m.keySet()) {
                    if(m.get(k) instanceof Date) {
                        m.put(k, sdf.format((Date)m.get(k)));
                    }
                }
            }
            return R.ok().put("data", result);
        }
    }
    
        /**
     * （按值统计）时间统计类型(多)
     */
    @RequestMapping("/valueMul/{xColumnName}/{timeStatType}")
    public R valueMulDay(@PathVariable("xColumnName") String xColumnName, @PathVariable("timeStatType") String timeStatType,@RequestParam String yColumnNameMul,HttpServletRequest request) throws IOException
    {
        java.nio.file.Path path = java.nio.file.Paths.get("value_xinnengyuanqiche_" + xColumnName + "_" + yColumnNameMul + "_" + timeStatType + ".json");
        if (java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            String[] yColumnNames = yColumnNameMul.split(",");
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xColumn", xColumnName);
            params.put("timeStatType", timeStatType);
            List<List<Map<String, Object>>> result2 = new ArrayList<List<Map<String,Object>>>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
            for(int i=0;i<yColumnNames.length;i++) {
                params.put("yColumn", yColumnNames[i]);
                List<Map<String, Object>> result = xinnengyuanqicheService.selectTimeStatValue(params, ew);
                for(Map<String, Object> m : result) {
                    for(String k : m.keySet()) {
                        if(m.get(k) instanceof Date) {
                            m.put(k, sdf.format((Date)m.get(k)));
                        }
                    }
                }
                result2.add(result);
            }
            return R.ok().put("data", result2);
        }
    }
    
        /**
     * 分组统计
     */
    @RequestMapping("/group/{columnName}")
    public R group(@PathVariable("columnName") String columnName,HttpServletRequest request) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("group_xinnengyuanqiche_" + columnName + "_timeType.json");
        if(java.nio.file.Files.exists(path)){
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("column", columnName);
        EntityWrapper<XinnengyuanqicheEntity> ew = packageScope(new EntityWrapper<XinnengyuanqicheEntity>());
            List<Map<String, Object>> result = xinnengyuanqicheService.selectGroup(params, ew);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for(Map<String, Object> m : result) {
            for(String k : m.keySet()) {
                if(m.get(k) instanceof Date) {
                    m.put(k, sdf.format((Date)m.get(k)));
                }
            }
        }
        return R.ok().put("data", result);
        }
    }    
    
    







}
