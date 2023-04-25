package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @Description 缓存工具类
 * @Date 2023-04-24-15-41
 * @Author qianzhikang
 */
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 存入Redis并设置过期时间
     *
     * @param key      key
     * @param object   存储对象
     * @param time     过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object object, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object), time, timeUnit);
    }


    /**
     * 存入Redis并设置逻辑过期时间
     *
     * @param key      key
     * @param object   存储对象
     * @param time     过期时间
     * @param timeUnit 时间单位
     */
    public void setWithLogicExpire(String key, Object object, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 带缓存穿透保护的查询缓存
     *
     * @param keyPrefix key前缀
     * @param id        id
     * @param type      返回值类型
     * @param function  数据库查询逻辑
     * @param time      查询数据库后，存入缓存的过期时间
     * @param timeUnit  时间单位
     * @param <R>       返回值类型
     * @param <ID>      查询参数
     * @return 返回指定type类型结果
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id,
                                          Class<R> type,
                                          Function<ID, R> function,
                                          Long time, TimeUnit timeUnit) {
        // 产品key
        String key = keyPrefix + id;
        // 查询redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 若结果为 "" 字符串代表是缓存空对象处理的redis，直接返回 null
        if ("".equals(json)) {
            return null;
        }
        // 查询数据库数据
        R r = function.apply(id);
        // 若数据库不存在，使用缓存空对象方法处理缓存穿透,并返回null，此处过时时间默认为2分钟
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", 2L, TimeUnit.MINUTES);
            return null;
        }
        // 存在的情况下，存储入缓存
        set(key, r, time, timeUnit);
        // 返回值
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     *
     * @param keyPrefix key前缀
     * @param id        id
     * @param type      返回值类型
     * @param function  数据库查询逻辑
     * @param time      查询数据库后，存入缓存的过期时间
     * @param timeUnit  时间单位
     * @param <R>       返回值类型
     * @param <ID>      查询参数
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        // 构造redis查询key
        String key = keyPrefix + id;
        // 查询结果
        String json = stringRedisTemplate.opsForValue().get(key);
        // 若不存在，则代表数据不存在，不是热点key，返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 存在，类型转换
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        // 过期判断
        // 未过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;
        }
        // 过期 -> 缓存重建
        boolean isLock = tryLock(key);
        if (isLock) {
            // 新建线程执行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 数据库查询
                    R r1 = function.apply(id);
                    // 缓存数据
                    setWithLogicExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(key);
                }
            });
        }
        return r;
    }
    /**
     * 获取互斥锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 防止拆箱空指针
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
