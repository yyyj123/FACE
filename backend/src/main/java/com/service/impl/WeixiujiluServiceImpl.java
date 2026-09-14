package com.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.utils.PageUtils;
import com.utils.Query;

import com.dao.WeixiujiluDao;
import com.entity.WeixiujiluEntity;
import com.service.WeixiujiluService;
import com.service.PeijianxinxiService;
import com.entity.vo.WeixiujiluVO;
import com.entity.view.WeixiujiluView;

/**
 * 维修记录业务逻辑实现（已对齐真实实体字段）
 * 核心优化：报表聚合分析与模糊查询强化
 */
@Service("weixiujiluService")
public class WeixiujiluServiceImpl extends ServiceImpl<WeixiujiluDao, WeixiujiluEntity> implements WeixiujiluService {

	@Autowired
	private PeijianxinxiService peijianxinxiService;

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		String xingming = (String) params.get("xingming");
		String chepaihao = (String) params.get("chepaihao");

		EntityWrapper<WeixiujiluEntity> wrapper = new EntityWrapper<>();

		// 1. 动态模糊查询：支持姓名和车牌号
		if(xingming != null && !"".equals(xingming)) {
			wrapper.like("xingming", xingming);
		}
		if(chepaihao != null && !"".equals(chepaihao)) {
			wrapper.like("chepaihao", chepaihao);
		}

		// 2. 列表排序优化
		wrapper.orderBy("addtime", false);

		Page<WeixiujiluEntity> page = this.selectPage(
				new Query<WeixiujiluEntity>(params).getPage(),
				wrapper
		);
		return new PageUtils(page);
	}

	/**
	 * 重写 insert 方法，尝试建立维修记录与配件库存的关联。
	 * 注意：由于原表中没有 peijianId，如果前端传递了扩展参数，可以在此处处理联动。
	 */
	@Override
	public boolean insert(WeixiujiluEntity entity) {
		// 先计算总价：服务价格 + 配件售价
		if (entity.getJiage() != null && entity.getAllshoujia() != null) {
			entity.setZongjia(entity.getJiage() + entity.getAllshoujia());
		}
		return super.insert(entity);
	}

	/**
	 * 核心优化：定制化数据透视与报表生成算法
	 * 能够根据前端传入的时间区间，动态生成 SQL 并进行 SUM 聚合计算
	 */
	@Override
	public Map<String, Object> selectRevenueStats(Map<String, Object> params) {
		String startDate = (String) params.get("startDate");
		String endDate = (String) params.get("endDate");

		EntityWrapper<WeixiujiluEntity> wrapper = new EntityWrapper<>();

		// 1. 数据预清洗：如果前端传了时间范围，则开启时间切片过滤
		if (startDate != null && !"".equals(startDate) && endDate != null && !"".equals(endDate)) {
			wrapper.ge("DATE_FORMAT(addtime, '%Y-%m-%d')", startDate);
			wrapper.le("DATE_FORMAT(addtime, '%Y-%m-%d')", endDate);
		}

		// 2. 动态聚合：按月分组 (GROUP BY) 并计算总价之和 (SUM)
		wrapper.setSqlSelect("DATE_FORMAT(addtime, '%Y-%m') as month, SUM(zongjia) as totalRevenue")
				.groupBy("DATE_FORMAT(addtime, '%Y-%m')")
				.orderBy("month", true);

		// 3. 执行查询
		List<Map<String, Object>> result = this.selectMaps(wrapper);

		// 4. 数据结构重组：转换为 ECharts 可识别的 X轴 和 Y轴 数组格式
		Map<String, Object> stats = new HashMap<>();
		List<String> xAxis = new ArrayList<>();
		List<Double> seriesData = new ArrayList<>();

		if (result != null && !result.isEmpty()) {
			for (Map<String, Object> map : result) {
				xAxis.add((String) map.get("month"));
				Object total = map.get("totalRevenue");
				// 防空指针处理，确保图表渲染不崩溃
				if (total != null) {
					seriesData.add(Double.valueOf(total.toString()));
				} else {
					seriesData.add(0.0);
				}
			}
		}

		stats.put("xAxis", xAxis);
		stats.put("seriesData", seriesData);

		return stats;
	}

	// --- 以下为自动生成的原有方法 ---

	@Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<WeixiujiluEntity> wrapper) {
		Page<WeixiujiluView> page =new Query<WeixiujiluView>(params).getPage();
		page.setRecords(baseMapper.selectListView(page,wrapper));
		return new PageUtils(page);
	}

	@Override
	public List<WeixiujiluVO> selectListVO(Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectListVO(wrapper);
	}

	@Override
	public WeixiujiluVO selectVO(Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectVO(wrapper);
	}

	@Override
	public List<WeixiujiluView> selectListView(Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public WeixiujiluView selectView(Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}

	@Override
	public List<Map<String, Object>> selectValue(Map<String, Object> params, Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectValue(params, wrapper);
	}

	@Override
	public List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params, Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectTimeStatValue(params, wrapper);
	}

	@Override
	public List<Map<String, Object>> selectGroup(Map<String, Object> params, Wrapper<WeixiujiluEntity> wrapper) {
		return baseMapper.selectGroup(params, wrapper);
	}
}