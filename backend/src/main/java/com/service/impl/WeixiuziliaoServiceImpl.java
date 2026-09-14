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


import com.dao.WeixiuziliaoDao;
import com.entity.WeixiuziliaoEntity;
import com.service.WeixiuziliaoService;
import com.entity.vo.WeixiuziliaoVO;
import com.entity.view.WeixiuziliaoView;

@Service("weixiuziliaoService")
public class WeixiuziliaoServiceImpl extends ServiceImpl<WeixiuziliaoDao, WeixiuziliaoEntity> implements WeixiuziliaoService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<WeixiuziliaoEntity> page = this.selectPage(
                new Query<WeixiuziliaoEntity>(params).getPage(),
                new EntityWrapper<WeixiuziliaoEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<WeixiuziliaoEntity> wrapper) {
		  Page<WeixiuziliaoView> page =new Query<WeixiuziliaoView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<WeixiuziliaoVO> selectListVO(Wrapper<WeixiuziliaoEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public WeixiuziliaoVO selectVO(Wrapper<WeixiuziliaoEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<WeixiuziliaoView> selectListView(Wrapper<WeixiuziliaoEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public WeixiuziliaoView selectView(Wrapper<WeixiuziliaoEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}


}
