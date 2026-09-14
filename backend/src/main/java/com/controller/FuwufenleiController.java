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

import com.entity.FuwufenleiEntity;
import com.entity.view.FuwufenleiView;

import com.service.FuwufenleiService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 服务分类
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/fuwufenlei")
public class FuwufenleiController {
    @Autowired
    private FuwufenleiService fuwufenleiService;




    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,FuwufenleiEntity fuwufenlei,
		HttpServletRequest request){
        EntityWrapper<FuwufenleiEntity> ew = new EntityWrapper<FuwufenleiEntity>();



		PageUtils page = fuwufenleiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, fuwufenlei), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,FuwufenleiEntity fuwufenlei, 
		HttpServletRequest request){
        EntityWrapper<FuwufenleiEntity> ew = new EntityWrapper<FuwufenleiEntity>();

		PageUtils page = fuwufenleiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, fuwufenlei), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( FuwufenleiEntity fuwufenlei){
       	EntityWrapper<FuwufenleiEntity> ew = new EntityWrapper<FuwufenleiEntity>();
      	ew.allEq(MPUtil.allEQMapPre( fuwufenlei, "fuwufenlei")); 
        return R.ok().put("data", fuwufenleiService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(FuwufenleiEntity fuwufenlei){
        EntityWrapper< FuwufenleiEntity> ew = new EntityWrapper< FuwufenleiEntity>();
 		ew.allEq(MPUtil.allEQMapPre( fuwufenlei, "fuwufenlei")); 
		FuwufenleiView fuwufenleiView =  fuwufenleiService.selectView(ew);
		return R.ok("查询服务分类成功").put("data", fuwufenleiView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        FuwufenleiEntity fuwufenlei = fuwufenleiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(fuwufenlei,deSens);
        return R.ok().put("data", fuwufenlei);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        FuwufenleiEntity fuwufenlei = fuwufenleiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(fuwufenlei,deSens);
        return R.ok().put("data", fuwufenlei);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody FuwufenleiEntity fuwufenlei, HttpServletRequest request){
        if(fuwufenleiService.selectCount(new EntityWrapper<FuwufenleiEntity>().eq("fuwufenlei", fuwufenlei.getFuwufenlei()))>0) {
            return R.error("服务分类已存在");
        }
    	//ValidatorUtils.validateEntity(fuwufenlei);
        fuwufenleiService.insert(fuwufenlei);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody FuwufenleiEntity fuwufenlei, HttpServletRequest request){
        if(fuwufenleiService.selectCount(new EntityWrapper<FuwufenleiEntity>().eq("fuwufenlei", fuwufenlei.getFuwufenlei()))>0) {
            return R.error("服务分类已存在");
        }
    	//ValidatorUtils.validateEntity(fuwufenlei);
        fuwufenleiService.insert(fuwufenlei);
        return R.ok().put("data",fuwufenlei.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody FuwufenleiEntity fuwufenlei, HttpServletRequest request){
        //ValidatorUtils.validateEntity(fuwufenlei);
        if(fuwufenleiService.selectCount(new EntityWrapper<FuwufenleiEntity>().ne("id", fuwufenlei.getId()).eq("fuwufenlei", fuwufenlei.getFuwufenlei()))>0) {
            return R.error("服务分类已存在");
        }
        //全部更新
        fuwufenleiService.updateById(fuwufenlei);

        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        fuwufenleiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	











}
