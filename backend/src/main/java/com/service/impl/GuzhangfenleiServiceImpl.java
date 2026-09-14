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


import com.dao.GuzhangfenleiDao;
import com.entity.GuzhangfenleiEntity;
import com.service.GuzhangfenleiService;
import com.entity.vo.GuzhangfenleiVO;
import com.entity.view.GuzhangfenleiView;

@Service("guzhangfenleiService")
public class GuzhangfenleiServiceImpl extends ServiceImpl<GuzhangfenleiDao, GuzhangfenleiEntity> implements GuzhangfenleiService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<GuzhangfenleiEntity> page = this.selectPage(
                new Query<GuzhangfenleiEntity>(params).getPage(),
                new EntityWrapper<GuzhangfenleiEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<GuzhangfenleiEntity> wrapper) {
		  Page<GuzhangfenleiView> page =new Query<GuzhangfenleiView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<GuzhangfenleiVO> selectListVO(Wrapper<GuzhangfenleiEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public GuzhangfenleiVO selectVO(Wrapper<GuzhangfenleiEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<GuzhangfenleiView> selectListView(Wrapper<GuzhangfenleiEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public GuzhangfenleiView selectView(Wrapper<GuzhangfenleiEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}


}
