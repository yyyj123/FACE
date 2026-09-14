package com.dao;

import com.entity.PeijianchukuEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.PeijianchukuVO;
import com.entity.view.PeijianchukuView;


/**
 * 配件出库
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface PeijianchukuDao extends BaseMapper<PeijianchukuEntity> {
	
	List<PeijianchukuVO> selectListVO(@Param("ew") Wrapper<PeijianchukuEntity> wrapper);
	
	PeijianchukuVO selectVO(@Param("ew") Wrapper<PeijianchukuEntity> wrapper);
	
	List<PeijianchukuView> selectListView(@Param("ew") Wrapper<PeijianchukuEntity> wrapper);

	List<PeijianchukuView> selectListView(Pagination page,@Param("ew") Wrapper<PeijianchukuEntity> wrapper);

	
	PeijianchukuView selectView(@Param("ew") Wrapper<PeijianchukuEntity> wrapper);
	

    List<Map<String, Object>> selectValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<PeijianchukuEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<PeijianchukuEntity> wrapper);

    List<Map<String, Object>> selectGroup(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<PeijianchukuEntity> wrapper);



}
