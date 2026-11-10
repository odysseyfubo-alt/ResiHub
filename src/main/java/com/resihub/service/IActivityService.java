package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Activity;

public interface IActivityService extends IService<Activity> {

    Result queryList();

    Result create(Activity activity);

    /** 报名（名額限流 + 生成现场核销码） */
    Result signup(Long id);

    /** 现场核销 */
    Result verify(String code);

    Result myActivities();
}
