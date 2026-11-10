package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Community;

public interface ICommunityService extends IService<Community> {

    Result listAll();

    Result detail(Long id);
}
