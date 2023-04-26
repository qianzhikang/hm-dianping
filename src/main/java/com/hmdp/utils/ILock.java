package com.hmdp.utils;

/**
 * @Description TODO
 * @Date 2023-04-26-13-30
 * @Author qianzhikang
 */
public interface ILock {
    /**
     * 获取锁
     * @param timeout 超时时间
     * @return boolean
     */
    boolean tryLock(Long timeout);

    /**
     * 释放锁
     */
    void unLock();
}
