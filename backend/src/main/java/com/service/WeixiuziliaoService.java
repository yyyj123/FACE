package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.WeixiuziliaoEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.WeixiuziliaoVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.WeixiuziliaoView;


/**
 * 维修资料
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface WeixiuziliaoService extends IService<WeixiuziliaoEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<WeixiuziliaoVO> selectListVO(Wrapper<WeixiuziliaoEntity> wrapper);
   	
   	WeixiuziliaoVO selectVO(@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);
   	
   	List<WeixiuziliaoView> selectListView(Wrapper<WeixiuziliaoEntity> wrapper);
   	
   	WeixiuziliaoView selectView(@Param("ew") Wrapper<WeixiuziliaoEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<WeixiuziliaoEntity> wrapper);

   	

}

