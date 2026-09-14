package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.PinpaixinxiEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.PinpaixinxiVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.PinpaixinxiView;


/**
 * 品牌信息
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface PinpaixinxiService extends IService<PinpaixinxiEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<PinpaixinxiVO> selectListVO(Wrapper<PinpaixinxiEntity> wrapper);
   	
   	PinpaixinxiVO selectVO(@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);
   	
   	List<PinpaixinxiView> selectListView(Wrapper<PinpaixinxiEntity> wrapper);
   	
   	PinpaixinxiView selectView(@Param("ew") Wrapper<PinpaixinxiEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<PinpaixinxiEntity> wrapper);

   	

}

