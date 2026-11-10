package com.resihub.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 手机号等校验
 */
public class RegexUtils {

    public static boolean isPhoneInvalid(String phone) {
        return mismatch(phone, "^(1[3-9]\\d{9})$");
    }

    private static boolean mismatch(String str, String regex) {
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
