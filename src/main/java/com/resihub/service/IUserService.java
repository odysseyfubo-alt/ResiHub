package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.LoginFormDTO;
import com.resihub.dto.Result;
import com.resihub.entity.User;

public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO form);

    Result me();

    /** 提交/修改业主认证信息 */
    Result apply(Long communityId, String building, String room);

    /** 待审核业主列表（管家/管理员） */
    Result pendingAudits();

    /** 审核通过/驳回 */
    Result audit(Long userId, boolean pass);

    /** 校验当前用户是否已通过业主认证（写操作前调用） */
    void assertAudited();
}
