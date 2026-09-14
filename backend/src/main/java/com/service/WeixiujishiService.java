package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.WeixiujishiEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.WeixiujishiVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.WeixiujishiView;


/**
 * 维修技师
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface WeixiujishiService extends IService<WeixiujishiEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<WeixiujishiVO> selectListVO(Wrapper<WeixiujishiEntity> wrapper);
   	
   	WeixiujishiVO selectVO(@Param("ew") Wrapper<WeixiujishiEntity> wrapper);
   	
   	List<WeixiujishiView> selectListView(Wrapper<WeixiujishiEntity> wrapper);
   	
   	WeixiujishiView selectView(@Param("ew") Wrapper<WeixiujishiEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<WeixiujishiEntity> wrapper);

   	

    List<Map<String, Object>> selectValue(Map<String, Object> params,Wrapper<WeixiujishiEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params,Wrapper<WeixiujishiEntity> wrapper);

    List<Map<String, Object>> selectGroup(Map<String, Object> params,Wrapper<WeixiujishiEntity> wrapper);



}

