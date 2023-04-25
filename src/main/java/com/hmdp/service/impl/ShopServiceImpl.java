package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author qzk
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 按id查询
     *
     * @param id id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透解决方案
        //Shop shop = queryWithPassThrough(id);
        // 互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);

        // 缓存穿透解决方案 使用封装工具类
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿 使用封装工具类
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }


    /**
     * 逻辑过期存储店铺信息
     *
     * @param id            店铺id
     * @param expireSeconds 过期时间
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        // 设置过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 设置data
        redisData.setData(shop);
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
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


    /**
     * 查询店铺（处理缓存穿透版）
     *
     * @param id 店铺id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        // 1.查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 存在 -> 返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否命中空值
        if (shopJson != null) {
            return null;
        }
        // 2. 不存在 -> 查询数据库
        Shop shop = getById(id);
        // 不存在 -> 返回
        if (shop == null) {
            // 缓存空对象
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 存在写入redis 添加过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回
        return shop;
    }


    /**
     * 查询店铺（互斥锁解决缓存击穿）
     *
     * @param id 店铺id
     * @return
     */
    private Shop queryWithMutex(Long id) {
        // 1.查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 存在 -> 返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否命中空值
        if (shopJson != null) {
            return null;
        }
        Shop shop = null;
        try {
            // 执行缓存重建
            // 获取互斥锁
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
            if (!isLock) {
                Thread.sleep(50);
                // double check 保证一致性
                shopJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopJson)) {
                    shop = JSONUtil.toBean(shopJson, Shop.class);
                    return shop;
                }
                return queryWithMutex(id);
            }
            // 2. 不存在 -> 查询数据库
            shop = getById(id);
            // 不存在 -> 返回
            if (shop == null) {
                // 缓存空对象
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 存在写入redis 添加过期时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY + id);
        }

        // 返回
        return shop;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 查询店铺（逻辑过期解决缓存击穿）
     *
     * @param id 店铺id
     * @return
     */
    private Shop queryWithLogicalExpire(Long id) {
        // 查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 不存在 -> 返回 null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 存在
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期 -> 返回
            return shop;
        }
        // 过期 -> 缓存重建
        // 缓存重建
        // 获取锁 成功 -> 开启线程执行缓存重建
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        if (isLock) {
            // double check 查询过期时间
            shopJson = stringRedisTemplate.opsForValue().get(key);
            // 不存在 -> 返回 null
            if (StrUtil.isBlank(shopJson)) {
                return null;
            }
            // 存在
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            data = (JSONObject) redisData.getData();
            shop = JSONUtil.toBean(data, Shop.class);
            expireTime = redisData.getExpireTime();
            // 判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期 -> 返回
                return shop;
            }
            // 过期
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(LOCK_SHOP_KEY + id);
                }
            });
        }
        // 获取锁 失败 -> 返回老数据（过期的）
        return shop;
    }


}
