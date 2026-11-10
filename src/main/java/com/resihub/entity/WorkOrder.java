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
@TableName("tb_work_order")
public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long communityId;
    /** 报修人 */
    private Long userId;
    private String type;
    private String description;
    private String images;
    /** 0待接单 1处理中 2待居民确认 3已完成 4已超时升级 5已取消 */
    private Integer status;
    private Long assigneeId;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
    private LocalDateTime timeoutAt;
    private Integer rating;
    private String comment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String assigneeName;
}
