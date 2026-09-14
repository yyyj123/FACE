package com.dao;

import com.entity.GuzhangfenleiEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.GuzhangfenleiVO;
import com.entity.view.GuzhangfenleiView;


/**
 * 故障分类
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface GuzhangfenleiDao extends BaseMapper<GuzhangfenleiEntity> {
	
	List<GuzhangfenleiVO> selectListVO(@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);
	
	GuzhangfenleiVO selectVO(@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);
	
	List<GuzhangfenleiView> selectListView(@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);

	List<GuzhangfenleiView> selectListView(Pagination page,@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);

	
	GuzhangfenleiView selectView(@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);
	

}
