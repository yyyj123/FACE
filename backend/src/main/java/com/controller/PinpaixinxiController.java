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

import com.entity.PinpaixinxiEntity;
import com.entity.view.PinpaixinxiView;

import com.service.PinpaixinxiService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 品牌信息
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/pinpaixinxi")
public class PinpaixinxiController {
    @Autowired
    private PinpaixinxiService pinpaixinxiService;




    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,PinpaixinxiEntity pinpaixinxi,
		HttpServletRequest request){
        EntityWrapper<PinpaixinxiEntity> ew = new EntityWrapper<PinpaixinxiEntity>();



		PageUtils page = pinpaixinxiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, pinpaixinxi), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,PinpaixinxiEntity pinpaixinxi, 
		HttpServletRequest request){
        EntityWrapper<PinpaixinxiEntity> ew = new EntityWrapper<PinpaixinxiEntity>();

		PageUtils page = pinpaixinxiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, pinpaixinxi), params), params));
		
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( PinpaixinxiEntity pinpaixinxi){
       	EntityWrapper<PinpaixinxiEntity> ew = new EntityWrapper<PinpaixinxiEntity>();
      	ew.allEq(MPUtil.allEQMapPre( pinpaixinxi, "pinpaixinxi")); 
        return R.ok().put("data", pinpaixinxiService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(PinpaixinxiEntity pinpaixinxi){
        EntityWrapper< PinpaixinxiEntity> ew = new EntityWrapper< PinpaixinxiEntity>();
 		ew.allEq(MPUtil.allEQMapPre( pinpaixinxi, "pinpaixinxi")); 
		PinpaixinxiView pinpaixinxiView =  pinpaixinxiService.selectView(ew);
		return R.ok("查询品牌信息成功").put("data", pinpaixinxiView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        PinpaixinxiEntity pinpaixinxi = pinpaixinxiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(pinpaixinxi,deSens);
        return R.ok().put("data", pinpaixinxi);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        PinpaixinxiEntity pinpaixinxi = pinpaixinxiService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(pinpaixinxi,deSens);
        return R.ok().put("data", pinpaixinxi);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody PinpaixinxiEntity pinpaixinxi, HttpServletRequest request){
        if(pinpaixinxiService.selectCount(new EntityWrapper<PinpaixinxiEntity>().eq("pinpai", pinpaixinxi.getPinpai()))>0) {
            return R.error("品牌已存在");
        }
    	//ValidatorUtils.validateEntity(pinpaixinxi);
        pinpaixinxiService.insert(pinpaixinxi);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody PinpaixinxiEntity pinpaixinxi, HttpServletRequest request){
        if(pinpaixinxiService.selectCount(new EntityWrapper<PinpaixinxiEntity>().eq("pinpai", pinpaixinxi.getPinpai()))>0) {
            return R.error("品牌已存在");
        }
    	//ValidatorUtils.validateEntity(pinpaixinxi);
        pinpaixinxiService.insert(pinpaixinxi);
        return R.ok().put("data",pinpaixinxi.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody PinpaixinxiEntity pinpaixinxi, HttpServletRequest request){
        //ValidatorUtils.validateEntity(pinpaixinxi);
        if(pinpaixinxiService.selectCount(new EntityWrapper<PinpaixinxiEntity>().ne("id", pinpaixinxi.getId()).eq("pinpai", pinpaixinxi.getPinpai()))>0) {
            return R.error("品牌已存在");
        }
        //全部更新
        pinpaixinxiService.updateById(pinpaixinxi);

        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        pinpaixinxiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	











}
