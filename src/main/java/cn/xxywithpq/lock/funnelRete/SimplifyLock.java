package cn.xxywithpq.lock.funnelRete;

import com.sun.corba.se.impl.orbutil.concurrent.Sync;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @program: infra-monitor-service
 * @description:
 * @author: qian.pan
 * @create: 2019/04/28 17:58
 **/
@Slf4j
@Component
public class SimplifyLock {

    public final String OK = "OK";

    @Autowired
    private JedisPool jedisPool;

    /**
     * The current owner of exclusive mode synchronization.
     */
    private transient ConcurrentHashMap<String, ThreadLock> exclusiveOwnerThread = new ConcurrentHashMap<>();

    private transient ConcurrentHashMap<String, ConcurrentLinkedDeque<Thread>> dequeMap = new ConcurrentHashMap();

    /**
     * 释放锁
     *
     * @param lockKey
     */
    public boolean unlock(String lockKey) {
        lockKey = packageLockKey(lockKey);
        try (Jedis jedis = jedisPool.getResource()) {
            if (tryRelease(lockKey, jedis)) {
                log.info("释放锁成功 unlock {}", Thread.currentThread().getId());
                unparkSuccessor(lockKey);
                return true;
            }
        }
        return false;
    }

    private void unparkSuccessor(String lockKey) {
        ConcurrentLinkedDeque<Thread> threads = dequeMap.get(lockKey);
        if (null != threads) {
            Thread thread = threads.peekFirst();
            if (null != thread) {
                LockSupport.unpark(thread);
                log.info("唤醒 unparkSuccessor {}", thread.getId());
            }
        }
    }


    private String packageLockKey(String lockKey) {
        return String.format("infra-monitor:distributedLock:%s", lockKey);
    }


    private Thread getExclusiveOwnerThread(String lockKey) {
        return null == exclusiveOwnerThread.get(lockKey) ? null : exclusiveOwnerThread.get(lockKey).thread;
    }

    private void initExclusiveOwnerThread(String lockKey) {
        log.info("initExclusiveOwnerThread start {}", Thread.currentThread().getId());
        ThreadLock threadLock = new ThreadLock();
        threadLock.setThread(Thread.currentThread());
        exclusiveOwnerThread.put(lockKey, threadLock);
        log.info("initExclusiveOwnerThread end {} {}", Thread.currentThread().getId(), threadLock);
    }

    private void removeExclusiveOwnerThread(String lockKey) {
        exclusiveOwnerThread.remove(lockKey);
    }

    public final boolean acquire(final String lockKey, int leaseTime, long waitTime) {
        final String finalLockKey = packageLockKey(lockKey);
        log.info("准备拿锁 {}", Thread.currentThread().getId());
        try (Jedis jedis = jedisPool.getResource()) {
            if (!tryAcquire(jedis, 1, finalLockKey, leaseTime) &&
                    acquireQueued(jedis, 1, finalLockKey, leaseTime)) {
            }
        }
        return exclusiveOwnerThread.get(finalLockKey).thread == Thread.currentThread() ? true : false;
    }


    /**
     * 判断当前线程是否 是队列的第一个（公平锁机制）
     *
     * @param lockKey
     * @return 如果当前线程之前有一个排队的线程，则为true,如果当前线程是队列的头节点或队列为空，返回false
     */
    public final boolean hasQueuedPredecessors(String lockKey) {
        ConcurrentLinkedDeque concurrentLinkedDeque = dequeMap.get(lockKey);
        log.info("hasQueuedPredecessors start {}  {}", Thread.currentThread().getId(), concurrentLinkedDeque);
        boolean flag = !(null == concurrentLinkedDeque || concurrentLinkedDeque.peekFirst() == Thread.currentThread());
        log.info("hasQueuedPredecessors end {}", flag);
        return flag;
    }


