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
 * 配件出库
 * 数据库通用操作实体类（普通增删改查）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
@TableName("peijianchuku")
public class PeijianchukuEntity<T> implements Serializable {
	private static final long serialVersionUID = 1L;


	public PeijianchukuEntity() {
		
	}
	
	public PeijianchukuEntity(T t) {
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
	 * 出库编号
	 */
					
	private String chukubianhao;
	
	/**
	 * 配件名称
	 */
					
	private String peijianmingcheng;
	
	/**
	 * 配件种类
	 */
					
	private String peijianzhonglei;
	
	/**
	 * 售价
	 */
					
	private Double shoujia;
	
	/**
	 * 数量
	 */
					
	private Integer shuliang;
	
	/**
	 * 总价
	 */
					
	private Double zongjia;
	
	/**
	 * 客户
	 */
					
	private String kehu;
	
	/**
	 * 出库时间
	 */
				
	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd")
	@DateTimeFormat 		
	private Date chukushijian;
	
	/**
	 * 备注
	 */
					
	private String beizhu;
	
	/**
	 * 维修账号
	 */
					
	private String weixiuzhanghao;
	
	/**
	 * 维修姓名
	 */
					
	private String weixiuxingming;
	
	
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
	 * 设置：出库编号
	 */
	public void setChukubianhao(String chukubianhao) {
		this.chukubianhao = chukubianhao;
	}
	/**
	 * 获取：出库编号
	 */
	public String getChukubianhao() {
		return chukubianhao;
	}
	/**
	 * 设置：配件名称
	 */
	public void setPeijianmingcheng(String peijianmingcheng) {
		this.peijianmingcheng = peijianmingcheng;
	}
	/**
	 * 获取：配件名称
	 */
	public String getPeijianmingcheng() {
		return peijianmingcheng;
	}
	/**
	 * 设置：配件种类
	 */
	public void setPeijianzhonglei(String peijianzhonglei) {
		this.peijianzhonglei = peijianzhonglei;
	}
	/**
	 * 获取：配件种类
	 */
	public String getPeijianzhonglei() {
		return peijianzhonglei;
	}
	/**
	 * 设置：售价
	 */
	public void setShoujia(Double shoujia) {
		this.shoujia = shoujia;
	}
	/**
	 * 获取：售价
	 */
	public Double getShoujia() {
		return shoujia;
	}
	/**
	 * 设置：数量
	 */
	public void setShuliang(Integer shuliang) {
		this.shuliang = shuliang;
	}
	/**
	 * 获取：数量
	 */
	public Integer getShuliang() {
		return shuliang;
	}
	/**
	 * 设置：总价
	 */
	public void setZongjia(Double zongjia) {
		this.zongjia = zongjia;
	}
	/**
	 * 获取：总价
	 */
	public Double getZongjia() {
		return zongjia;
	}
	/**
	 * 设置：客户
	 */
	public void setKehu(String kehu) {
		this.kehu = kehu;
	}
	/**
	 * 获取：客户
	 */
	public String getKehu() {
		return kehu;
	}
	/**
	 * 设置：出库时间
	 */
	public void setChukushijian(Date chukushijian) {
		this.chukushijian = chukushijian;
	}
	/**
	 * 获取：出库时间
	 */
	public Date getChukushijian() {
		return chukushijian;
	}
	/**
	 * 设置：备注
	 */
	public void setBeizhu(String beizhu) {
		this.beizhu = beizhu;
	}
	/**
	 * 获取：备注
	 */
	public String getBeizhu() {
		return beizhu;
	}
	/**
	 * 设置：维修账号
	 */
	public void setWeixiuzhanghao(String weixiuzhanghao) {
		this.weixiuzhanghao = weixiuzhanghao;
	}
	/**
	 * 获取：维修账号
	 */
	public String getWeixiuzhanghao() {
		return weixiuzhanghao;
	}
	/**
	 * 设置：维修姓名
	 */
	public void setWeixiuxingming(String weixiuxingming) {
		this.weixiuxingming = weixiuxingming;
	}
	/**
	 * 获取：维修姓名
	 */
	public String getWeixiuxingming() {
		return weixiuxingming;
	}

}
