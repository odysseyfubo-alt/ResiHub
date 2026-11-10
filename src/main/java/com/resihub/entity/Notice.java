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
@TableName("tb_notice")
public class Notice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long communityId;
    private String title;
    private String content;
    /** 0公告 1停水停电 2活动 3紧急 */
    private Integer type;
    private Integer top;
    /** 定向楼栋，空=全小区 */
    private String buildingTarget;
    private Long publisherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前登录用户是否已读（不入库） */
    @TableField(exist = false)
    private Boolean readFlag;

    /** 发布人昵称（不入库） */
    @TableField(exist = false)
    private String publisherName;
}
