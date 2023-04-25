package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.mockito.internal.matchers.Or;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    void preheating() {
        shopServiceImpl.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(500);
        CountDownLatch latch = new CountDownLatch(300);
        //Runnable task = () -> {
        //    for (int j = 0; j < 100; j++) {
        //        long order = redisIdWorker.nextId("order");
        //        System.out.println("id=" + order);
        //    }
        //
        //};
        for (int i = 0; i < 300; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    long order = redisIdWorker.nextId("order");
                    System.out.println("id=" + order);
                }
                latch.countDown();
            });
        }
        latch.await();
    }


    // 多线程测试
    @Test
    void testThread() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(500);
        Runnable task = () -> {
            for (int j = 0; j < 100; j++) {
                long order = redisIdWorker.nextId("order");
                System.out.println("id=" + order);
            }
        };
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        Thread.sleep(10000);
    }
}
