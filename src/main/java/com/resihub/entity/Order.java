package com.resihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_order")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private Long groupBuyId;
    private Integer quantity;
    /** 总价（分） */
    private Integer amount;
    /** 0待成团 1待自提 2已完成 3已退款 4已取消 */
    private Integer status;
    private String pickupCode;
    private LocalDateTime verifyTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 商品名（不入库） */
    @TableField(exist = false)
    private String productName;
    /** 自提点（不入库） */
    @TableField(exist = false)
    private String pickupPoint;
    /** 团状态（不入库） */
    @TableField(exist = false)
    private Integer groupStatus;
}
