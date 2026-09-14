package com.entity.vo;

import com.entity.WeixiuziliaoEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
 

/**
 * 维修资料
 * @author 
 * @email 
 * @date 2025-12-22 17:11:22
 */
public class WeixiuziliaoVO  implements Serializable {
	private static final long serialVersionUID = 1L;

	 			
	/**
	 * 资料文件
	 */
	
	private String ziliaowenjian;
		
	/**
	 * 封面
	 */
	
	private String fengmian;
		
	/**
	 * 上传时间
	 */
		
	@JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat 
	private Date shangchuanshijian;
		
	/**
	 * 资料内容
	 */
	
	private String ziliaoneirong;
				
	
	/**
	 * 设置：资料文件
	 */
	 
	public void setZiliaowenjian(String ziliaowenjian) {
		this.ziliaowenjian = ziliaowenjian;
	}
	
	/**
	 * 获取：资料文件
	 */
	public String getZiliaowenjian() {
		return ziliaowenjian;
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
	 * 设置：上传时间
	 */
	 
	public void setShangchuanshijian(Date shangchuanshijian) {
		this.shangchuanshijian = shangchuanshijian;
	}
	
	/**
	 * 获取：上传时间
	 */
	public Date getShangchuanshijian() {
		return shangchuanshijian;
	}
				
	
	/**
	 * 设置：资料内容
	 */
	 
	public void setZiliaoneirong(String ziliaoneirong) {
		this.ziliaoneirong = ziliaoneirong;
	}
	
	/**
	 * 获取：资料内容
	 */
	public String getZiliaoneirong() {
		return ziliaoneirong;
	}
			
}
