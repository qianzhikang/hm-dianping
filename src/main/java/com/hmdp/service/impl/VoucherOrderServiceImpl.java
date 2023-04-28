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
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author qzk
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private VoucherOrderServiceImpl voucherOrderServiceImpl;

    /**
     * lua解锁脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // resource 的 lua 文件脚本
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 秒杀订单入库的执行队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 执行秒杀订单入库的线程池
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 处理秒杀订单业务入库的具体任务
    private class VoucherOrderHandle implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常,{}", e);
                }

            }
        }
    }

    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandle());
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = UserHolder.getUser().getId();
        // 使用 redisson 实现可重入锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean success = lock.tryLock();

        if (!success) {
           log.error("重复下单！");
           return;
        }
        try {
            voucherOrderServiceImpl.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }


    // 优化版 秒杀接口
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 执行lua脚本，判断用户秒杀下单资格
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString());
        // 判断结果 0：下单成功  1：库存不足  2：重复下单
        int r = result.intValue();
        if (r != 0) {
            return r == 1 ? Result.fail("库存不足") : Result.fail("重复下单");
        }
        long orderId = redisIdWorker.nextId("order");

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 生成全局唯一订单id
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        // 创建添加到阻塞队列
        orderTasks.add(voucherOrder);

        // 返回
        return Result.ok(orderId);
    }

    //@Override
    //public Result seckillVoucher(Long voucherId) {
    //    // 查询秒杀券信息
    //    SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
    //    // 判断秒杀是否开始或结束
    //    if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //        return Result.fail("尚未开始");
    //    }
    //    if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
    //        return Result.fail("已经结束");
    //    }
    //    // 查询库存是否充足
    //    if (seckillVoucher.getStock() < 1) {
    //        return Result.fail("库存不足");
    //    }
    //    // 获取用户id
    //    Long userId = UserHolder.getUser().getId();
    //    //// 上锁解决并发问题 控制事务提交
    //    //synchronized (userId.toString().intern()) {
    //    //    // 获取代理对象解决事务失效问题
    //    //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //    //    return proxy.createVoucherOrder(voucherId);
    //    //}
    //
    //
    //    //// 创建分布式锁对象
    //    //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
    //    //
    //    //// 获取锁
    //    //boolean isLock = lock.tryLock(1200L);
    //    //if (!isLock) {
    //    //    // 失败
    //    //    return Result.fail("不允许重复下单");
    //    //}
    //    //try {
    //    //    // 获取锁成功
    //    //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //    //    return proxy.createVoucherOrder(voucherId);
    //    //} finally {
    //    //    // 释放锁
    //    //    lock.unLock();
    //    //}
    //
    //    // 使用 redisson 实现可重入锁
    //    RLock lock = redissonClient.getLock("lock:order:" + userId);
    //    boolean success = lock.tryLock();
    //
    //    if (!success) {
    //        return Result.fail("不允许重复下单");
    //    }
    //    try {
    //        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //        return proxy.createVoucherOrder(voucherId);
    //    } finally {
    //        lock.unlock();
    //    }
    //}

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单的问题
        // 查询用户订单
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 判断是否下过单
        if (count > 0) {
            log.error("用户已经购买过该优惠券了");
            return;
        }

        // 扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        //// 创建订单
        //VoucherOrder voucherOrder = new VoucherOrder();
        //// 生成全局唯一订单id
        //voucherOrder.setId(redisIdWorker.nextId("order"));
        //voucherOrder.setUserId(UserHolder.getUser().getId());
        //voucherOrder.setVoucherId(voucherId);
        // 订单存储入库
        save(voucherOrder);
        // 返回订单id
        //return Result.ok(voucherOrder.getId());
    }
}
