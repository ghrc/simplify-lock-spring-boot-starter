package cn.xxywithpq.lock;


import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class WithoutLockTest {


    /**
     * 不带分布式锁
     */
    @RepeatedTest(100)
    public void main() {
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
