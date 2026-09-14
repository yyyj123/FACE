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


import com.dao.WeixiujishiDao;
import com.entity.WeixiujishiEntity;
import com.service.WeixiujishiService;
import com.entity.vo.WeixiujishiVO;
import com.entity.view.WeixiujishiView;

@Service("weixiujishiService")
public class WeixiujishiServiceImpl extends ServiceImpl<WeixiujishiDao, WeixiujishiEntity> implements WeixiujishiService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<WeixiujishiEntity> page = this.selectPage(
                new Query<WeixiujishiEntity>(params).getPage(),
                new EntityWrapper<WeixiujishiEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<WeixiujishiEntity> wrapper) {
		  Page<WeixiujishiView> page =new Query<WeixiujishiView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<WeixiujishiVO> selectListVO(Wrapper<WeixiujishiEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public WeixiujishiVO selectVO(Wrapper<WeixiujishiEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<WeixiujishiView> selectListView(Wrapper<WeixiujishiEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public WeixiujishiView selectView(Wrapper<WeixiujishiEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}

    @Override
    public List<Map<String, Object>> selectValue(Map<String, Object> params, Wrapper<WeixiujishiEntity> wrapper) {
        return baseMapper.selectValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params, Wrapper<WeixiujishiEntity> wrapper) {
        return baseMapper.selectTimeStatValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectGroup(Map<String, Object> params, Wrapper<WeixiujishiEntity> wrapper) {
        return baseMapper.selectGroup(params, wrapper);
    }




}
