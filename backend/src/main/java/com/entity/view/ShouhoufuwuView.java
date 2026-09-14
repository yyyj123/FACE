package com.entity.view;

import com.entity.ShouhoufuwuEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import java.io.Serializable;
import com.utils.EncryptUtil;
 

/**
 * 售后服务
 * 后端返回视图实体辅助类   
 * （通常后端关联的表或者自定义的字段需要返回使用）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@TableName("shouhoufuwu")
public class ShouhoufuwuView  extends ShouhoufuwuEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	public ShouhoufuwuView(){
	}
 
 	public ShouhoufuwuView(ShouhoufuwuEntity shouhoufuwuEntity){
 	try {
			BeanUtils.copyProperties(this, shouhoufuwuEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
 		
	}


}
