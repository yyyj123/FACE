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
 * 配件信息
 * 数据库通用操作实体类（普通增删改查）
 */
@TableName("peijianxinxi")
public class PeijianxinxiEntity<T> implements Serializable {
	private static final long serialVersionUID = 1L;

	public PeijianxinxiEntity() {
	}

	public PeijianxinxiEntity(T t) {
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

	private String peijianbianhao;
	private String peijianmingcheng;
	private String peijianzhonglei;
	private String pinpai;
	private Integer shuliang;
	private Double shoujia;
	private String tupian;

	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd")
	@DateTimeFormat
	private Date dengjiriqi;

	private String peijianjieshao;

	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
	private Date addtime;

	public Date getAddtime() { return addtime; }
	public void setAddtime(Date addtime) { this.addtime = addtime; }

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public void setPeijianbianhao(String peijianbianhao) { this.peijianbianhao = peijianbianhao; }
	public String getPeijianbianhao() { return peijianbianhao; }

	public void setPeijianmingcheng(String peijianmingcheng) { this.peijianmingcheng = peijianmingcheng; }
	public String getPeijianmingcheng() { return peijianmingcheng; }

	public void setPeijianzhonglei(String peijianzhonglei) { this.peijianzhonglei = peijianzhonglei; }
	public String getPeijianzhonglei() { return peijianzhonglei; }

	public void setPinpai(String pinpai) { this.pinpai = pinpai; }
	public String getPinpai() { return pinpai; }

	public void setShuliang(Integer shuliang) { this.shuliang = shuliang; }
	public Integer getShuliang() { return shuliang; }

	public void setShoujia(Double shoujia) { this.shoujia = shoujia; }
	public Double getShoujia() { return shoujia; }

	public void setTupian(String tupian) { this.tupian = tupian; }
	public String getTupian() { return tupian; }

	public void setDengjiriqi(Date dengjiriqi) { this.dengjiriqi = dengjiriqi; }
	public Date getDengjiriqi() { return dengjiriqi; }

	public void setPeijianjieshao(String peijianjieshao) { this.peijianjieshao = peijianjieshao; }
	public String getPeijianjieshao() { return peijianjieshao; }
}