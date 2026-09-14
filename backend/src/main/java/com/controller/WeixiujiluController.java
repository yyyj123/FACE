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

import com.entity.WeixiujiluEntity;
import com.entity.view.WeixiujiluView;

import com.service.WeixiujiluService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MPUtil;
import com.utils.MapUtils;
import com.utils.CommonUtil;
import java.io.IOException;

/**
 * 维修记录
 * 后端接口
 * @author
 * @email
 * @date 2025-12-22 17:11:21
 */
@RestController
@RequestMapping("/weixiujilu")
public class WeixiujiluController {
    @Autowired
    private WeixiujiluService weixiujiluService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 核心优化：ECharts统计报表 - 获取月度营收趋势数据
     * 为前端的大屏可视化提供数据接口支撑
     * 【修复点】：增加了 @RequestParam 接收前端的日期参数
     */
    @RequestMapping("/selectRevenueStats")
    public R selectRevenueStats(@RequestParam Map<String, Object> params) {
        Map<String, Object> stats = weixiujiluService.selectRevenueStats(params);
        return R.ok().put("stats", stats);
    }

    /**
     * 后台列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,WeixiujiluEntity weixiujilu,
                  HttpServletRequest request){
        int currentPage = Math.max(1, parsePositiveInt(params.get("page"), 1));
        int pageSize = Math.min(200, Math.max(1, parsePositiveInt(params.get("limit"), 10)));
        int offset = (currentPage - 1) * pageSize;
        StringBuilder where = new StringBuilder(" WHERE sr.shop_id = ?");
        List<Object> args = new ArrayList<Object>();
        args.add(1L);
        appendLike(where, args, "ai.service_names", params.get("fuwumingcheng"));
        appendLike(where, args, "m.name", params.get("xingming"));
        Object note = params.get("chepaihao");
        if (note != null && !note.toString().trim().isEmpty()) {
            where.append(" AND (COALESCE(a.member_note, '') LIKE ? OR COALESCE(sr.service_summary, '') LIKE ?)");
            args.add(note.toString());
            args.add(note.toString());
        }
        Object pay = params.get("ispay");
        if (pay != null && !pay.toString().trim().isEmpty()) {
            where.append(" AND COALESCE(so.is_paid, 0) = ?");
            args.add("已支付".equals(pay.toString()) ? 1 : 0);
        }

        String joins = " FROM service_record sr " +
            "JOIN member m ON m.id = sr.member_id " +
            "JOIN staff st ON st.id = sr.staff_id " +
            "LEFT JOIN appointment a ON a.id = sr.appointment_id " +
            "LEFT JOIN (SELECT appointment_id, GROUP_CONCAT(service_name_snapshot ORDER BY sort_order SEPARATOR '、') service_names, " +
            "SUM(price_snapshot) service_price FROM appointment_item GROUP BY appointment_id) ai ON ai.appointment_id = a.id " +
            "LEFT JOIN (SELECT service_record_id, MAX(status = 'PAID') is_paid, MAX(paid_amount) paid_amount " +
            "FROM sales_order GROUP BY service_record_id) so ON so.service_record_id = sr.id";

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + joins + where, Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<Object>(args);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "SELECT sr.id, COALESCE(a.appointment_no, CONCAT('SR', sr.id)) weixiubianhao, " +
                "COALESCE(ai.service_names, sr.service_summary, '到店护理') fuwumingcheng, " +
                "'护理服务' fuwufenlei, NULL fengmian, COALESCE(ai.service_price, 0) jiage, " +
                "NULL peijianmingcheng, 0 allshoujia, COALESCE(so.paid_amount, ai.service_price, 0) zongjia, " +
                "sr.actual_start_at weixiushijian, m.member_no zhanghao, m.name xingming, m.phone shouji, " +
                "COALESCE(a.member_note, sr.service_summary, '') chepaihao, st.staff_no weixiuzhanghao, " +
                "st.name weixiuxingming, IF(COALESCE(so.is_paid, 0) = 1, '已支付', '未支付') ispay" +
                joins + where + " ORDER BY sr.actual_start_at DESC LIMIT ? OFFSET ?",
            listArgs.toArray()
        );
        return R.ok().put("data", new PageUtils(records, total == null ? 0 : total.intValue(), pageSize, currentPage));
    }

    private int parsePositiveInt(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private void appendLike(StringBuilder where, List<Object> args, String column, Object value) {
        if (value == null || value.toString().trim().isEmpty()) return;
        where.append(" AND ").append(column).append(" LIKE ?");
        args.add(value.toString());
    }

    /**
     * 前台列表
     */
    @IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,WeixiujiluEntity weixiujilu,
                  HttpServletRequest request){
        EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();

        PageUtils page = weixiujiluService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, weixiujilu), params), params));

        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(page,deSens);
        return R.ok().put("data", page);
    }



    /**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( WeixiujiluEntity weixiujilu){
        EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
        ew.allEq(MPUtil.allEQMapPre( weixiujilu, "weixiujilu"));
        return R.ok().put("data", weixiujiluService.selectListView(ew));
    }

    /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(WeixiujiluEntity weixiujilu){
        EntityWrapper< WeixiujiluEntity> ew = new EntityWrapper< WeixiujiluEntity>();
        ew.allEq(MPUtil.allEQMapPre( weixiujilu, "weixiujilu"));
        WeixiujiluView weixiujiluView =  weixiujiluService.selectView(ew);
        return R.ok("查询维修记录成功").put("data", weixiujiluView);
    }

    /**
     * 后台详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        WeixiujiluEntity weixiujilu = weixiujiluService.selectById(id);
        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(weixiujilu,deSens);
        return R.ok().put("data", weixiujilu);
    }

    /**
     * 前台详情
     */
    @IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        WeixiujiluEntity weixiujilu = weixiujiluService.selectById(id);
        Map<String, String> deSens = new HashMap<>();
        DeSensUtil.desensitize(weixiujilu,deSens);
        return R.ok().put("data", weixiujilu);
    }




    /**
     * 后台保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody WeixiujiluEntity weixiujilu, HttpServletRequest request){
        //ValidatorUtils.validateEntity(weixiujilu);
        weixiujiluService.insert(weixiujilu);
        return R.ok();
    }

    /**
     * 前台保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody WeixiujiluEntity weixiujilu, HttpServletRequest request){
        //ValidatorUtils.validateEntity(weixiujilu);
        weixiujiluService.insert(weixiujilu);
        return R.ok().put("data",weixiujilu.getId());
    }





    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody WeixiujiluEntity weixiujilu, HttpServletRequest request){
        //ValidatorUtils.validateEntity(weixiujilu);
        //全部更新
        weixiujiluService.updateById(weixiujilu);

        return R.ok();
    }





    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        weixiujiluService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }










    /**
     * （按值统计）
     */
    @RequestMapping("/value/{xColumnName}/{yColumnName}")
    public R value(@PathVariable("yColumnName") String yColumnName, @PathVariable("xColumnName") String xColumnName,HttpServletRequest request) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujilu_" + xColumnName + "_" + yColumnName + "_timeType.json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xColumn", xColumnName);
            params.put("yColumn", yColumnName);
            EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
            String tableName = request.getSession().getAttribute("tableName").toString();
            if(tableName.equals("chezhu")) {
                ew.eq("zhanghao", (String)request.getSession().getAttribute("username"));
            }
            if(tableName.equals("weixiujishi")) {
                ew.eq("weixiuzhanghao", (String)request.getSession().getAttribute("username"));
            }
            List<Map<String, Object>> result = weixiujiluService.selectValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujilu_" + xColumnName + "_" + yColumnNameMul + "_timeType.json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            String[] yColumnNames = yColumnNameMul.split(",");
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xColumn", xColumnName);
            List<List<Map<String, Object>>> result2 = new ArrayList<List<Map<String,Object>>>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
            String tableName = request.getSession().getAttribute("tableName").toString();
            if(tableName.equals("chezhu")) {
                ew.eq("zhanghao", (String)request.getSession().getAttribute("username"));
            }
            if(tableName.equals("weixiujishi")) {
                ew.eq("weixiuzhanghao", (String)request.getSession().getAttribute("username"));
            }
            for(int i=0;i<yColumnNames.length;i++) {
                params.put("yColumn", yColumnNames[i]);
                List<Map<String, Object>> result = weixiujiluService.selectValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujilu_" + xColumnName + "_" + yColumnName + "_"+timeStatType+".json");
        if(java.nio.file.Files.exists(path)) {
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xColumn", xColumnName);
            params.put("yColumn", yColumnName);
            params.put("timeStatType", timeStatType);
            EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
            String tableName = request.getSession().getAttribute("tableName").toString();
            if(tableName.equals("chezhu")) {
                ew.eq("zhanghao", (String)request.getSession().getAttribute("username"));
            }
            if(tableName.equals("weixiujishi")) {
                ew.eq("weixiuzhanghao", (String)request.getSession().getAttribute("username"));
            }
            List<Map<String, Object>> result = weixiujiluService.selectTimeStatValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("value_weixiujilu_" + xColumnName + "_" + yColumnNameMul + "_" + timeStatType + ".json");
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
            EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
            String tableName = request.getSession().getAttribute("tableName").toString();
            if(tableName.equals("chezhu")) {
                ew.eq("zhanghao", (String)request.getSession().getAttribute("username"));
            }
            if(tableName.equals("weixiujishi")) {
                ew.eq("weixiuzhanghao", (String)request.getSession().getAttribute("username"));
            }
            for(int i=0;i<yColumnNames.length;i++) {
                params.put("yColumn", yColumnNames[i]);
                List<Map<String, Object>> result = weixiujiluService.selectTimeStatValue(params, ew);
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
        java.nio.file.Path path = java.nio.file.Paths.get("group_weixiujilu_" + columnName + "_timeType.json");
        if(java.nio.file.Files.exists(path)){
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            return R.ok().put("data", (new org.json.JSONArray(content)).toList());
        }else{
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("column", columnName);
            EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
            String tableName = request.getSession().getAttribute("tableName").toString();
            if(tableName.equals("chezhu")) {
                ew.eq("zhanghao", (String)request.getSession().getAttribute("username"));
            }
            if(tableName.equals("weixiujishi")) {
                ew.eq("weixiuzhanghao", (String)request.getSession().getAttribute("username"));
            }
            List<Map<String, Object>> result = weixiujiluService.selectGroup(params, ew);
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
    public R count(@RequestParam Map<String, Object> params,WeixiujiluEntity weixiujilu, HttpServletRequest request){
        String tableName = request.getSession().getAttribute("tableName").toString();
        if(tableName.equals("chezhu")) {
            weixiujilu.setZhanghao((String)request.getSession().getAttribute("username"));
        }
        if(tableName.equals("weixiujishi")) {
            weixiujilu.setWeixiuzhanghao((String)request.getSession().getAttribute("username"));
        }
        EntityWrapper<WeixiujiluEntity> ew = new EntityWrapper<WeixiujiluEntity>();
        int count = weixiujiluService.selectCount(MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, weixiujilu), params), params));
        return R.ok().put("data", count);
    }
}
