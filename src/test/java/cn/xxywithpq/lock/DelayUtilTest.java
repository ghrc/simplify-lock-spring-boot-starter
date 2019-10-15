package cn.xxywithpq.lock;


import cn.xxywithpq.delay.DelayUtil;
import cn.xxywithpq.delay.DelayUtilFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class DelayUtilTest {

    @Test
    public void test() throws InterruptedException {

        DelayUtilFactory limitUtilFactory = new DelayUtilFactory();

        for (int i = 0; i < 3; i++) {
            int finalI = i;
            DelayUtil instance = limitUtilFactory.getInstance(String.valueOf(finalI), 10, 10, 10, (x) -> {
                log.info("执行逻辑 {}", x);
            });
            instance.put("LimitUtilTest test" + finalI);
        }

        Thread.sleep(1000000000);
    }


}
