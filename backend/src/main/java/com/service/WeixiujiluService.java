package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.WeixiujiluEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.WeixiujiluVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.WeixiujiluView;

/**
 * 维修记录
 * 优化重点：暴露 ECharts 统计报表接口
 */
public interface WeixiujiluService extends IService<WeixiujiluEntity> {

	PageUtils queryPage(Map<String, Object> params);

	List<WeixiujiluVO> selectListVO(Wrapper<WeixiujiluEntity> wrapper);

	WeixiujiluVO selectVO(@Param("ew") Wrapper<WeixiujiluEntity> wrapper);

	List<WeixiujiluView> selectListView(Wrapper<WeixiujiluEntity> wrapper);

	WeixiujiluView selectView(@Param("ew") Wrapper<WeixiujiluEntity> wrapper);

	PageUtils queryPage(Map<String, Object> params,Wrapper<WeixiujiluEntity> wrapper);

	List<Map<String, Object>> selectValue(Map<String, Object> params,Wrapper<WeixiujiluEntity> wrapper);

	List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params,Wrapper<WeixiujiluEntity> wrapper);

	List<Map<String, Object>> selectGroup(Map<String, Object> params,Wrapper<WeixiujiluEntity> wrapper);

	/**
	 * 新增：定制化报表数据聚合接口，为前端 ECharts 提供月度营收分析
	 * 注意：这里必须接收 params 参数，用于传递前端选定的时间范围
	 */
	Map<String, Object> selectRevenueStats(Map<String, Object> params);

}