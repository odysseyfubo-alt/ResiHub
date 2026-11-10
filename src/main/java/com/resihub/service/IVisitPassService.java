package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.VisitPass;

public interface IVisitPassService extends IService<VisitPass> {

    /** 业主生成访客通行码 */
    Result create(VisitPass form, Integer hours);

    /** 门禁核销（Lua 保证一次性） */
    Result verify(String code);

    Result myPasses();
}
