package com.dao;

import com.entity.PinpaixinxiEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.PinpaixinxiVO;
import com.entity.view.PinpaixinxiView;


/**
 * 品牌信息
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface PinpaixinxiDao extends BaseMapper<PinpaixinxiEntity> {
	
	List<PinpaixinxiVO> selectListVO(@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);
	
	PinpaixinxiVO selectVO(@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);
	
	List<PinpaixinxiView> selectListView(@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);

	List<PinpaixinxiView> selectListView(Pagination page,@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);

	
	PinpaixinxiView selectView(@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);
	

}
