package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Description TODO
 * @Date 2023-04-26-13-36
 * @Author qianzhikang
 */
public class SimpleRedisLock implements ILock {
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /** lua解锁脚本 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // resource 的 lua 文件脚本
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 获取锁
     *
     * @param timeout 超时时间
     * @return boolean
     */
    @Override
    public boolean tryLock(Long timeout) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId , timeout, TimeUnit.SECONDS);
        // 预防拆箱空指针
        return Boolean.TRUE.equals(success);
    }


    /**
     * 释放锁
     */
    @Override
    public void unLock() {
       // 调用脚本,释放锁
        // 此处在lua脚本中执行，保证原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
    ///**
    // * 释放锁
    // */
    //@Override
    //public void unLock() {
    //    // 获取线程标识
    //    String threadId = ID_PREFIX + Thread.currentThread().getId();
    //    // 获取锁中的线程标识
    //    String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //    // 判断一致
    //    if (id.equals(threadId)) {
    //        // 释放锁
    //        stringRedisTemplate.delete(KEY_PREFIX + name);
    //    }
    //}
}
