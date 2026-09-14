package com.entity.view;

import com.entity.GuzhangpaichaEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import com.baomidou.mybatisplus.annotations.TableField;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.io.Serializable;

/**
 * 故障排查视图实体
 * 扩展了算法评分字段以支持定制化排序 [cite: 303]
 */
@TableName("guzhangpaicha")
public class GuzhangpaichaView extends GuzhangpaichaEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 算法得分：用于内存中的权重排序，不对应数据库字段 [cite: 304]
	 */
	@TableField(exist = false)
	private Double score;

	public GuzhangpaichaView() {
	}

	public GuzhangpaichaView(GuzhangpaichaEntity guzhangpaichaEntity) {
		try {
			BeanUtils.copyProperties(this, guzhangpaichaEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	// --- Getter 和 Setter 方法 ---

	public Double getScore() {
		return score;
	}

	public void setScore(Double score) {
		this.score = score;
	}
}