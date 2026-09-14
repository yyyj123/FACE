package com.dao;

import com.entity.WeixiujiluEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.WeixiujiluVO;
import com.entity.view.WeixiujiluView;


/**
 * 维修记录
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface WeixiujiluDao extends BaseMapper<WeixiujiluEntity> {
	
	List<WeixiujiluVO> selectListVO(@Param("ew") Wrapper<WeixiujiluEntity> wrapper);
	
	WeixiujiluVO selectVO(@Param("ew") Wrapper<WeixiujiluEntity> wrapper);
	
	List<WeixiujiluView> selectListView(@Param("ew") Wrapper<WeixiujiluEntity> wrapper);

	List<WeixiujiluView> selectListView(Pagination page,@Param("ew") Wrapper<WeixiujiluEntity> wrapper);

	
	WeixiujiluView selectView(@Param("ew") Wrapper<WeixiujiluEntity> wrapper);
	

    List<Map<String, Object>> selectValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<WeixiujiluEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<WeixiujiluEntity> wrapper);

    List<Map<String, Object>> selectGroup(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<WeixiujiluEntity> wrapper);



}
