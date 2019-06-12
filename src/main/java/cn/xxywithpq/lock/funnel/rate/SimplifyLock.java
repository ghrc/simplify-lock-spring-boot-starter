package cn.xxywithpq.lock.funnel.rate;

import cn.xxywithpq.lock.funnel.rate.conf.CustomsProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @description:
 * @author: qian.pan
 * @create: 2019/04/28 17:58
 **/
@Slf4j
@Component
public class SimplifyLock {

    private final String OK = "OK";
    private final Integer DEFAULT_LEASE_TIME = 30;
    private final ConcurrentHashMap<String, SimplifyLock.Sync> concurrentHashMap = new ConcurrentHashMap();
    @Autowired
    CustomsProperties customsProperties;
    @Autowired
    private JedisPool jedisPool;

    private String packageLockKey(String lockKey) {
        return String.format("%s:distributedLock:%s", customsProperties.getNamespace(), lockKey);
    }

    /**
     * 尝试redis获取锁(原子操作)
     *
     * @param jedis
     * @param lockKey
     * @param leaseTime 暂不实现自动过期
     * @return
     */
    private final boolean jedisAcquire(String lockKey, int leaseTime) {
        log.info("jedisAcquire {}", Thread.currentThread().getId());
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(lockKey, "1", "NX", "EX", DEFAULT_LEASE_TIME);
            if (OK.equalsIgnoreCase(result)) {
                return true;
            }
            return false;
        }
    }

    /**
     * 尝试redis获取锁(原子操作)
     *
     * @return
     */
    private final boolean jedisDel(String lockKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(lockKey);
            return true;
        }
    }

    public final boolean lock(String key) {
        log.info("begin to lock {}", Thread.currentThread().getId());
        try {
            getSync(key).acquire(1);
        } catch (Exception e) {
            log.error("lock fail {}", e);
            return false;
        }
        return true;
    }

    public final void unlock(String key) {
        getSync(key).release(1);
    }


    private final Sync getSync(String key) {
        key = packageLockKey(key);
        Sync sync;
        if (concurrentHashMap.containsKey(key)) {
            sync = concurrentHashMap.get(key);
        } else {
            Sync newSync = new Sync(key);
            sync = concurrentHashMap.putIfAbsent(key, newSync);
            if (null == sync) {
                sync = newSync;
            }
        }
        return sync;
    }

    @Data
    private class Sync extends AbstractQueuedSynchronizer {

        private final String name;

        public Sync(String name) {
            this.name = name;
        }

        @Override
        protected boolean tryAcquire(int acquires) {
            for (; ; ) {
                final Thread current = Thread.currentThread();
                int c = getState();
                if (c == 0) {
                    if (!hasQueuedPredecessors() && null == getExclusiveOwnerThread()
                            && jedisAcquire(this.name, DEFAULT_LEASE_TIME)) {
                        setState(c + 1);
                        setExclusiveOwnerThread(current);
                        return true;
                    }
                } else if (current == getExclusiveOwnerThread()) {
                    int nextc = c + acquires;
                    if (nextc < 0) {
                        throw new Error("Maximum lock count exceeded");
                    }
                    setState(nextc);
                    return true;
                }

                if (null == getExclusiveOwnerThread()) {
                    continue;
                }
                return false;
            }
        }

        @Override
        protected boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
                throw new IllegalMonitorStateException();
            }
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
                jedisDel(this.name);
            }
            setState(c);
            return free;
        }
    }

}
