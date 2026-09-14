package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.PeijianchukuEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.PeijianchukuVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.PeijianchukuView;


/**
 * 配件出库
 *
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public interface PeijianchukuService extends IService<PeijianchukuEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
   	List<PeijianchukuVO> selectListVO(Wrapper<PeijianchukuEntity> wrapper);
   	
   	PeijianchukuVO selectVO(@Param("ew") Wrapper<PeijianchukuEntity> wrapper);
   	
   	List<PeijianchukuView> selectListView(Wrapper<PeijianchukuEntity> wrapper);
   	
   	PeijianchukuView selectView(@Param("ew") Wrapper<PeijianchukuEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,Wrapper<PeijianchukuEntity> wrapper);

   	

    List<Map<String, Object>> selectValue(Map<String, Object> params,Wrapper<PeijianchukuEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params,Wrapper<PeijianchukuEntity> wrapper);

    List<Map<String, Object>> selectGroup(Map<String, Object> params,Wrapper<PeijianchukuEntity> wrapper);



}

