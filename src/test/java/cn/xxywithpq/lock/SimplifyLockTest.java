package cn.xxywithpq.lock;


import cn.xxywithpq.SimplifyLockSpringBootStarterApplication;
import cn.xxywithpq.lock.funnelRete.SimplifyLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SimplifyLockSpringBootStarterApplication.class)
public class SimplifyLockTest {

    @Autowired
    SimplifyLock simplifyLock;

    @Test
    public void main() {
        int num = 5;
        CountDownLatch countDownLatch = new CountDownLatch(num);

        for (int i = 0; i < num; i++) {
            new Thread(() -> {
                boolean test = simplifyLock.acquire("test", 20, 10);

                if (test) {
                    log.info("锁获取成功 {}", Thread.currentThread().getId());

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                simplifyLock.unlock("test");
                countDownLatch.countDown();
            }).start();
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
        }
        log.info("all is ok");

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
