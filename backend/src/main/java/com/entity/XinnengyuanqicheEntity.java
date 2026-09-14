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
 * 新能源汽车
 * 数据库通用操作实体类（普通增删改查）
 * @author 
 * @email 
 * @date 2025-12-22 17:11:21
 */
@TableName("xinnengyuanqiche")
public class XinnengyuanqicheEntity<T> implements Serializable {
	private static final long serialVersionUID = 1L;


	public XinnengyuanqicheEntity() {
		
	}
	
	public XinnengyuanqicheEntity(T t) {
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
	 * 汽车型号
	 */
					
	private String qichexinghao;
	
	/**
	 * 汽车类型
	 */
					
	private String qicheleixing;
	
	/**
	 * 品牌
	 */
					
	private String pinpai;
	
	/**
	 * 百公里加速
	 */
					
	private String baigonglijiasu;
	
	/**
	 * 最高时速
	 */
					
	private String zuigaoshisu;
	
	/**
	 * 续航公里数
	 */
					
	private String xuhanggonglishu;
	
	/**
	 * 效率
	 */
					
	private String xiaolv;
	
	/**
	 * 价格
	 */
					
	private Double jiage;
	
	/**
	 * 座位数
	 */
					
	private Integer zuoweishu;
	
	/**
	 * 动力总成
	 */
					
	private String donglizongcheng;
	
	/**
	 * 充电插头
	 */
					
	private String chongdianchatou;
	
	/**
	 * 封面
	 */
					
	private String fengmian;
	
	/**
	 * 外形式样
	 */
					
	private String waixingshiyang;
	
	/**
	 * 充电方案
	 */
					
	private String chongdianfangan;
	
	/**
	 * 技术规格
	 */
					
	private String jishuguige;
	
	/**
	 * 详细介绍
	 */
					
	private String xiangxijieshao;
	
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
	 * 设置：汽车型号
	 */
	public void setQichexinghao(String qichexinghao) {
		this.qichexinghao = qichexinghao;
	}
	/**
	 * 获取：汽车型号
	 */
	public String getQichexinghao() {
		return qichexinghao;
	}
	/**
	 * 设置：汽车类型
	 */
	public void setQicheleixing(String qicheleixing) {
		this.qicheleixing = qicheleixing;
	}
	/**
	 * 获取：汽车类型
	 */
	public String getQicheleixing() {
		return qicheleixing;
	}
	/**
	 * 设置：品牌
	 */
	public void setPinpai(String pinpai) {
		this.pinpai = pinpai;
	}
	/**
	 * 获取：品牌
	 */
	public String getPinpai() {
		return pinpai;
	}
	/**
	 * 设置：百公里加速
	 */
	public void setBaigonglijiasu(String baigonglijiasu) {
		this.baigonglijiasu = baigonglijiasu;
	}
	/**
	 * 获取：百公里加速
	 */
	public String getBaigonglijiasu() {
		return baigonglijiasu;
	}
	/**
	 * 设置：最高时速
	 */
	public void setZuigaoshisu(String zuigaoshisu) {
		this.zuigaoshisu = zuigaoshisu;
	}
	/**
	 * 获取：最高时速
	 */
	public String getZuigaoshisu() {
		return zuigaoshisu;
	}
	/**
	 * 设置：续航公里数
	 */
	public void setXuhanggonglishu(String xuhanggonglishu) {
		this.xuhanggonglishu = xuhanggonglishu;
	}
	/**
	 * 获取：续航公里数
	 */
	public String getXuhanggonglishu() {
		return xuhanggonglishu;
	}
	/**
	 * 设置：效率
	 */
	public void setXiaolv(String xiaolv) {
		this.xiaolv = xiaolv;
	}
	/**
	 * 获取：效率
	 */
	public String getXiaolv() {
		return xiaolv;
	}
	/**
	 * 设置：价格
	 */
	public void setJiage(Double jiage) {
		this.jiage = jiage;
	}
	/**
	 * 获取：价格
	 */
	public Double getJiage() {
		return jiage;
	}
	/**
	 * 设置：座位数
	 */
	public void setZuoweishu(Integer zuoweishu) {
		this.zuoweishu = zuoweishu;
	}
	/**
	 * 获取：座位数
	 */
	public Integer getZuoweishu() {
		return zuoweishu;
	}
	/**
	 * 设置：动力总成
	 */
	public void setDonglizongcheng(String donglizongcheng) {
		this.donglizongcheng = donglizongcheng;
	}
	/**
	 * 获取：动力总成
	 */
	public String getDonglizongcheng() {
		return donglizongcheng;
	}
	/**
	 * 设置：充电插头
	 */
	public void setChongdianchatou(String chongdianchatou) {
		this.chongdianchatou = chongdianchatou;
	}
	/**
	 * 获取：充电插头
	 */
	public String getChongdianchatou() {
		return chongdianchatou;
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
	 * 设置：外形式样
	 */
	public void setWaixingshiyang(String waixingshiyang) {
		this.waixingshiyang = waixingshiyang;
	}
	/**
	 * 获取：外形式样
	 */
	public String getWaixingshiyang() {
		return waixingshiyang;
	}
	/**
	 * 设置：充电方案
	 */
	public void setChongdianfangan(String chongdianfangan) {
		this.chongdianfangan = chongdianfangan;
	}
	/**
	 * 获取：充电方案
	 */
	public String getChongdianfangan() {
		return chongdianfangan;
	}
	/**
	 * 设置：技术规格
	 */
	public void setJishuguige(String jishuguige) {
		this.jishuguige = jishuguige;
	}
	/**
	 * 获取：技术规格
	 */
	public String getJishuguige() {
		return jishuguige;
	}
	/**
	 * 设置：详细介绍
	 */
	public void setXiangxijieshao(String xiangxijieshao) {
		this.xiangxijieshao = xiangxijieshao;
	}
	/**
	 * 获取：详细介绍
	 */
	public String getXiangxijieshao() {
		return xiangxijieshao;
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
