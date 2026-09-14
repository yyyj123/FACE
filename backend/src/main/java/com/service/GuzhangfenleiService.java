package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.GuzhangfenleiEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.GuzhangfenleiVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.GuzhangfenleiView;


/**
 * 故障分类
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface GuzhangfenleiService extends IService<GuzhangfenleiEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<GuzhangfenleiVO> selectListVO(Wrapper<GuzhangfenleiEntity> wrapper);
   	
   	GuzhangfenleiVO selectVO(@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);
   	
   	List<GuzhangfenleiView> selectListView(Wrapper<GuzhangfenleiEntity> wrapper);
   	
   	GuzhangfenleiView selectView(@Param("ew") Wrapper<GuzhangfenleiEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<GuzhangfenleiEntity> wrapper);

   	

}

