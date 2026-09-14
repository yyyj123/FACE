package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.GuzhangpaichaEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.GuzhangpaichaVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.GuzhangpaichaView;


/**
 * 故障排查
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface GuzhangpaichaService extends IService<GuzhangpaichaEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<GuzhangpaichaVO> selectListVO(Wrapper<GuzhangpaichaEntity> wrapper);
   	
   	GuzhangpaichaVO selectVO(@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);
   	
   	List<GuzhangpaichaView> selectListView(Wrapper<GuzhangpaichaEntity> wrapper);
   	
   	GuzhangpaichaView selectView(@Param("ew") Wrapper<GuzhangpaichaEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<GuzhangpaichaEntity> wrapper);

   	

}