    /**
     * 尝试获得锁
     */
    protected final boolean tryAcquire(Jedis jedis, int acquires, final String lockKey, int leaseTime) {
        final Thread current = Thread.currentThread();
//        1.先在本地查询是否当前lockKey是否已经有一个锁
        ThreadLock threadLock = exclusiveOwnerThread.get(lockKey);
        log.info("本地查询是否当前lockKey是否已经有一个锁 {} {}", Thread.currentThread().getId(), threadLock);
        int c = 0;
        if (threadLock != null) {
            c = threadLock.getCount();
        }
//        2.本地没有记录,进入抢锁流程
        if (c == 0) {
            log.info("本地没有记录,进入抢锁流程 {}", Thread.currentThread().getId());
            if (!hasQueuedPredecessors(lockKey) &&
                    jedisAcquire(jedis, lockKey, leaseTime)) {
                initExclusiveOwnerThread(lockKey);
                return true;
            }
        } else if (current == threadLock.getThread()) {
            int nextc = c + acquires;
            if (nextc < 0) {
                throw new Error("Maximum lock count exceeded");
            }
            threadLock.setCount(nextc);
            log.info("tryAcquire 可重入 {}", threadLock);
            return true;
        }
        return false;
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
     * 线程锁的封装对象，count 实现重入锁,没存在redis而存在本地内存，原因:
     * <p>
     * - 在没有锁竞争的情况下，便于实现偏向锁，减少redis通信，优化性能
     * <p>
     * - Thread不能Serializable。
     */
    @Data
    class ThreadLock {
        /**
         * 线程
         */
        private Thread thread;
        /**
         * 重入次数
         */
        private volatile int count = 1;

        void setCount(int count) {
            this.count = count;
        }

        protected final int getState() {
            return count;
        }
    }


    /**
     * 重复尝试获取锁
     *
     * @param jedis
     * @param acquires
     * @param lockKey
     * @param leaseTime
     * @return
     */
    final boolean acquireQueued(Jedis jedis, int acquires, final String lockKey, int leaseTime) {
        for (; ; ) {
            if (!tryAcquire(jedis, acquires, lockKey, leaseTime)) {
                parkAndCheckInterrupt(lockKey);
            } else {
                return true;
            }
        }
    }

    boolean addWaiter(final String lockKey) {
        ConcurrentLinkedDeque<Thread> threadsDeque = dequeMap.get(lockKey);
        if (null == threadsDeque) {
            ConcurrentLinkedDeque<Thread> deque = new ConcurrentLinkedDeque<>();
            deque.add(Thread.currentThread());
            dequeMap.put(lockKey, deque);
        } else {
            threadsDeque.add(Thread.currentThread());
        }
        log.info("我被加入等候队列 addWaiter {} {}", Thread.currentThread().getId(), dequeMap);
        return true;
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt(String lockKey) {
        ThreadLock threadLock = exclusiveOwnerThread.get(lockKey);
        if (null != threadLock && addWaiter(lockKey)) {
            LockSupport.park(this);
            log.info("我被睡眠了 parkAndCheckInterrupt {}", Thread.currentThread().getId());
            return true;
        }
        return false;
    }


    private final boolean tryRelease(String lockKey, Jedis jedis) {
        if (Thread.currentThread() != getExclusiveOwnerThread(lockKey)) {
            throw new IllegalMonitorStateException("Thread: " + Thread.currentThread().getId() + " lockKey: " + lockKey + " 解锁状态异常");
        }
        ThreadLock threadLock = exclusiveOwnerThread.get(lockKey);
        boolean free = false;
        if (null != threadLock && (threadLock.count += -1) == 0) {
            jedis.del(lockKey);
            removeExclusiveOwnerThread(lockKey);
            free = true;
        }
        return free;
    }

    private final ConcurrentHashMap<String, SimplifyLock.Sync> concurrentHashMap = new ConcurrentHashMap();

    public final void lock(String key) {
        Sync sync;
        if (concurrentHashMap.contains(key)) {
            sync = concurrentHashMap.get(key);
        } else {
            Sync newSync = new Sync(key);
            sync = concurrentHashMap.putIfAbsent(key, newSync);
            if (null == sync) {
                sync = newSync;
            }
        }
        sync.acquire(1);
    }

    @Data
    private class Sync extends AbstractQueuedSynchronizer {

        private final String name;

        public Sync(String name) {
            this.name = name;
        }

        @Override
        protected boolean tryAcquire(int arg) {
//            final String finalLockKey = packageLockKey(lockKey);
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedPredecessors() &&
                        jedisAcquire(lockKey, 0)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }

//        1.先在本地查询是否当前lockKey是否已经有一个锁
            ThreadLock threadLock = exclusiveOwnerThread.get(lockKey);
            log.info("本地查询是否当前lockKey是否已经有一个锁 {} {}", Thread.currentThread().getId(), threadLock);
            int c = 0;
            if (threadLock != null) {
                c = threadLock.getCount();
            }
//        2.本地没有记录,进入抢锁流程
            if (c == 0) {
                log.info("本地没有记录,进入抢锁流程 {}", Thread.currentThread().getId());
                if (!hasQueuedPredecessors(lockKey) &&
                        jedisAcquire(jedis, lockKey, leaseTime)) {
                    initExclusiveOwnerThread(lockKey);
                    return true;
                }
            } else if (current == threadLock.getThread()) {
                int nextc = c + acquires;
                if (nextc < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                threadLock.setCount(nextc);
                log.info("tryAcquire 可重入 {}", threadLock);
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int arg) {
            return super.tryRelease(arg);
        }

        @Override
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
    }

}
