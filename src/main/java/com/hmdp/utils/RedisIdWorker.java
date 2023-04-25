package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Description redis唯一id生成器
 * @Date 2023-04-25-15-10
 * @Author qianzhikang
 */
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1672531200L;

    private static final int COUNT_BITS = 32;

    /**
     * 生成id
     * @param keyPrefix 业务前缀
     * @return 唯一业务的id
     */
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 生成自增长key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增长值
        long count = stringRedisTemplate.opsForValue().increment("icr" + ":" +  keyPrefix + ":" + date + ":");
        // 时间戳左移32位
        return timestamp << COUNT_BITS | count;
    }
}
