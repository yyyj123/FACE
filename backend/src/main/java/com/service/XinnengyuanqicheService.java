package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.XinnengyuanqicheEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.XinnengyuanqicheVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.XinnengyuanqicheView;


/**
 * 新能源汽车
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
public interface XinnengyuanqicheService extends IService<XinnengyuanqicheEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<XinnengyuanqicheVO> selectListVO(Wrapper<XinnengyuanqicheEntity> wrapper);
   	
   	XinnengyuanqicheVO selectVO(@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);
   	
   	List<XinnengyuanqicheView> selectListView(Wrapper<XinnengyuanqicheEntity> wrapper);
   	
   	XinnengyuanqicheView selectView(@Param("ew") Wrapper<XinnengyuanqicheEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<XinnengyuanqicheEntity> wrapper);

   	

    List<Map<String, Object>> selectValue(Map<String, Object> params,Wrapper<XinnengyuanqicheEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params,Wrapper<XinnengyuanqicheEntity> wrapper);

    List<Map<String, Object>> selectGroup(Map<String, Object> params,Wrapper<XinnengyuanqicheEntity> wrapper);



}

