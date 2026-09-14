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
 * 故障排查
 * 数据库通用操作实体类（普通增删改查）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@TableName("guzhangpaicha")
public class GuzhangpaichaEntity<T> implements Serializable {
	private static final long serialVersionUID = 1L;


	public GuzhangpaichaEntity() {
		
	}
	
	public GuzhangpaichaEntity(T t) {
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
	 * 故障名称
	 */
					
	private String guzhangmingcheng;
	
	/**
	 * 故障分类
	 */
					
	private String guzhangfenlei;
	
	/**
	 * 故障原因
	 */
					
	private String guzhangyuanyin;
	
	/**
	 * 封面
	 */
					
	private String fengmian;
	
	/**
	 * 排查部件
	 */
					
	private String paichabujian;
	
	/**
	 * 故障排查
	 */
					
	private String guzhangpaicha;
	
	/**
	 * 发布时间
	 */
				
	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat 		
	private Date fabushijian;
	
	/**
	 * 最近点击时间
	 */
				
	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat 		
	private Date clicktime;
	
	/**
	 * 点击次数
	 */
					
	private Integer clicknum;
	
	/**
	 * 收藏数
	 */
					
	private Integer storeupnum;
	
	
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
	 * 设置：故障名称
	 */
	public void setGuzhangmingcheng(String guzhangmingcheng) {
		this.guzhangmingcheng = guzhangmingcheng;
	}
	/**
	 * 获取：故障名称
	 */
	public String getGuzhangmingcheng() {
		return guzhangmingcheng;
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
	/**
	 * 设置：故障原因
	 */
	public void setGuzhangyuanyin(String guzhangyuanyin) {
		this.guzhangyuanyin = guzhangyuanyin;
	}
	/**
	 * 获取：故障原因
	 */
	public String getGuzhangyuanyin() {
		return guzhangyuanyin;
	}
	/**
	 * 设置：封面
	 */
	public void setFengmian(String fengmian) {
		this.fengmian = fengmian;
	}
	/**
	 * 获取：封面
	 */
	public String getFengmian() {
		return fengmian;
	}
	/**
	 * 设置：排查部件
	 */
	public void setPaichabujian(String paichabujian) {
		this.paichabujian = paichabujian;
	}
	/**
	 * 获取：排查部件
	 */
	public String getPaichabujian() {
		return paichabujian;
	}
	/**
	 * 设置：故障排查
	 */
	public void setGuzhangpaicha(String guzhangpaicha) {
		this.guzhangpaicha = guzhangpaicha;
	}
	/**
	 * 获取：故障排查
	 */
	public String getGuzhangpaicha() {
		return guzhangpaicha;
	}
	/**
	 * 设置：发布时间
	 */
	public void setFabushijian(Date fabushijian) {
		this.fabushijian = fabushijian;
	}
	/**
	 * 获取：发布时间
	 */
	public Date getFabushijian() {
		return fabushijian;
	}
	/**
	 * 设置：最近点击时间
	 */
	public void setClicktime(Date clicktime) {
		this.clicktime = clicktime;
	}
	/**
	 * 获取：最近点击时间
	 */
	public Date getClicktime() {
		return clicktime;
	}
	/**
	 * 设置：点击次数
	 */
	public void setClicknum(Integer clicknum) {
		this.clicknum = clicknum;
	}
	/**
	 * 获取：点击次数
	 */
	public Integer getClicknum() {
		return clicknum;
	}
	/**
	 * 设置：收藏数
	 */
	public void setStoreupnum(Integer storeupnum) {
		this.storeupnum = storeupnum;
	}
	/**
	 * 获取：收藏数
	 */
	public Integer getStoreupnum() {
		return storeupnum;
	}

}
