package com.entity;

import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.lang.reflect.InvocationTargetException;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.beanutils.BeanUtils;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.enums.FieldFill;
import com.baomidou.mybatisplus.enums.IdType;


/**
 * 故障分类
 * 数据库通用操作实体类（普通增删改查）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@TableName("guzhangfenlei")
public class GuzhangfenleiEntity<T> implements Serializable {
	private static final long serialVersionUID = 1L;


	public GuzhangfenleiEntity() {
		
	}
	
	public GuzhangfenleiEntity(T t) {
		try {
			BeanUtils.copyProperties(this, t);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 主键id
	 */
    @TableId(type = IdType.AUTO)
	private Long id;
	/**
	 * 故障分类
	 */
					
	private String guzhangfenlei;
	
	
	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
	private Date addtime;

	public Date getAddtime() {
		return addtime;
	}
	public void setAddtime(Date addtime) {
		this.addtime = addtime;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}
	/**
	 * 设置：故障分类
	 */
	public void setGuzhangfenlei(String guzhangfenlei) {
		this.guzhangfenlei = guzhangfenlei;
	}
	/**
	 * 获取：故障分类
	 */
	public String getGuzhangfenlei() {
		return guzhangfenlei;
	}

}
