package cn.xxywithpq.lock;


import cn.xxywithpq.SimplifyLockSpringBootStarterApplication;
import cn.xxywithpq.lock.funnelRete.SimplifyLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SimplifyLockSpringBootStarterApplication.class)
public class SimplifyLockTest {

    @Autowired
    SimplifyLock simplifyLock;

    /**
     * 带分布式锁
     */
    @Test
    @Repeat(2)
    public void main() {
        int num = 10;
        CountDownLatch countDownLatch = new CountDownLatch(num);

        final int[] concurrentNum = {0};
        for (int i = 0; i < num; i++) {
            new Thread(() -> {
                try {

                    boolean test = simplifyLock.acquire("test", 20, 10);
                    if (test) {
                        log.info("锁获取成功 {}", Thread.currentThread().getId());

                        boolean test1 = simplifyLock.acquire("test", 20, 10);
                        if (test1) {
                            log.info("重入锁 获取成功 {}", Thread.currentThread().getId());
                        }
                        test();
                    }
                } catch (Exception e) {
                    log.error("SimplifyLockTest e {}", e);
                } finally {
                    boolean unLock = false;
                    simplifyLock.unlock("test");
                    unLock = simplifyLock.unlock("test");

                    if (unLock) {
                        countDownLatch.countDown();
                    }

                }
            }).start();
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
        }
        log.info("all is ok");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void test() {
        int num = 20;
        CountDownLatch countDownLatch = new CountDownLatch(num);
        CyclicBarrier barrier = new CyclicBarrier(num);

        final int[] concurrentNum = {0};
        for (int i = 0; i < num; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
                concurrentNum[0]++;
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                concurrentNum[0]++;
                countDownLatch.countDown();
            }).start();
        }

        try {
            countDownLatch.await();
            log.info("result {}", concurrentNum[0]);
            assertEquals(num * 2, concurrentNum[0]);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
