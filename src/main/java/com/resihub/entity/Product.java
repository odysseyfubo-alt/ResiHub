package com.resihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("tb_product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long communityId;
    private String name;
    private String subTitle;
    private String images;
    /** 团购价（分） */
    private Integer price;
    /** 原价（分） */
    private Integer originPrice;
    private String unit;
    private Integer stock;
    private Integer totalStock;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
