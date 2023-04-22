# Redis缓存实战

## 1. 缓存

### 1.1 添加缓存层

![1653322097736](/Users/qianzhikang/Code/hm-dianping/note/assets/1653322097736.png)

> 代码思路：如果缓存有，则直接返回，如果缓存不存在，则查询数据库，然后存入redis

```java
public Result queryById(Long id) {
        // 1.查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 存在 -> 返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 2. 不存在 -> 查询数据库
        Shop shop = getById(id);
        // 不存在 -> 返回
        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        // 存在写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        // 返回
        return Result.ok(shop);
    }
```

### 1.2 缓存不一致问题

由于我们的**缓存的数据源来自于数据库**,而数据库的**数据是会发生变化的**,因此,如果当数据库中**数据发生变化,而缓存却没有同步**,此时就会有**一致性问题存在**

> 实现商铺和缓存与数据库双写一致

