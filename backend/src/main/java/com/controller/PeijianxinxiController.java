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

import com.entity.PeijianxinxiEntity;
import com.entity.view.PeijianxinxiView;

import com.service.PeijianxinxiService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 配件信息
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@RestController
@RequestMapping("/peijianxinxi")
public class PeijianxinxiController {
    @Autowired
    private PeijianxinxiService peijianxinxiService;




    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,PeijianxinxiEntity peijianxinxi,
		HttpServletRequest request){
        EntityWrapper<PeijianxinxiEntity> ew = new EntityWrapper<PeijianxinxiEntity>();



		PageUtils page = peijianxinxiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, peijianxinxi), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,PeijianxinxiEntity peijianxinxi, 
		HttpServletRequest request){
        EntityWrapper<PeijianxinxiEntity> ew = new EntityWrapper<PeijianxinxiEntity>();

		PageUtils page = peijianxinxiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, peijianxinxi), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( PeijianxinxiEntity peijianxinxi){
       	EntityWrapper<PeijianxinxiEntity> ew = new EntityWrapper<PeijianxinxiEntity>();
      	ew.allEq(MPUtil.allEQMapPre( peijianxinxi, "peijianxinxi")); 
        return R.ok().put("data", peijianxinxiService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(PeijianxinxiEntity peijianxinxi){
        EntityWrapper< PeijianxinxiEntity> ew = new EntityWrapper< PeijianxinxiEntity>();
 		ew.allEq(MPUtil.allEQMapPre( peijianxinxi, "peijianxinxi")); 
		PeijianxinxiView peijianxinxiView =  peijianxinxiService.selectView(ew);
		return R.ok("查询配件信息成功").put("data", peijianxinxiView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        PeijianxinxiEntity peijianxinxi = peijianxinxiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(peijianxinxi,deSens);
        return R.ok().put("data", peijianxinxi);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        PeijianxinxiEntity peijianxinxi = peijianxinxiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(peijianxinxi,deSens);
        return R.ok().put("data", peijianxinxi);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody PeijianxinxiEntity peijianxinxi, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(peijianxinxi);
        peijianxinxiService.insert(peijianxinxi);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody PeijianxinxiEntity peijianxinxi, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(peijianxinxi);
        peijianxinxiService.insert(peijianxinxi);
        return R.ok().put("data",peijianxinxi.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody PeijianxinxiEntity peijianxinxi, HttpServletRequest request){
        //ValidatorUtils.validateEntity(peijianxinxi);
        //全部更新
        peijianxinxiService.updateById(peijianxinxi);

        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        peijianxinxiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	











}
