package com.dao;

import com.entity.XinnengyuanqicheEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.XinnengyuanqicheVO;
import com.entity.view.XinnengyuanqicheView;


/**
 * 新能源汽车
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface XinnengyuanqicheDao extends BaseMapper<XinnengyuanqicheEntity> {
	
	List<XinnengyuanqicheVO> selectListVO(@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);
	
	XinnengyuanqicheVO selectVO(@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);
	
	List<XinnengyuanqicheView> selectListView(@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);

	List<XinnengyuanqicheView> selectListView(Pagination page,@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);

	
	XinnengyuanqicheView selectView(@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);
	

    List<Map<String, Object>> selectValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);

    List<Map<String, Object>> selectGroup(@Param("params") Map<String, Object> params,@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);



}
