package com.resihub.dto;

import lombok.Data;

/**
 * 登录态里的用户信息（存 Redis Hash，也用于前端返回）
 */
@Data
public class UserDTO {
    private Long id;
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
}
