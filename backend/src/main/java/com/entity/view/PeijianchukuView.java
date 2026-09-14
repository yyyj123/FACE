package com.entity.view;

import com.entity.PeijianchukuEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import java.io.Serializable;
import com.utils.EncryptUtil;
 

/**
 * 配件出库
 * 后端返回视图实体辅助类   
 * （通常后端关联的表或者自定义的字段需要返回使用）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@TableName("peijianchuku")
public class PeijianchukuView  extends PeijianchukuEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	public PeijianchukuView(){
	}
 
 	public PeijianchukuView(PeijianchukuEntity peijianchukuEntity){
 	try {
			BeanUtils.copyProperties(this, peijianchukuEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
 		
	}


}
