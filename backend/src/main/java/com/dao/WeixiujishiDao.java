package com.dao;

import com.entity.WeixiujishiEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.WeixiujishiVO;
import com.entity.view.WeixiujishiView;


/**
 * 维修技师
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface WeixiujishiDao extends BaseMapper<WeixiujishiEntity> {
	
	List<WeixiujishiVO> selectListVO(@Param("ew") Wrapper<WeixiujishiEntity> wrapper);
	
	WeixiujishiVO selectVO(@Param("ew") Wrapper<WeixiujishiEntity> wrapper);
	
	List<WeixiujishiView> selectListView(@Param("ew") Wrapper<WeixiujishiEntity> wrapper);

	List<WeixiujishiView> selectListView(Pagination page,@Param("ew") Wrapper<WeixiujishiEntity> wrapper);

	
	WeixiujishiView selectView(@Param("ew") Wrapper<WeixiujishiEntity> wrapper);
	

    List<Map<String, Object>> selectValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<WeixiujishiEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<WeixiujishiEntity> wrapper);

    List<Map<String, Object>> selectGroup(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<WeixiujishiEntity> wrapper);



}
