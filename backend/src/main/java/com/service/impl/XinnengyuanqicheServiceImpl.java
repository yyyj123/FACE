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


import com.dao.XinnengyuanqicheDao;
import com.entity.XinnengyuanqicheEntity;
import com.service.XinnengyuanqicheService;
import com.entity.vo.XinnengyuanqicheVO;
import com.entity.view.XinnengyuanqicheView;

@Service("xinnengyuanqicheService")
public class XinnengyuanqicheServiceImpl extends ServiceImpl<XinnengyuanqicheDao, XinnengyuanqicheEntity> implements XinnengyuanqicheService {
	
	
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<XinnengyuanqicheEntity> page = this.selectPage(
                new Query<XinnengyuanqicheEntity>(params).getPage(),
                new EntityWrapper<XinnengyuanqicheEntity>()
        );
        return new PageUtils(page);
    }
    
    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<XinnengyuanqicheEntity> wrapper) {
		  Page<XinnengyuanqicheView> page =new Query<XinnengyuanqicheView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    
    @Override
	public List<XinnengyuanqicheVO> selectListVO(Wrapper<XinnengyuanqicheEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}
	
	@Override
	public XinnengyuanqicheVO selectVO(Wrapper<XinnengyuanqicheEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}
	
	@Override
	public List<XinnengyuanqicheView> selectListView(Wrapper<XinnengyuanqicheEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public XinnengyuanqicheView selectView(Wrapper<XinnengyuanqicheEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}

    @Override
    public List<Map<String, Object>> selectValue(Map<String, Object> params, Wrapper<XinnengyuanqicheEntity> wrapper) {
        return baseMapper.selectValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params, Wrapper<XinnengyuanqicheEntity> wrapper) {
        return baseMapper.selectTimeStatValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectGroup(Map<String, Object> params, Wrapper<XinnengyuanqicheEntity> wrapper) {
        return baseMapper.selectGroup(params, wrapper);
    }




}
