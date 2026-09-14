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


import com.dao.FuwufenleiDao;
import com.entity.FuwufenleiEntity;
import com.service.FuwufenleiService;
import com.entity.vo.FuwufenleiVO;
import com.entity.view.FuwufenleiView;

@Service("fuwufenleiService")
public class FuwufenleiServiceImpl extends ServiceImpl<FuwufenleiDao, FuwufenleiEntity> implements FuwufenleiService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<FuwufenleiEntity> page = this.selectPage(
                new Query<FuwufenleiEntity>(params).getPage(),
                new EntityWrapper<FuwufenleiEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<FuwufenleiEntity> wrapper) {
		  Page<FuwufenleiView> page =new Query<FuwufenleiView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<FuwufenleiVO> selectListVO(Wrapper<FuwufenleiEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public FuwufenleiVO selectVO(Wrapper<FuwufenleiEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<FuwufenleiView> selectListView(Wrapper<FuwufenleiEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public FuwufenleiView selectView(Wrapper<FuwufenleiEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}


}
