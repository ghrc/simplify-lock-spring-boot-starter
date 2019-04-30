package cn.xxywithpq.lock.funnelRete;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @program: infra-monitor-service
 * @description:
 * @author: qian.pan
 * @create: 2019/04/25 14:41
 **/
@Data
@AllArgsConstructor
public class Funnel {
    /**
     * 漏斗容量
     */
    long capacity;
    /**
     * 漏斗剩余空间
     */
    long leftQuota;
    /**
     * 上一次流水时间(时间戳)
     */
    long leakingTs;

    /**
     * 漏斗流水速率（capacity / leakingDuration）
     */
    float leakingRate;


    /**
     * @param capacity        漏斗容量
     * @param leakingDuration 漏斗 从空到满 所需要的时间(秒)
     */
    public Funnel(long capacity, float leakingDuration) {
        this.capacity = capacity;
        this.leakingRate = capacity / leakingDuration;
        this.leftQuota = capacity;
        this.leakingTs = System.currentTimeMillis();
    }


}
