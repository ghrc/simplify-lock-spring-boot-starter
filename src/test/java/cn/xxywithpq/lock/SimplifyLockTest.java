package cn.xxywithpq.lock;


import cn.xxywithpq.SimplifyLockSpringBootStarterApplication;
import cn.xxywithpq.lock.funnelRete.SimplifyLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SimplifyLockSpringBootStarterApplication.class)
public class SimplifyLockTest {

    @Autowired
    SimplifyLock simplifyLock;

    @Test
    public void main() {

        for (int i = 0; i < 5; i++) {
            boolean test = simplifyLock.acquire("test", 20, 10);

            if (test) {
                log.info("锁获取成功 {}", Thread.currentThread().getId());
            }
            simplifyLock.unlock("test");

        }
    }

}
