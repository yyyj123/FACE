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

import com.entity.FuwuyuyueEntity;
import com.entity.view.FuwuyuyueView;

import com.service.FuwuyuyueService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 服务预约
 * 后端接口
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/fuwuyuyue")
public class FuwuyuyueController {
    @Autowired
    private FuwuyuyueService fuwuyuyueService;




    



    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,FuwuyuyueEntity fuwuyuyue,
		HttpServletRequest request){
		String tableName = request.getSession().getAttribute("tableName").toString();
		if(tableName.equals("chezhu")) {
			fuwuyuyue.setZhanghao((String)request.getSession().getAttribute("username"));
		}
		if(tableName.equals("weixiujishi")) {
			fuwuyuyue.setWeixiuzhanghao((String)request.getSession().getAttribute("username"));
		}
        EntityWrapper<FuwuyuyueEntity> ew = new EntityWrapper<FuwuyuyueEntity>();



		PageUtils page = fuwuyuyueService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, fuwuyuyue), params), params));
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }
    
    /**
     * 前台列表
     * 该接口用于获取服务预约的前台列表数据，支持分页查询和条件过滤
     * 使用了@IgnoreAuth注解表示不需要身份验证即可访问
     *
     * @param params 查询参数，包含分页、排序等信息
     * @param fuwuyuyue 服务预约实体对象，用于条件过滤
     * @param request HTTP请求对象，可用于获取请求相关信息
     * @return 返回处理结果，包含分页数据
     */
	@IgnoreAuth  // 忽略Token验证
    @RequestMapping("/list")  // 请求映射到/list路径
    public R list(@RequestParam Map<String, Object> params,FuwuyuyueEntity fuwuyuyue, 
		HttpServletRequest request){
        // 创建实体包装器，用于构建查询条件
        EntityWrapper<FuwuyuyueEntity> ew = new EntityWrapper<FuwuyuyueEntity>();

        // 调用服务层方法查询分页数据，支持排序、范围查询和模糊查询
		PageUtils page = fuwuyuyueService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, fuwuyuyue), params), params));
		
        // 创建去敏感化映射，用于处理敏感数据
				Map<String, String> deSens = new HashMap<>();
        // 对分页数据进行去敏感化处理
				DeSensUtil.desensitize(page,deSens);
        // 返回成功结果，并将分页数据放入data字段
        return R.ok().put("data", page);
    }



	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( FuwuyuyueEntity fuwuyuyue){
       	EntityWrapper<FuwuyuyueEntity> ew = new EntityWrapper<FuwuyuyueEntity>();
      	ew.allEq(MPUtil.allEQMapPre( fuwuyuyue, "fuwuyuyue")); 
        return R.ok().put("data", fuwuyuyueService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(FuwuyuyueEntity fuwuyuyue){
        EntityWrapper< FuwuyuyueEntity> ew = new EntityWrapper< FuwuyuyueEntity>();
 		ew.allEq(MPUtil.allEQMapPre( fuwuyuyue, "fuwuyuyue")); 
		FuwuyuyueView fuwuyuyueView =  fuwuyuyueService.selectView(ew);
		return R.ok("查询服务预约成功").put("data", fuwuyuyueView);
    }
	
    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        FuwuyuyueEntity fuwuyuyue = fuwuyuyueService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(fuwuyuyue,deSens);
        return R.ok().put("data", fuwuyuyue);
    }

    /**
     * 前台详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        FuwuyuyueEntity fuwuyuyue = fuwuyuyueService.selectById(id);
				Map<String, String> deSens = new HashMap<>();
				DeSensUtil.desensitize(fuwuyuyue,deSens);
        return R.ok().put("data", fuwuyuyue);
    }
    



    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody FuwuyuyueEntity fuwuyuyue, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(fuwuyuyue);
        fuwuyuyueService.insert(fuwuyuyue);
        return R.ok();
    }
    
    /**
     * 前台保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody FuwuyuyueEntity fuwuyuyue, HttpServletRequest request){
    	//ValidatorUtils.validateEntity(fuwuyuyue);
        fuwuyuyueService.insert(fuwuyuyue);
        return R.ok().put("data",fuwuyuyue.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody FuwuyuyueEntity fuwuyuyue, HttpServletRequest request){
        //ValidatorUtils.validateEntity(fuwuyuyue);
        //全部更新
        fuwuyuyueService.updateById(fuwuyuyue);

        return R.ok();
    }

    /**
     * 审核
     */
    @RequestMapping("/shBatch")
    @Transactional
    public R update(@RequestBody Long[] ids, @RequestParam String sfsh, @RequestParam String shhf){
        List<FuwuyuyueEntity> list = new ArrayList<FuwuyuyueEntity>();
        for(Long id : ids) {
            FuwuyuyueEntity fuwuyuyue = fuwuyuyueService.selectById(id);
            fuwuyuyue.setSfsh(sfsh);
            fuwuyuyue.setShhf(shhf);
            list.add(fuwuyuyue);
        }
        fuwuyuyueService.updateBatchById(list);
        return R.ok();
    }


    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        fuwuyuyueService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
    
	











}
