package com.dao;

import com.entity.ChezhuEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.ChezhuVO;
import com.entity.view.ChezhuView;


/**
 * 车主
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface ChezhuDao extends BaseMapper<ChezhuEntity> {
	
	List<ChezhuVO> selectListVO(@Param("ew") Wrapper<ChezhuEntity> wrapper);
	
	ChezhuVO selectVO(@Param("ew") Wrapper<ChezhuEntity> wrapper);
	
	List<ChezhuView> selectListView(@Param("ew") Wrapper<ChezhuEntity> wrapper);

	List<ChezhuView> selectListView(Pagination page,@Param("ew") Wrapper<ChezhuEntity> wrapper);

	
	ChezhuView selectView(@Param("ew") Wrapper<ChezhuEntity> wrapper);
	

    List<Map<String, Object>> selectValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<ChezhuEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<ChezhuEntity> wrapper);

    List<Map<String, Object>> selectGroup(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<ChezhuEntity> wrapper);



}
