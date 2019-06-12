package cn.xxywithpq.lock;


import cn.xxywithpq.SimplifyLockSpringBootStarterApplication;
import cn.xxywithpq.lock.funnel.rate.SimplifyLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    public void main() throws InterruptedException {
        int num = 20;
        CountDownLatch countDownLatch = new CountDownLatch(num);

        int[] count = new int[]{0};
        for (int i = 0; i < num; i++) {
            new Thread(() -> {
                try {
                    simplifyLock.lock("test");
                    simplifyLock.lock("test");
                    log.info("锁获取成功 {}", Thread.currentThread().getId());
                    int i1 = count[0];
                    Thread.sleep(3000);
                    count[0] = i1 + 1;
                } catch (Exception e) {
                    log.error("SimplifyLockTest e {}", e);
                } finally {
                    simplifyLock.unlock("test");
                    simplifyLock.unlock("test");
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();
        log.info("count:" + count[0]);
        assertEquals(num, count[0]);
    }


}
