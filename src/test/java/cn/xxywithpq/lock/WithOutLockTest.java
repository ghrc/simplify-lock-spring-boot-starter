package cn.xxywithpq.lock;


import cn.xxywithpq.SimplifyLockSpringBootStarterApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SimplifyLockSpringBootStarterApplication.class)
public class WithOutLockTest {

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
                    int i1 = count[0];
                    Thread.sleep(100);
                    count[0] = i1 + 1;
                } catch (Exception e) {
                    log.error("SimplifyLockTest e {}", e);
                } finally {
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();
        System.out.println("count:" + count[0]);
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
