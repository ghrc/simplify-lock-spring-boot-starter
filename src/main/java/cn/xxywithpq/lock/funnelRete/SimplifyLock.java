package cn.xxywithpq.lock.funnelRete;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @program: infra-monitor-service
 * @description:
 * @author: qian.pan
 * @create: 2019/04/28 17:58
 **/
@Slf4j
@Component
public class SimplifyLock {

    private final String OK = "OK";
    private final ConcurrentHashMap<String, SimplifyLock.Sync> concurrentHashMap = new ConcurrentHashMap();

    @Autowired
    private JedisPool jedisPool;

    private String packageLockKey(String lockKey) {
        return String.format("infra-monitor:distributedLock:%s", lockKey);
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
            String result = jedis.set(lockKey, "1", "NX");
            if (OK.equalsIgnoreCase(result)) {
                log.info("jedisAcquire success，Thread {} 拿到锁", Thread.currentThread().getId());
                return true;
            }
            return false;
        }
    }

    /**
     * 尝试redis获取锁(原子操作)
     *
     * @param jedis
     * @param lockKey
     * @param leaseTime 暂不实现自动过期
     * @return
     */
    private final boolean jedisDel(String lockKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            Long l = jedis.del(lockKey);
            return true;
        }
    }

    public final void lock(String key) {
        log.info("begin to lock {}", Thread.currentThread().getId());
        getSync(key).acquire(1);
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
                            && jedisAcquire(this.name, 0)) {
                        setState(c + 1);
                        setExclusiveOwnerThread(current);
                        return true;
                    }
                } else if (isHeldExclusively()) {
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
            if (!isHeldExclusively()) {
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

        @Override
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
    }

}
