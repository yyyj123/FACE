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

import com.entity.PingjiafankuiEntity;
import com.entity.view.PingjiafankuiView;

import com.service.PingjiafankuiService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 评价反馈
 * 后端接口
 * @author
 * @email
 * @date 2025-12-22 17:11:22
 */
@RestController
@RequestMapping("/pingjiafankui")
public class PingjiafankuiController {
    @Autowired
    private PingjiafankuiService pingjiafankuiService;

    /**
     * 核心优化：ECharts统计报表 - 获取技师服务质量排行榜数据
     * 为前端的大屏可视化提供数据接口支撑
     */
    @RequestMapping("/getTechnicianPerformanceStats")
    public R getTechnicianPerformanceStats() {
        Map<String, Object> stats = pingjiafankuiService.getTechnicianPerformanceStats();
        return R.ok().put("stats", stats);
    }

    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,PingjiafankuiEntity pingjiafankui,
                  HttpServletRequest request){
        String tableName = request.getSession().getAttribute("tableName").toString();
        if(tableName.equals("chezhu")) {
            pingjiafankui.setZhanghao((String)request.getSession().getAttribute("username"));
        }
        if(tableName.equals("weixiujishi")) {
            pingjiafankui.setWeixiuzhanghao((String)request.getSession().getAttribute("username"));
        }
        EntityWrapper<PingjiafankuiEntity> ew = new EntityWrapper<PingjiafankuiEntity>();



        PageUtils page = pingjiafankuiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, pingjiafankui), params), params));
        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }

    /**
     * 前台列表
     */
    @IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,PingjiafankuiEntity pingjiafankui,
                  HttpServletRequest request){
        EntityWrapper<PingjiafankuiEntity> ew = new EntityWrapper<PingjiafankuiEntity>();

        PageUtils page = pingjiafankuiService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, pingjiafankui), params), params));

        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



    /**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( PingjiafankuiEntity pingjiafankui){
        EntityWrapper<PingjiafankuiEntity> ew = new EntityWrapper<PingjiafankuiEntity>();
        ew.allEq(MPUtil.allEQMapPre( pingjiafankui, "pingjiafankui"));
        return R.ok().put("data", pingjiafankuiService.selectListView(ew));
    }

    /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(PingjiafankuiEntity pingjiafankui){
        EntityWrapper< PingjiafankuiEntity> ew = new EntityWrapper< PingjiafankuiEntity>();
        ew.allEq(MPUtil.allEQMapPre( pingjiafankui, "pingjiafankui"));
        PingjiafankuiView pingjiafankuiView =  pingjiafankuiService.selectView(ew);
        return R.ok("查询评价反馈成功").put("data", pingjiafankuiView);
    }

    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        PingjiafankuiEntity pingjiafankui = pingjiafankuiService.selectById(id);
        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(pingjiafankui,deSens);
        return R.ok().put("data", pingjiafankui);
    }

    /**
     * 前台详情
     */
    @IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        PingjiafankuiEntity pingjiafankui = pingjiafankuiService.selectById(id);
        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(pingjiafankui,deSens);
        return R.ok().put("data", pingjiafankui);
    }




    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody PingjiafankuiEntity pingjiafankui, HttpServletRequest request){
        //ValidatorUtils.validateEntity(pingjiafankui);
        pingjiafankuiService.insert(pingjiafankui);
        return R.ok();
    }

    /**
     * 前台保存
     */
    @IgnoreAuth
    @RequestMapping("/add")
    public R add(@RequestBody PingjiafankuiEntity pingjiafankui, HttpServletRequest request){
        //ValidatorUtils.validateEntity(pingjiafankui);
        pingjiafankuiService.insert(pingjiafankui);
        return R.ok().put("data",pingjiafankui.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody PingjiafankuiEntity pingjiafankui, HttpServletRequest request){
        //ValidatorUtils.validateEntity(pingjiafankui);
        //全部更新
        pingjiafankuiService.updateById(pingjiafankui);

        return R.ok();
    }





    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        pingjiafankuiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }

}