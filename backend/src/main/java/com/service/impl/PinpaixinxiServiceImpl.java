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


import com.dao.PinpaixinxiDao;
import com.entity.PinpaixinxiEntity;
import com.service.PinpaixinxiService;
import com.entity.vo.PinpaixinxiVO;
import com.entity.view.PinpaixinxiView;

@Service("pinpaixinxiService")
public class PinpaixinxiServiceImpl extends ServiceImpl<PinpaixinxiDao, PinpaixinxiEntity> implements PinpaixinxiService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<PinpaixinxiEntity> page = this.selectPage(
                new Query<PinpaixinxiEntity>(params).getPage(),
                new EntityWrapper<PinpaixinxiEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<PinpaixinxiEntity> wrapper) {
		  Page<PinpaixinxiView> page =new Query<PinpaixinxiView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<PinpaixinxiVO> selectListVO(Wrapper<PinpaixinxiEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public PinpaixinxiVO selectVO(Wrapper<PinpaixinxiEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<PinpaixinxiView> selectListView(Wrapper<PinpaixinxiEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public PinpaixinxiView selectView(Wrapper<PinpaixinxiEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}


}
