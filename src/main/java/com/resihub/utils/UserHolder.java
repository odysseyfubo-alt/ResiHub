package com.resihub.utils;

import com.resihub.dto.UserDTO;

/**
 * 当前登录用户（线程级）
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        TL.set(user);
    }

    public static UserDTO getUser() {
        return TL.get();
    }

    public static Long getUserId() {
        UserDTO u = TL.get();
        return u == null ? null : u.getId();
    }

    public static Long getCommunityId() {
        UserDTO u = TL.get();
        return u == null ? null : u.getCommunityId();
    }

    public static Integer getRole() {
        UserDTO u = TL.get();
        return u == null ? null : u.getRole();
    }

    public static void removeUser() {
        TL.remove();
    }
}
