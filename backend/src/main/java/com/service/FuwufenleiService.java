package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.FuwufenleiEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.FuwufenleiVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.FuwufenleiView;


/**
 * 服务分类
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface FuwufenleiService extends IService<FuwufenleiEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<FuwufenleiVO> selectListVO(Wrapper<FuwufenleiEntity> wrapper);
   	
   	FuwufenleiVO selectVO(@Param("ew") Wrapper<FuwufenleiEntity> wrapper);
   	
   	List<FuwufenleiView> selectListView(Wrapper<FuwufenleiEntity> wrapper);
   	
   	FuwufenleiView selectView(@Param("ew") Wrapper<FuwufenleiEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<FuwufenleiEntity> wrapper);

   	

}

