package cn.xxywithpq.lock;


import cn.xxywithpq.SimplifyLockSpringBootStarterApplication;
import cn.xxywithpq.lock.funnel.rate.FunnelRateLimiter;
import cn.xxywithpq.lock.funnel.rate.SimplifyLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SimplifyLockSpringBootStarterApplication.class)
public class FunnelTest {

    @Autowired
    FunnelRateLimiter funnelRateLimiter;

    /**
     * 带分布式锁
     */
    @Test
    public void main() throws InterruptedException {
        int num = 20;
        CountDownLatch countDownLatch = new CountDownLatch(num);

        int[] count = new int[]{0};
        for (int i = 0; i < num; i++) {
            Thread.sleep(5000);
            new Thread(() -> {
                try {
                    if (funnelRateLimiter.isActionAllowed("test", "funnelTest", 1, 10, 1)) {
                        log.info("我被获准进来啦 {}", Thread.currentThread().getId());
                        int i1 = count[0];
                        Thread.sleep(500);
                        count[0] = i1 + 1;
                    }

                } catch (Exception e) {
                    log.error("FunnelTest e {}", e);
                } finally {
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();
        log.info("count:" + count[0]);
        assertEquals(num / 2, count[0]);
    }


}
