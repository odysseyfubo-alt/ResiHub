package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Notice;

public interface INoticeService extends IService<Notice> {

    Result list(Integer type);

    Result detail(Long id);

    Result publish(Notice notice);

    Result unreadCount();

    Result readAll();
}
