package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 查缓存
        String listJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        // 查到 -> 返回
        if (StrUtil.isNotBlank(listJson)) {
            List<ShopType> shopTypes = JSONUtil.toList(listJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 查不到 -> 数据库查询
        List<ShopType> list = query().list();
        // 添加缓存
        if (CollectionUtil.isNotEmpty(list)) {
            String jsonStr = JSONUtil.toJsonStr(list);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, jsonStr);
        }
        // 返回
        return Result.ok(list);
    }
}
