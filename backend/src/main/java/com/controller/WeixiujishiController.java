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
import com.entity.TokenEntity;
import com.utils.ValidatorUtils;
import com.utils.DeSensUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

import com.entity.WeixiujishiEntity;
import com.entity.view.WeixiujishiView;

import com.service.WeixiujishiService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 维修技师
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/weixiujishi")
public class WeixiujishiController {
    @Autowired
    private WeixiujishiService weixiujishiService;




    
	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	
	/**
	 * 登录
	 */
	@IgnoreAuth
	@RequestMapping(value = "/login")
	public R login(String username, String password, String captcha, HttpServletRequest request) {
		WeixiujishiEntity u = weixiujishiService.selectOne(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", username));
		if(u==null || !u.getMima().equals(password)) {
			return R.error("账号或密码不正确");
		}
		
		String token = tokenService.generateToken(u.getId(), username,"weixiujishi",  "美容师" );
		return R.ok().put("token", token);
	}


	
	/**
     * 注册
     */
	@IgnoreAuth
    @RequestMapping("/register")
    public R register(@RequestBody WeixiujishiEntity weixiujishi){
    	//ValidatorUtils.validateEntity(weixiujishi);
    	WeixiujishiEntity u = weixiujishiService.selectOne(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", weixiujishi.getWeixiuzhanghao()));
		if(u!=null) {
			return R.error("注册用户已存在");
		}
		Long uId = new Date().getTime();
		weixiujishi.setId(uId);
        weixiujishiService.insert(weixiujishi);
        return R.ok();
    }

	
	/**
	 * 退出
	 */
	@RequestMapping("/logout")
	public R logout(HttpServletRequest request) {
		request.getSession().invalidate();
		return R.ok("退出成功");
	}
	
	/**
     * 获取用户的session用户信息
     */
    @RequestMapping("/session")
    public R getCurrUser(HttpServletRequest request){
    	Long id = (Long)request.getSession().getAttribute("userId");
        WeixiujishiEntity u = weixiujishiService.selectById(id);
        return R.ok().put("data", u);
    }
    
    /**
     * 密码重置
     */
    @IgnoreAuth
	@RequestMapping(value = "/resetPass")
    public R resetPass(String username, HttpServletRequest request){
    	WeixiujishiEntity u = weixiujishiService.selectOne(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", username));
    	if(u==null) {
    		return R.error("账号不存在");
    	}
        u.setMima("123456");
        weixiujishiService.updateById(u);
        return R.ok("密码已重置为：123456");
    }



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,WeixiujishiEntity weixiujishi,
		HttpServletRequest request){
        EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();



		PageUtils page = weixiujishiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, weixiujishi), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,WeixiujishiEntity weixiujishi, 
		HttpServletRequest request){
        EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();

		PageUtils page = weixiujishiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, weixiujishi), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( WeixiujishiEntity weixiujishi){
       	EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
      	ew.allEq(MPUtil.allEQMapPre( weixiujishi, "weixiujishi")); 
        return R.ok().put("data", weixiujishiService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(WeixiujishiEntity weixiujishi){
        EntityWrapper< WeixiujishiEntity> ew = new EntityWrapper< WeixiujishiEntity>();
 		ew.allEq(MPUtil.allEQMapPre( weixiujishi, "weixiujishi")); 
		WeixiujishiView weixiujishiView =  weixiujishiService.selectView(ew);
		return R.ok("查询维修技师成功").put("data", weixiujishiView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        WeixiujishiEntity weixiujishi = weixiujishiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(weixiujishi,deSens);
        return R.ok().put("data", weixiujishi);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        WeixiujishiEntity weixiujishi = weixiujishiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(weixiujishi,deSens);
        return R.ok().put("data", weixiujishi);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    @Transactional
    public R save(@RequestBody WeixiujishiEntity weixiujishi, HttpServletRequest request){
        if(weixiujishiService.selectCount(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", weixiujishi.getWeixiuzhanghao()))>0) {
            return R.error("维修账号已存在");
        }
    	weixiujishi.setId(new Date().getTime()+new Double(Math.floor(Math.random()*1000)).longValue());
    	//ValidatorUtils.validateEntity(weixiujishi);
    	WeixiujishiEntity u = weixiujishiService.selectOne(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", weixiujishi.getWeixiuzhanghao()));
		if(u!=null) {
			return R.error("用户已存在");
		}
		weixiujishi.setId(new Date().getTime());
        weixiujishiService.insert(weixiujishi);
        createFaceStaffAccount(weixiujishi);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    @Transactional
    public R add(@RequestBody WeixiujishiEntity weixiujishi, HttpServletRequest request){
        if(weixiujishiService.selectCount(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", weixiujishi.getWeixiuzhanghao()))>0) {
            return R.error("维修账号已存在");
        }
    	weixiujishi.setId(new Date().getTime()+new Double(Math.floor(Math.random()*1000)).longValue());
    	//ValidatorUtils.validateEntity(weixiujishi);
    	WeixiujishiEntity u = weixiujishiService.selectOne(new EntityWrapper<WeixiujishiEntity>().eq("weixiuzhanghao", weixiujishi.getWeixiuzhanghao()));
		if(u!=null) {
			return R.error("用户已存在");
		}
		weixiujishi.setId(new Date().getTime());
        weixiujishiService.insert(weixiujishi);
        createFaceStaffAccount(weixiujishi);
        return R.ok().put("data",weixiujishi.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody WeixiujishiEntity weixiujishi, HttpServletRequest request){
        //ValidatorUtils.validateEntity(weixiujishi);
        if(weixiujishiService.selectCount(new EntityWrapper<WeixiujishiEntity>().ne("id", weixiujishi.getId()).eq("weixiuzhanghao", weixiujishi.getWeixiuzhanghao()))>0) {
            return R.error("维修账号已存在");
        }
        WeixiujishiEntity previous = weixiujishiService.selectById(weixiujishi.getId());
        //全部更新
        weixiujishiService.updateById(weixiujishi);
        updateFaceStaffAccount(previous, weixiujishi);
    if(null!=weixiujishi.getWeixiuzhanghao())
    {
        // 修改token
        TokenEntity tokenEntity = new TokenEntity();
        tokenEntity.setUsername(weixiujishi.getWeixiuzhanghao());
        tokenService.update(tokenEntity, new EntityWrapper<TokenEntity>().eq("userid", weixiujishi.getId()));
    }


        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    @Transactional
    public R delete(@RequestBody Long[] ids){
        for (Long id : ids) {
            WeixiujishiEntity technician = weixiujishiService.selectById(id);
            if (technician != null) deactivateFaceStaffAccount(technician.getWeixiuzhanghao());
        }
        weixiujishiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }

    private void createFaceStaffAccount(WeixiujishiEntity technician) {
        Map<String, Object> shop = jdbcTemplate.queryForMap(
            "SELECT id, tenant_id FROM shop WHERE status = 'ACTIVE' ORDER BY id LIMIT 1"
        );
        Long shopId = ((Number) shop.get("id")).longValue();
        Long tenantId = ((Number) shop.get("tenant_id")).longValue();
        List<Map<String, Object>> staffRows = jdbcTemplate.queryForList(
            "SELECT id FROM staff WHERE shop_id = ? AND staff_no = ? LIMIT 1",
            shopId, technician.getWeixiuzhanghao()
        );
        Long staffId;
        if (staffRows.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO staff (tenant_id, home_shop_id, shop_id, staff_no, name, phone, job_role, level_name, avatar_url, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'BEAUTICIAN', '美容师', ?, 'ACTIVE')",
                tenantId, shopId, shopId, technician.getWeixiuzhanghao(), technician.getWeixiuxingming(),
                technician.getLianxidianhua(), technician.getTouxiang()
            );
            staffId = jdbcTemplate.queryForObject(
                "SELECT id FROM staff WHERE shop_id = ? AND staff_no = ? LIMIT 1",
                Long.class, shopId, technician.getWeixiuzhanghao()
            );
        } else {
            staffId = ((Number) staffRows.get(0).get("id")).longValue();
            jdbcTemplate.update(
                "UPDATE staff SET name = ?, phone = ?, avatar_url = ?, status = 'ACTIVE' WHERE id = ?",
                technician.getWeixiuxingming(), technician.getLianxidianhua(), technician.getTouxiang(), staffId
            );
        }
        Integer accountCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account WHERE staff_id = ?", Integer.class, staffId
        );
        if (accountCount == null || accountCount == 0) {
            jdbcTemplate.update(
                "INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, staff_id, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'BEAUTICIAN', ?, 'ACTIVE')",
                tenantId, shopId, shopId, technician.getWeixiuzhanghao(), technician.getWeixiuxingming(),
                passwordEncoder.encode(technician.getMima()), staffId
            );
        }
    }

    private void updateFaceStaffAccount(WeixiujishiEntity previous, WeixiujishiEntity technician) {
        if (previous == null) return;
        List<Map<String, Object>> staffRows = jdbcTemplate.queryForList(
            "SELECT id FROM staff WHERE staff_no = ? LIMIT 1",
            previous.getWeixiuzhanghao()
        );
        if (staffRows.isEmpty()) {
            createFaceStaffAccount(technician);
            return;
        }
        Long staffId = ((Number) staffRows.get(0).get("id")).longValue();
        jdbcTemplate.update(
            "UPDATE staff SET staff_no = ?, name = ?, phone = ?, avatar_url = ?, status = 'ACTIVE' WHERE id = ?",
            technician.getWeixiuzhanghao(), technician.getWeixiuxingming(), technician.getLianxidianhua(),
            technician.getTouxiang(), staffId
        );
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
            "SELECT id, role_code FROM account WHERE staff_id = ? LIMIT 1", staffId
        );
        if (accounts.isEmpty()) {
            Map<String, Object> staffScope = jdbcTemplate.queryForMap(
                "SELECT tenant_id, shop_id FROM staff WHERE id = ?", staffId
            );
            Long tenantId = ((Number) staffScope.get("tenant_id")).longValue();
            Long shopId = ((Number) staffScope.get("shop_id")).longValue();
            jdbcTemplate.update(
                "INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, staff_id, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'BEAUTICIAN', ?, 'ACTIVE')",
                tenantId, shopId, shopId, technician.getWeixiuzhanghao(), technician.getWeixiuxingming(),
                passwordEncoder.encode(technician.getMima()), staffId
            );
        } else if ("BEAUTICIAN".equals(accounts.get(0).get("role_code")) && StringUtils.isNotBlank(technician.getMima())) {
            jdbcTemplate.update(
                "UPDATE account SET username = ?, password_hash = ?, status = 'ACTIVE' WHERE id = ?",
                technician.getWeixiuzhanghao(), passwordEncoder.encode(technician.getMima()), accounts.get(0).get("id")
            );
        } else if ("BEAUTICIAN".equals(accounts.get(0).get("role_code"))) {
            jdbcTemplate.update(
                "UPDATE account SET username = ?, status = 'ACTIVE' WHERE id = ?",
                technician.getWeixiuzhanghao(), accounts.get(0).get("id")
            );
        }
    }

    private void deactivateFaceStaffAccount(String username) {
        jdbcTemplate.update("UPDATE staff SET status = 'INACTIVE' WHERE staff_no = ?", username);
        jdbcTemplate.update("UPDATE account SET status = 'INACTIVE' WHERE username = ? AND role_code = 'BEAUTICIAN'", username);
    }
    
	








        /**
     * （按值统计）
     */
    @RequestMapping("/value/{xColumnName}/{yColumnName}")
    public R value(@PathVariable("yColumnName") String yColumnName, @PathVariable("xColumnName") String xColumnName,HttpServletRequest request) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujishi_" + xColumnName + "_" + yColumnName + "_timeType.json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("xColumn", xColumnName);
        params.put("yColumn", yColumnName);
        EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
            List<Map<String, Object>> result = weixiujishiService.selectValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujishi_" + xColumnName + "_" + yColumnNameMul + "_timeType.json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
        String[] yColumnNames = yColumnNameMul.split(",");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("xColumn", xColumnName);
        List<List<Map<String, Object>>> result2 = new ArrayList<List<Map<String,Object>>>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
        for(int i=0;i<yColumnNames.length;i++) {
            params.put("yColumn", yColumnNames[i]);
            List<Map<String, Object>> result = weixiujishiService.selectValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujishi_" + xColumnName + "_" + yColumnName + "_"+timeStatType+".json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xColumn", xColumnName);
            params.put("yColumn", yColumnName);
            params.put("timeStatType", timeStatType);
            EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
                    List<Map<String, Object>> result = weixiujishiService.selectTimeStatValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujishi_" + xColumnName + "_" + yColumnNameMul + "_" + timeStatType + ".json");
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
            EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
            for(int i=0;i<yColumnNames.length;i++) {
                params.put("yColumn", yColumnNames[i]);
                List<Map<String, Object>> result = weixiujishiService.selectTimeStatValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("group_weixiujishi_" + columnName + "_timeType.json");
        if(java.nio.file.Files.exists(path)){
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("column", columnName);
        EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
            List<Map<String, Object>> result = weixiujishiService.selectGroup(params, ew);
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
     * 总数量
     */
    @RequestMapping("/count")
    public R count(@RequestParam Map<String, Object> params,WeixiujishiEntity weixiujishi, HttpServletRequest request){
        EntityWrapper<WeixiujishiEntity> ew = new EntityWrapper<WeixiujishiEntity>();
        int count = weixiujishiService.selectCount(MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, weixiujishi), params), params));
        return R.ok().put("data", count);
    }



}
