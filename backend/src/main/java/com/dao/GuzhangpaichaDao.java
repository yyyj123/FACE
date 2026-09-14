package com.dao;

import com.entity.GuzhangpaichaEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.GuzhangpaichaVO;
import com.entity.view.GuzhangpaichaView;


/**
 * 故障排查
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface GuzhangpaichaDao extends BaseMapper<GuzhangpaichaEntity> {
	
	List<GuzhangpaichaVO> selectListVO(@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);
	
	GuzhangpaichaVO selectVO(@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);
	
	List<GuzhangpaichaView> selectListView(@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);

	List<GuzhangpaichaView> selectListView(Pagination page,@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);

	
	GuzhangpaichaView selectView(@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);
	

}
