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
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String phone;
    private String nickName;
    private String icon;
    private Long communityId;
    private String building;
    private String room;
    /** 0居民 1维修工 2管家 3管理员 */
    private Integer role;
    /** 0待审核 1已通过 2已驳回 */
    private Integer audit;
    private Integer points;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
