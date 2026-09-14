package com.dao;

import com.entity.FuwufenleiEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.FuwufenleiVO;
import com.entity.view.FuwufenleiView;


/**
 * 服务分类
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface FuwufenleiDao extends BaseMapper<FuwufenleiEntity> {
	
	List<FuwufenleiVO> selectListVO(@Param("ew") Wrapper<FuwufenleiEntity> wrapper);
	
	FuwufenleiVO selectVO(@Param("ew") Wrapper<FuwufenleiEntity> wrapper);
	
	List<FuwufenleiView> selectListView(@Param("ew") Wrapper<FuwufenleiEntity> wrapper);

	List<FuwufenleiView> selectListView(Pagination page,@Param("ew") Wrapper<FuwufenleiEntity> wrapper);

	
	FuwufenleiView selectView(@Param("ew") Wrapper<FuwufenleiEntity> wrapper);
	

}
