package com.service.impl;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.utils.PageUtils;
import com.utils.Query;

import com.dao.PingjiafankuiDao;
import com.entity.PingjiafankuiEntity;
import com.service.PingjiafankuiService;
import com.entity.vo.PingjiafankuiVO;
import com.entity.view.PingjiafankuiView;

@Service("pingjiafankuiService")
public class PingjiafankuiServiceImpl extends ServiceImpl<PingjiafankuiDao, PingjiafankuiEntity> implements PingjiafankuiService {

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		String weixiuxingming = (String) params.get("weixiuxingming");
		String xingming = (String) params.get("xingming");

		EntityWrapper<PingjiafankuiEntity> wrapper = new EntityWrapper<>();

		if (weixiuxingming != null && !"".equals(weixiuxingming)) {
			wrapper.like("weixiuxingming", weixiuxingming);
		}
		if (xingming != null && !"".equals(xingming)) {
			wrapper.like("xingming", xingming);
		}

		wrapper.orderBy("addtime", false);

		Page<PingjiafankuiEntity> page = this.selectPage(
				new Query<PingjiafankuiEntity>(params).getPage(),
				wrapper
		);
		return new PageUtils(page);
	}

	public Map<String, Object> getTechnicianPerformanceStats() {
		EntityWrapper<PingjiafankuiEntity> wrapper = new EntityWrapper<>();

		String sqlSelect = "weixiuxingming as name, " +
				"AVG(CASE " +
				"WHEN manyichengdu = '非常满意' THEN 5.0 " +
				"WHEN manyichengdu = '满意' THEN 4.0 " +
				"WHEN manyichengdu = '一般' THEN 3.0 " +
				"WHEN manyichengdu = '不满意' THEN 2.0 " +
				"ELSE 1.0 END) as averageScore";

		wrapper.setSqlSelect(sqlSelect)
				.groupBy("weixiuxingming")
				.orderBy("averageScore", false);

		List<Map<String, Object>> result = this.selectMaps(wrapper);

		Map<String, Object> stats = new HashMap<>();
		stats.put("xAxis", result.stream().map(m -> m.get("name")).collect(Collectors.toList()));

		stats.put("seriesData", result.stream().map(m -> {
			Object score = m.get("averageScore");
			if (score instanceof java.math.BigDecimal) {
				return ((java.math.BigDecimal) score).setScale(1, java.math.RoundingMode.HALF_UP);
			}
			if (score instanceof Double) {
				return String.format("%.1f", score);
			}
			return score;
		}).collect(Collectors.toList()));

		return stats;
	}

	@Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<PingjiafankuiEntity> wrapper) {
		Page<PingjiafankuiView> page =new Query<PingjiafankuiView>(params).getPage();
		page.setRecords(baseMapper.selectListView(page,wrapper));
		PageUtils pageUtil = new PageUtils(page);
		return pageUtil;
	}

	@Override
	public List<PingjiafankuiVO> selectListVO(Wrapper<PingjiafankuiEntity> wrapper) {
		return baseMapper.selectListVO(wrapper);
	}

	@Override
	public PingjiafankuiVO selectVO(Wrapper<PingjiafankuiEntity> wrapper) {
		return baseMapper.selectVO(wrapper);
	}

	@Override
	public List<PingjiafankuiView> selectListView(Wrapper<PingjiafankuiEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public PingjiafankuiView selectView(Wrapper<PingjiafankuiEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}
}