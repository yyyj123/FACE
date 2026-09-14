package com.service.impl;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.utils.PageUtils;
import com.utils.Query;

import com.dao.PeijianxinxiDao;
import com.entity.PeijianxinxiEntity;
import com.service.PeijianxinxiService;
import com.entity.vo.PeijianxinxiVO;
import com.entity.view.PeijianxinxiView;

/**
 * 配件信息业务实现类优化版
 * 增强点：多维模糊查询、库存预警排序、自动化库存扣减
 */
@Service("peijianxinxiService")
public class PeijianxinxiServiceImpl extends ServiceImpl<PeijianxinxiDao, PeijianxinxiEntity> implements PeijianxinxiService {

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		// 核心修复：移除前端传来的虚拟字段，防止其作为查询条件进入 SQL 导致报错
		params.remove("warningStatus");
		params.remove("colorTag");
		params.remove("warning_status");
		params.remove("color_tag");

		// 1. 提取检索参数：支持按名称和品牌进行多维查询
		String peijianmingcheng = (String) params.get("peijianmingcheng");
		String pinpai = (String) params.get("pinpai");

		EntityWrapper<PeijianxinxiEntity> wrapper = new EntityWrapper<>();

		// 2. 细化模糊查询逻辑：解决查询功能单一、不灵活的问题
		if(peijianmingcheng != null && !"".equals(peijianmingcheng)) {
			wrapper.like("peijianmingcheng", peijianmingcheng);
		}
		if(pinpai != null && !"".equals(pinpai)) {
			wrapper.like("pinpai", pinpai);
		}

		// 3. 排序优化：按库存数量升序排列，实现“库存预警”优先展示
		wrapper.orderBy("shuliang", true);

		Page<PeijianxinxiEntity> page = this.selectPage(
				new Query<PeijianxinxiEntity>(params).getPage(),
				wrapper
		);
		return new PageUtils(page);
	}

	/**
	 * 核心优化方法：自动化库存扣减与预警算法
	 * 此方法应在技师新增维修记录领用配件时调用
	 */
	public boolean reduceStock(Long id, Integer count) {
		PeijianxinxiEntity peijian = this.selectById(id);
		if (peijian != null) {
			int updatedStock = peijian.getShuliang() - count;

			// 鲁棒性检查：防止库存出现负数异常
			if (updatedStock < 0) {
				return false;
			}

			peijian.setShuliang(updatedStock);

			// 执行更新，结合Entity中定义的getWarningStatus实现逻辑预警
			return this.updateById(peijian);
		}
		return false;
	}

	@Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<PeijianxinxiEntity> wrapper) {
		// 同步清理可能透传的虚拟参数
		params.remove("warningStatus");
		params.remove("colorTag");
		params.remove("warning_status");
		params.remove("color_tag");

		Page<PeijianxinxiView> page = new Query<PeijianxinxiView>(params).getPage();
		page.setRecords(baseMapper.selectListView(page, wrapper));
		return new PageUtils(page);
	}

	@Override
	public List<PeijianxinxiVO> selectListVO(Wrapper<PeijianxinxiEntity> wrapper) {
		return baseMapper.selectListVO(wrapper);
	}

	@Override
	public PeijianxinxiVO selectVO(Wrapper<PeijianxinxiEntity> wrapper) {
		return baseMapper.selectVO(wrapper);
	}

	@Override
	public List<PeijianxinxiView> selectListView(Wrapper<PeijianxinxiEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public PeijianxinxiView selectView(Wrapper<PeijianxinxiEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}
}