package com.entity.view;

import com.entity.WeixiujishiEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import java.io.Serializable;
import com.utils.EncryptUtil;
 

/**
 * 维修技师
 * 后端返回视图实体辅助类   
 * （通常后端关联的表或者自定义的字段需要返回使用）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@TableName("weixiujishi")
public class WeixiujishiView  extends WeixiujishiEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	public WeixiujishiView(){
	}
 
 	public WeixiujishiView(WeixiujishiEntity weixiujishiEntity){
 	try {
			BeanUtils.copyProperties(this, weixiujishiEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
 		
	}


}
