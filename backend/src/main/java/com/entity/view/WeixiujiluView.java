package com.entity.view;

import com.entity.WeixiujiluEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import java.io.Serializable;
import com.utils.EncryptUtil;
 

/**
 * 维修记录
 * 后端返回视图实体辅助类   
 * （通常后端关联的表或者自定义的字段需要返回使用）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@TableName("weixiujilu")
public class WeixiujiluView  extends WeixiujiluEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	public WeixiujiluView(){
	}
 
 	public WeixiujiluView(WeixiujiluEntity weixiujiluEntity){
 	try {
			BeanUtils.copyProperties(this, weixiujiluEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
 		
	}


}
