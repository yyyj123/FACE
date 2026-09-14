package com.service.impl;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;
import java.util.Date;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.utils.PageUtils;
import com.utils.Query;

import com.dao.FuwuyuyueDao;
import com.entity.FuwuyuyueEntity;
import com.service.FuwuyuyueService;
import com.entity.vo.FuwuyuyueVO;
import com.entity.view.FuwuyuyueView;

/**
 * 服务预约业务逻辑实现
 * 核心优化：时间切片防冲突校验算法、业务状态机初始化
 */
@Service("fuwuyuyueService")
public class FuwuyuyueServiceImpl extends ServiceImpl<FuwuyuyueDao, FuwuyuyueEntity> implements FuwuyuyueService {

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		String fuwumingcheng = (String) params.get("fuwumingcheng");
		String xingming = (String) params.get("xingming");

		EntityWrapper<FuwuyuyueEntity> wrapper = new EntityWrapper<>();

		// 增强模糊查询，方便管理员快速定位预约记录
		if (fuwumingcheng != null && !"".equals(fuwumingcheng)) {
			wrapper.like("fuwumingcheng", fuwumingcheng);
		}
		if (xingming != null && !"".equals(xingming)) {
			wrapper.like("xingming", xingming);
		}

		// 按预约时间倒序排列
		wrapper.orderBy("addtime", false);

		Page<FuwuyuyueEntity> page = this.selectPage(
				new Query<FuwuyuyueEntity>(params).getPage(),
				wrapper
		);
		return new PageUtils(page);
	}

	/**
	 * 重写新增方法，加入“时间冲突防重发”逻辑
	 */
	@Override
	public boolean insert(FuwuyuyueEntity entity) {
		// 1. 防冲突检测：检查技师在预约时间段是否空闲
		if (entity.getWeixiuzhanghao() != null && entity.getYuyueshijian() != null) {

			// 设定冲突时间阈值：前后 2 小时视为同一个时间段
			long time = entity.getYuyueshijian().getTime();
			Date startTime = new Date(time - 2 * 3600 * 1000);
			Date endTime = new Date(time + 2 * 3600 * 1000);

			EntityWrapper<FuwuyuyueEntity> wrapper = new EntityWrapper<>();
			wrapper.eq("weixiuzhanghao", entity.getWeixiuzhanghao())
					.between("yuyueshijian", startTime, endTime)
					.ne("sfsh", "否"); // 排除掉已经被拒绝的预约

			// 如果查询到记录，说明该时间段技师已被占用
			int conflictCount = this.selectCount(wrapper);
			if (conflictCount > 0) {
				// 抛出异常，前端捕获后可弹窗提示车主
				throw new RuntimeException("当前技师在该时间段已被预约，请更换时间或选择其他技师。");
			}
		}

		// 2. 状态机规范化：所有新提交的预约，强制设为“待审核”
		if (entity.getSfsh() == null || "".equals(entity.getSfsh())) {
			entity.setSfsh("待审核");
		}

		return super.insert(entity);
	}

	// --- 以下为自动生成的原有方法 ---

	@Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<FuwuyuyueEntity> wrapper) {
		Page<FuwuyuyueView> page =new Query<FuwuyuyueView>(params).getPage();
		page.setRecords(baseMapper.selectListView(page,wrapper));
		return new PageUtils(page);
	}

	@Override
	public List<FuwuyuyueVO> selectListVO(Wrapper<FuwuyuyueEntity> wrapper) {
		return baseMapper.selectListVO(wrapper);
	}

	@Override
	public FuwuyuyueVO selectVO(Wrapper<FuwuyuyueEntity> wrapper) {
		return baseMapper.selectVO(wrapper);
	}

	@Override
	public List<FuwuyuyueView> selectListView(Wrapper<FuwuyuyueEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public FuwuyuyueView selectView(Wrapper<FuwuyuyueEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}
}