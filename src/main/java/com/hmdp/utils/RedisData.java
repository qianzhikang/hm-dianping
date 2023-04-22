package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑过期存储使用
 * @author qianzhikang
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
