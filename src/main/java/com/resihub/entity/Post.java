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
@TableName("tb_post")
public class Post implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long communityId;
    private Long userId;
    /** 闲置 / 求助 / 分享 / 拼车 */
    private String category;
    private String title;
    private String content;
    private String images;
    private Integer liked;
    private Integer comments;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String userIcon;
    /** 当前用户是否已点赞 */
    @TableField(exist = false)
    private Boolean isLike;
}
