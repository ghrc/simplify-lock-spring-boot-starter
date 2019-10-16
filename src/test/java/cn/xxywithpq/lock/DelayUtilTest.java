package cn.xxywithpq.lock;


import cn.xxywithpq.delay.DelayUtil;
import cn.xxywithpq.delay.DelayUtilFactory;
import cn.xxywithpq.lock.funnel.rate.Funnel;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class DelayUtilTest {

    @Test
    public void test() throws InterruptedException {

        for (int i = 0; i < 10; i++) {
            int finalI = i;
            DelayUtil instance = DelayUtilFactory.getInstance(String.valueOf(finalI), 10, 10, 10, (x) -> test((Funnel) x));
            instance.put(new Funnel(i, i));
        }

        Thread.sleep(1000000000);
    }


    public void test(Funnel delayUtil) {
        log.info("delayUtil {}", delayUtil);
    }


}
