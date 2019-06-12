package cn.xxywithpq.lock.funnel.rate;

import cn.xxywithpq.lock.funnel.rate.conf.CustomsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Component
@Slf4j
public class FunnelRateLimiter {

    private final String LOCK_IS_ACTIONALLOWED = "isActionAllowed";

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private CustomsProperties customsProperties;

    @Autowired
    SimplifyLock simplifyLock;

    /**
     * @param unionId         限流唯一id
     * @param actionKey       限流类型
     * @param capacity        漏斗容量
     * @param leakingDuration 漏斗全部灌满所需时间，用于计算流速(秒)
     * @param quota           漏斗配额步长(一次请求多少配额,一般为1次)
     * @return 是否允许操作
     */
    public boolean isActionAllowed(String unionId, String actionKey, long capacity, float leakingDuration, int quota) {
        String key = String.format("%s:funnel:%s:%s", customsProperties.getNamespace(), actionKey, unionId);
        boolean tryLock = simplifyLock.lock(LOCK_IS_ACTIONALLOWED + ":" + actionKey);
        if (!tryLock) {
            log.error("isActionAllowed No lock taken {} {}", unionId, actionKey);
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            Funnel funnel = getFunnel(jedis, key);
            if (funnel == null) {
                funnel = new Funnel(capacity, leakingDuration);
            }
            boolean flag = watering(funnel, quota);
//            对数据进行更新
            putFunnel(jedis, key, funnel, leakingDuration);
            return flag;
        } catch (Exception e) {
            log.error("FunnelRateLimiter isActionAllowed error {}", e);
            return false;
        } finally {
            simplifyLock.unlock(LOCK_IS_ACTIONALLOWED);
        }
    }


    private Funnel getFunnel(Jedis jedis, String key) {
        Map<String, String> funnelMap = jedis.hgetAll(key);
        if (null != funnelMap && funnelMap.size() > 0) {
            return new Funnel(Long.valueOf(funnelMap.get("capacity")), Long.valueOf(funnelMap.get("leftQuota")), Long.valueOf(funnelMap.get("leakingTs")), Float.valueOf(funnelMap.get("leakingRate")));
        }
        return null;
    }


    void putFunnel(Jedis jedis, String key, Funnel funnel, float leakingDuration) {
        HashMap<String, String> funnelMap = new HashMap<>(4);
        funnelMap.put("capacity", String.valueOf(funnel.getCapacity()));
        funnelMap.put("leftQuota", String.valueOf(funnel.getLeftQuota()));
        funnelMap.put("leakingTs", String.valueOf(funnel.getLeakingTs()));
        funnelMap.put("leakingRate", String.valueOf(funnel.getLeakingRate()));

        Pipeline p = jedis.pipelined();
        jedis.hmset(key, funnelMap);
        p.expire(key, (int) leakingDuration + 10);
        p.sync();
    }


    private void makeSpace(Funnel funnel) {
        long nowTs = System.currentTimeMillis();
        long deltaTs = TimeUnit.MILLISECONDS.toSeconds(nowTs - funnel.getLeakingTs());
        int deltaQuota = (int) (deltaTs * funnel.getLeakingRate());
        // 腾出空间太小，最小单位是1
        if (deltaQuota < 1) {
            return;
        }
        funnel.setLeftQuota(funnel.getLeftQuota() + deltaQuota);
        funnel.setLeakingTs(nowTs);
        if (funnel.getLeftQuota() > funnel.getCapacity()) {
            funnel.setLeftQuota(funnel.getCapacity());
        }
    }

    private boolean watering(Funnel funnel, int quota) {
        makeSpace(funnel);
        if (funnel.getLeftQuota() >= quota) {
            funnel.setLeftQuota(funnel.getLeftQuota() - quota);
            return true;
        }
        return false;
    }


//    /**
//     * 尝试获取锁
//     *
//     * @param lockKey
//     * @param waitTime  最多等待时间(秒)
//     * @param leaseTime 上锁后自动释放锁时间(秒)
//     * @return
//     */
//    public boolean tryLock(String lockKey, long waitTime, int leaseTime) {
//        long currentThreadid = Thread.currentThread().getId();
//        final String finalLockKey = packageLockKey(lockKey);
//        try (Jedis jedis = jedisPool.getResource()) {
//            long time;
//            long begin = System.currentTimeMillis();
//            String result = jedis.set(finalLockKey, "1", "NX", "EX", leaseTime);
//            if (OK.equalsIgnoreCase(result)) {
//                return true;
//            }
//
////            到目前为止已经超时，则返回false
//            time = System.currentTimeMillis() - begin;
//            if (time > TimeUnit.SECONDS.toMillis(waitTime)) {
//                return false;
//            }
//            CountDownLatch l = new CountDownLatch(1);
//            ScheduledFuture<?> scheduledFuture = executorService.scheduleAtFixedRate(() -> {
//                long id = Thread.currentThread().getId();
//                String waitResult = jedis.set(finalLockKey, "1", "NX", "EX", leaseTime);
//                if (OK.equalsIgnoreCase(waitResult)) {
//                    l.countDown();
//                    throw new RuntimeException();
//                }
//            }, 0, 500, TimeUnit.MILLISECONDS);
//            boolean await = l.await(TimeUnit.SECONDS.toMillis(waitTime) - time, TimeUnit.MILLISECONDS);
//            if (!await) {
//                scheduledFuture.cancel(true);
//            }
//            return await;
//        } catch (InterruptedException e) {
//            log.error("FunnelRateLimiter InterruptedException {}", e);
//            return false;
//        }
//    }

//    /**
//     * 释放锁
//     *
//     * @param lockKey
//     */
//    public void unlock(String lockKey) {
//        final String finalLockKey = packageLockKey(lockKey);
//        try (Jedis jedis = jedisPool.getResource()) {
//            Long del = jedis.del(finalLockKey);
//        } catch (Exception e) {
//            log.error("FunnelRateLimiter unlock error {}", e);
//        }
//    }

//    private String packageLockKey(String lockKey) {
//        return String.format("%s:distributedLock:%s", customsProperties.getNamespace(), lockKey);
//    }
}