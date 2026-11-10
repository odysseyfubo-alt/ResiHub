package com.resihub.service;

import com.resihub.dto.Result;

public interface IPointsService {

    /** 增加积分并落流水 */
    void addPoints(Long userId, int points, String type, String remark);

    /** 每日签到（Bitmap），返回本次获得积分 */
    Result sign();

    /** 本月连续签到天数 */
    Result signCount();

    /** 我的积分流水 */
    Result log();

    /** 我的积分总额 */
    Result total();
}
