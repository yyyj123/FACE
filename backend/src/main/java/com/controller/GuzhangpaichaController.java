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

import com.entity.GuzhangpaichaEntity;
import com.entity.view.GuzhangpaichaView;

import com.service.GuzhangpaichaService;
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
 * 故障排查
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@RestController
@RequestMapping("/guzhangpaicha")
public class GuzhangpaichaController {
    @Autowired
    private GuzhangpaichaService guzhangpaichaService;

    @Autowired
    private StoreupService storeupService;



    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,GuzhangpaichaEntity guzhangpaicha,
		HttpServletRequest request){
        EntityWrapper<GuzhangpaichaEntity> ew = new EntityWrapper<GuzhangpaichaEntity>();



		PageUtils page = guzhangpaichaService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, guzhangpaicha), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,GuzhangpaichaEntity guzhangpaicha, 
		HttpServletRequest request){
        EntityWrapper<GuzhangpaichaEntity> ew = new EntityWrapper<GuzhangpaichaEntity>();

		PageUtils page = guzhangpaichaService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, guzhangpaicha), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( GuzhangpaichaEntity guzhangpaicha){
       	EntityWrapper<GuzhangpaichaEntity> ew = new EntityWrapper<GuzhangpaichaEntity>();
      	ew.allEq(MPUtil.allEQMapPre( guzhangpaicha, "guzhangpaicha")); 
        return R.ok().put("data", guzhangpaichaService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(GuzhangpaichaEntity guzhangpaicha){
        EntityWrapper< GuzhangpaichaEntity> ew = new EntityWrapper< GuzhangpaichaEntity>();
 		ew.allEq(MPUtil.allEQMapPre( guzhangpaicha, "guzhangpaicha")); 
		GuzhangpaichaView guzhangpaichaView =  guzhangpaichaService.selectView(ew);
		return R.ok("查询故障排查成功").put("data", guzhangpaichaView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        GuzhangpaichaEntity guzhangpaicha = guzhangpaichaService.selectById(id);
		guzhangpaicha.setClicknum(guzhangpaicha.getClicknum()+1);
		guzhangpaicha.setClicktime(new Date());
		guzhangpaichaService.updateById(guzhangpaicha);
        guzhangpaicha = guzhangpaichaService.selectView(new EntityWrapper<GuzhangpaichaEntity>().eq("id", id));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(guzhangpaicha,deSens);
        return R.ok().put("data", guzhangpaicha);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        GuzhangpaichaEntity guzhangpaicha = guzhangpaichaService.selectById(id);
		guzhangpaicha.setClicknum(guzhangpaicha.getClicknum()+1);
		guzhangpaicha.setClicktime(new Date());
		guzhangpaichaService.updateById(guzhangpaicha);
        guzhangpaicha = guzhangpaichaService.selectView(new EntityWrapper<GuzhangpaichaEntity>().eq("id", id));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(guzhangpaicha,deSens);
        return R.ok().put("data", guzhangpaicha);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody GuzhangpaichaEntity guzhangpaicha, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(guzhangpaicha);
        guzhangpaichaService.insert(guzhangpaicha);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody GuzhangpaichaEntity guzhangpaicha, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(guzhangpaicha);
        guzhangpaichaService.insert(guzhangpaicha);
        return R.ok().put("data",guzhangpaicha.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody GuzhangpaichaEntity guzhangpaicha, HttpServletRequest request){
        //ValidatorUtils.validateEntity(guzhangpaicha);
        //全部更新
        guzhangpaichaService.updateById(guzhangpaicha);

        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        guzhangpaichaService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	
	/**
     * 前台智能排序
     */
	@IgnoreAuth
    @RequestMapping("/autoSort")
    public R autoSort(@RequestParam Map<String, Object> params,GuzhangpaichaEntity guzhangpaicha, HttpServletRequest request,String pre){
        EntityWrapper<GuzhangpaichaEntity> ew = new EntityWrapper<GuzhangpaichaEntity>();
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
		PageUtils page = guzhangpaichaService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, guzhangpaicha), params), params));
        return R.ok().put("data", page);
    }











}
