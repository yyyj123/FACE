package com.dao;

import com.entity.WeixiuziliaoEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.WeixiuziliaoVO;
import com.entity.view.WeixiuziliaoView;


/**
 * 维修资料
 * 
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface WeixiuziliaoDao extends BaseMapper<WeixiuziliaoEntity> {
	
	List<WeixiuziliaoVO> selectListVO(@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);
	
	WeixiuziliaoVO selectVO(@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);
	
	List<WeixiuziliaoView> selectListView(@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);

	List<WeixiuziliaoView> selectListView(Pagination page,@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);

	
	WeixiuziliaoView selectView(@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);
	

}
