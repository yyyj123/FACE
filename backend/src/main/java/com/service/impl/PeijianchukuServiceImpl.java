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


import com.dao.PeijianchukuDao;
import com.entity.PeijianchukuEntity;
import com.service.PeijianchukuService;
import com.entity.vo.PeijianchukuVO;
import com.entity.view.PeijianchukuView;

@Service("peijianchukuService")
public class PeijianchukuServiceImpl extends ServiceImpl<PeijianchukuDao, PeijianchukuEntity> implements PeijianchukuService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<PeijianchukuEntity> page = this.selectPage(
                new Query<PeijianchukuEntity>(params).getPage(),
                new EntityWrapper<PeijianchukuEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<PeijianchukuEntity> wrapper) {
		  Page<PeijianchukuView> page =new Query<PeijianchukuView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<PeijianchukuVO> selectListVO(Wrapper<PeijianchukuEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public PeijianchukuVO selectVO(Wrapper<PeijianchukuEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<PeijianchukuView> selectListView(Wrapper<PeijianchukuEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public PeijianchukuView selectView(Wrapper<PeijianchukuEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}

    @Override
    public List<Map<String, Object>> selectValue(Map<String, Object> params, Wrapper<PeijianchukuEntity> wrapper) {
        return baseMapper.selectValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params, Wrapper<PeijianchukuEntity> wrapper) {
        return baseMapper.selectTimeStatValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectGroup(Map<String, Object> params, Wrapper<PeijianchukuEntity> wrapper) {
        return baseMapper.selectGroup(params, wrapper);
    }




}
