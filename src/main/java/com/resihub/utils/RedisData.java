package com.resihub.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 带逻辑过期时间的缓存包装
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
