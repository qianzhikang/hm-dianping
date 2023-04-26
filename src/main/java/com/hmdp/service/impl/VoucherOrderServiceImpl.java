package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author qzk
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询秒杀券信息
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始或结束
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("已经结束");
        }
        // 查询库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        //// 上锁解决并发问题 控制事务提交
        //synchronized (userId.toString().intern()) {
        //    // 获取代理对象解决事务失效问题
        //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //    return proxy.createVoucherOrder(voucherId);
        //}

        // 创建分布式锁对象
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock(1200L);
        if (!isLock) {
            // 失败
            return Result.fail("不允许重复下单");
        }
        try {
            // 获取锁成功
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unLock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单的问题
        // 查询用户订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 判断是否下过单
        if (count > 0) {
            return Result.fail("用户已经购买过该优惠券了！");
        }

        // 扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 生成全局唯一订单id
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        // 订单存储入库
        save(voucherOrder);
        // 返回订单id
        return Result.ok(voucherOrder.getId());
    }
}
