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
@TableName("tb_group_buy")
public class GroupBuy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long communityId;
    private Long leaderUserId;
    private Integer minUsers;
    private Integer currentUsers;
    private String pickupPoint;
    private LocalDateTime deadline;
    /** 0进行中 1已成团 2已流团 3退款完成 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 商品信息（不入库） */
    @TableField(exist = false)
    private Product product;

    /** 团长昵称（不入库） */
    @TableField(exist = false)
    private String leaderName;

    /** 当前用户是否已参团（不入库） */
    @TableField(exist = false)
    private Boolean joined;
}
