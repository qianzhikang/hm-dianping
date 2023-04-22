package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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
        Shop shop = queryWithMutex(id);
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
}
