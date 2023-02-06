package com.qzk.dp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 秒杀优惠券表，与优惠券是一对一关系
 * @TableName tb_seckill_voucher
 */
@TableName(value ="tb_seckill_voucher")
@Data
public class SeckillVoucher implements Serializable {
    /**
     * 关联的优惠券的id
     */
    @TableId
    private Long voucherId;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 生效时间
     */
    private Date beginTime;

    /**
     * 失效时间
     */
    private Date endTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}