package cn.xxywithpq.lock.funnelRete;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/**
 * @program: infra-monitor-service
 * @description:
 * @author: qian.pan
 * @create: 2019/04/28 17:58
 **/
@Slf4j
@Component
public class SimplifyLock {

    ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

    public final String OK = "OK";

    @Autowired
    private JedisPool jedisPool;

    /**
     * The current owner of exclusive mode synchronization.
     */
    private transient ConcurrentHashMap<String, ThreadLock> exclusiveOwnerThread = new ConcurrentHashMap<>();

    private transient ConcurrentHashMap<String, ConcurrentLinkedDeque<Thread>> dequeMap = new ConcurrentHashMap();

    /**
     * 尝试获取锁
     *
     * @param lockKey
     * @param waitTime  最多等待时间(秒)
     * @param leaseTime 上锁后自动释放锁时间(秒)
     * @return
     */
    public boolean tryLock(String lockKey, long waitTime, int leaseTime) {
        Thread thread = Thread.currentThread();
        final String finalLockKey = packageLockKey(lockKey);
        try (Jedis jedis = jedisPool.getResource()) {
            long time;
            long begin = System.currentTimeMillis();
            log.info("获取锁开始 {} {}", thread.getId(), begin);
            String result = jedis.set(finalLockKey, "1", "NX", "EX", leaseTime);
            if (OK.equalsIgnoreCase(result)) {
                log.info("锁为空直接拿到锁，Thread {} 拿到锁", thread.getId());
                return true;
            }

//            到目前为止已经超时，则返回false
            time = System.currentTimeMillis() - begin;
            if (time > TimeUnit.SECONDS.toMillis(waitTime)) {
                return false;
            }
            CountDownLatch l = new CountDownLatch(1);
            ScheduledFuture<?> scheduledFuture = executorService.scheduleAtFixedRate(() -> {
                long id = Thread.currentThread().getId();
                String waitResult = jedis.set(finalLockKey, "1", "NX", "EX", leaseTime);
                if (OK.equalsIgnoreCase(waitResult)) {
                    log.info("轮询阶段拿到锁,Thread {} 拿到锁", id);
                    l.countDown();
                    throw new RuntimeException();
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
            boolean await = l.await(TimeUnit.SECONDS.toMillis(waitTime) - time, TimeUnit.MILLISECONDS);
            if (await) {
                log.info("拿锁阶段,Thread {} 拿到锁", thread.getId());
            } else {
                scheduledFuture.cancel(true);
            }
            return await;
        } catch (InterruptedException e) {
            log.error("FunnelRateLimiter InterruptedException {}", e);
            return false;
        }
    }

    /**
     * 释放锁
     *
     * @param lockKey
     */
    public void unlock(String lockKey) {
        lockKey = packageLockKey(lockKey);
        try (Jedis jedis = jedisPool.getResource()) {
            if (tryRelease(lockKey, jedis)) {
                log.info("释放锁成功 unlock {}", Thread.currentThread().getId());
                unparkSuccessor(lockKey);
            }
        }
    }

    private void unparkSuccessor(String lockKey) {
        ConcurrentLinkedDeque<Thread> threads = dequeMap.get(lockKey);
        if (null != threads) {
            Thread thread = threads.pollFirst();
            LockSupport.unpark(thread);
            log.info("唤醒 unparkSuccessor {}", thread.getId());
        }
    }

//    /**
//     * 释放锁
//     *
//     * @param lockKey
//     */
//    public void unlock(String lockKey) {
//        final String finalLockKey = packageLockKey(lockKey);
//        try (Jedis jedis = jedisPool.getResource()) {
//            Long del = jedis.del(finalLockKey);
//            if (del > 0) {
//                long currentThreadid = Thread.currentThread().getId();
//                log.info("FunnelRateLimiter 锁已经释放 Thread {}", currentThreadid);
//            }
//        } catch (Exception e) {
//            log.error("FunnelRateLimiter unlock error {}", e);
//        }
//    }

    private String packageLockKey(String lockKey) {
        return String.format("infra-monitor:distributedLock:%s", lockKey);
    }


    private Thread getExclusiveOwnerThread(String lockKey) {
        return null == exclusiveOwnerThread.get(lockKey) ? null : exclusiveOwnerThread.get(lockKey).thread;
    }

    private void initExclusiveOwnerThread(String lockKey) {
        log.info("initExclusiveOwnerThread start {}", Thread.currentThread().getId());
        ThreadLock threadLock = new ThreadLock();
        threadLock.addCount();
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
            if (!tryAcquire(jedis, 1, finalLockKey, leaseTime, waitTime) && addWaiter(finalLockKey) &&
                    acquireQueued(jedis, 1, finalLockKey, leaseTime, waitTime)) {
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
        return !(null == concurrentLinkedDeque || concurrentLinkedDeque.peekFirst() == Thread.currentThread());
    }


    /**
     * 尝试获得锁
     */
    protected final boolean tryAcquire(Jedis jedis, int acquires, final String lockKey, int leaseTime, long waitTime) {
        final Thread current = Thread.currentThread();
//        1.先在本地查询是否当前lockKey是否已经有一个锁
        ThreadLock threadLock = exclusiveOwnerThread.get(lockKey);
        log.info("先在本地查询是否当前lockKey是否已经有一个锁 {} {}", Thread.currentThread().getId(), threadLock);
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
            threadLock.addCount(acquires);
            return true;
        }
        return false;
    }

    /**
     * 尝试redis获取锁(原子操作)
     *
     * @param jedis
     * @param lockKey
     * @param leaseTime
     * @return
     */
    private final boolean jedisAcquire(Jedis jedis, final String lockKey, int leaseTime) {
        String result = jedis.set(lockKey, "1", "NX", "EX", leaseTime);
        if (OK.equalsIgnoreCase(result)) {
            log.info("锁为空直接拿到锁，Thread {} 拿到锁", Thread.currentThread().getId());
            return true;
        }
        return false;
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
        private volatile int count = 0;

        void subCount(int acquires) {
            addCount(acquires);
        }

        void addCount() {
            addCount(1);
        }

        void addCount(int acquires) {
            count += acquires;
        }
    }


    /**
     * 重复尝试获取锁
     *
     * @param jedis
     * @param acquires
     * @param lockKey
     * @param leaseTime
     * @param waitTime
     * @return
     */
    final boolean acquireQueued(Jedis jedis, int acquires, final String lockKey, int leaseTime, long waitTime) {
        for (; ; ) {
            if (!tryAcquire(jedis, acquires, lockKey, leaseTime, waitTime)) {
                parkAndCheckInterrupt();
            } else {
                return true;
            }
        }
    }

    boolean addWaiter(final String lockKey) {
        ConcurrentLinkedDeque<Thread> threadsDeque = dequeMap.get(lockKey);
        log.info("我被加入等候队列 addWaiter {} {}", Thread.currentThread().getId(), threadsDeque);
        if (null == threadsDeque) {
            ConcurrentLinkedDeque<Thread> deque = new ConcurrentLinkedDeque<>();
            deque.add(Thread.currentThread());
            dequeMap.put(lockKey, deque);
        } else {
            threadsDeque.add(Thread.currentThread());
        }
        return true;
    }


    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        log.info("我被睡眠了 parkAndCheckInterrupt {}", Thread.currentThread().getId());
        return true;
    }

//    public final boolean release(final String lockKey) {
//        if (tryRelease(lockKey)) {
//            unparkSuccessor();
//            return true;
//        }
//        return false;
//    }

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

    private void unparkSuccessor() {
        LockSupport.unpark(Thread.currentThread());
    }


}
