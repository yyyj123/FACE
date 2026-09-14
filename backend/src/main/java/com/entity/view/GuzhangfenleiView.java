package com.entity.view;

import com.entity.GuzhangfenleiEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import java.io.Serializable;
import com.utils.EncryptUtil;
 

/**
 * 故障分类
 * 后端返回视图实体辅助类   
 * （通常后端关联的表或者自定义的字段需要返回使用）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@TableName("guzhangfenlei")
public class GuzhangfenleiView  extends GuzhangfenleiEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	public GuzhangfenleiView(){
	}
 
 	public GuzhangfenleiView(GuzhangfenleiEntity guzhangfenleiEntity){
 	try {
			BeanUtils.copyProperties(this, guzhangfenleiEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
 		
	}


}
