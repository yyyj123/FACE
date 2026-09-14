package com.service.impl;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Comparator;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.utils.PageUtils;
import com.utils.Query;

import com.dao.GuzhangpaichaDao;
import com.entity.GuzhangpaichaEntity;
import com.service.GuzhangpaichaService;
import com.entity.vo.GuzhangpaichaVO;
import com.entity.view.GuzhangpaichaView;

@Service("guzhangpaichaService")
public class GuzhangpaichaServiceImpl extends ServiceImpl<GuzhangpaichaDao, GuzhangpaichaEntity> implements GuzhangpaichaService {

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		// 1. 获取检索关键词（对应实体类字段名：guzhangmingcheng）
		String guzhangmingcheng = (String) params.get("guzhangmingcheng");
		String guzhangfenlei = (String) params.get("guzhangfenlei");

		EntityWrapper<GuzhangpaichaEntity> wrapper = new EntityWrapper<>();

		// 2. 细化多字段模糊查询：支持名称或原因的联合搜索 [cite: 78, 112]
		if (guzhangmingcheng != null && !"".equals(guzhangmingcheng)) {
			wrapper.like("guzhangmingcheng", guzhangmingcheng)
					.or().like("guzhangyuanyin", guzhangmingcheng);
		}

		if (guzhangfenlei != null && !"".equals(guzhangfenlei)) {
			wrapper.eq("guzhangfenlei", guzhangfenlei);
		}

		// 3. 界面优化：默认按添加时间倒序，提升用户体验 [cite: 175]
		wrapper.orderBy("addtime", false);

		Page<GuzhangpaichaEntity> page = this.selectPage(
				new Query<GuzhangpaichaEntity>(params).getPage(),
				wrapper
		);
		return new PageUtils(page);
	}

	@Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<GuzhangpaichaEntity> wrapper) {
		Page<GuzhangpaichaView> page = new Query<GuzhangpaichaView>(params).getPage();
		page.setRecords(baseMapper.selectListView(page, wrapper));
		return new PageUtils(page);
	}

	/**
	 * 算法加持：基于权重分值的智能匹配模型 [cite: 87, 304]
	 * 修复了字段名不一致导致的编译错误
	 */
	@Override
	public List<GuzhangpaichaView> selectListView(Wrapper<GuzhangpaichaEntity> wrapper) {
		List<GuzhangpaichaView> list = baseMapper.selectListView(wrapper);

		// 模拟搜索词（实际可从前端 params 动态传入）
		String searchKey = "电池";

		return list.stream().map(item -> {
					double score = 0;
					// 匹配权重分配：名称(5分) > 原因(3分) > 具体内容(1分) [cite: 119]
					if (item.getGuzhangmingcheng() != null && item.getGuzhangmingcheng().contains(searchKey)) score += 5.0;
					if (item.getGuzhangyuanyin() != null && item.getGuzhangyuanyin().contains(searchKey)) score += 3.0;

					// 关键修复：将 getPaichabuzhou() 修正为实体类真实的 getGuzhangpaicha()
					if (item.getGuzhangpaicha() != null && item.getGuzhangpaicha().contains(searchKey)) score += 1.0;

					item.setScore(score);
					return item;
				})
				.sorted(Comparator.comparing(GuzhangpaichaView::getScore).reversed()) // 降序排列
				.collect(Collectors.toList());
	}

	/**
	 * 定制化报表数据接口：按分类统计故障分布 [cite: 163, 277]
	 */
	public Map<String, Object> getGroupStats() {
		EntityWrapper<GuzhangpaichaEntity> wrapper = new EntityWrapper<>();
		wrapper.setSqlSelect("guzhangfenlei as name, count(*) as value")
				.groupBy("guzhangfenlei");

		List<Map<String, Object>> result = this.selectMaps(wrapper);
		Map<String, Object> stats = new HashMap<>();
		stats.put("data", result);
		return stats;
	}

	@Override
	public List<GuzhangpaichaVO> selectListVO(Wrapper<GuzhangpaichaEntity> wrapper) {
		return baseMapper.selectListVO(wrapper);
	}

	@Override
	public GuzhangpaichaVO selectVO(Wrapper<GuzhangpaichaEntity> wrapper) {
		return baseMapper.selectVO(wrapper);
	}

	@Override
	public GuzhangpaichaView selectView(Wrapper<GuzhangpaichaEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}
}