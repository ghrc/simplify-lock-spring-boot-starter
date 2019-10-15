package cn.xxywithpq.delay;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class DelayUtil<E> {

    /**
     * 队列最大个数
     */
    final int maxSize;
    /**
     * 每分钟最大处理数
     */
    final int maxSizePerMinutes;
    ReentrantLock putLock = new ReentrantLock();
    ReentrantLock takeLock = new ReentrantLock();
    Condition notEmpty = takeLock.newCondition();
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService executorService;
    private ExecutorService service = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
        protected final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "pool-limit-util-take-thread-" + toString() + "-" + this.threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });
    private volatile transient Node<E> head;
    private volatile transient Node<E> tail;


    /**
     * 链表个数
     */
    private volatile transient AtomicInteger count = new AtomicInteger(0);
    /**
     * 每分钟处理个数限制
     */
    private volatile transient AtomicInteger limitCount = new AtomicInteger(0);

    /**
     * @param seconds      周期时间
     * @param maxHandleNum 队列最大个数
     * @param maxQueueSize 每周期时间内最大处理数
     */
    public DelayUtil(int seconds, int maxHandleNum, int maxQueueSize, Consumer<E> consumer) {
        synchronized (this) {
            head = tail = new Node(null);
            maxSizePerMinutes = maxHandleNum;
            maxSize = maxQueueSize;
            executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                protected final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "pool-limit-util-timekeeper-thread-" + toString() + "-" + this.threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });
            scheduledFuture = executorService.scheduleAtFixedRate(() -> limitCount.set(0), seconds, seconds, TimeUnit.SECONDS);

            for (int i = 0; i < 1; i++) {
                service.submit(() -> {
                    while (true) {
                        try {
                            E take = take();
                            log.info("LimitUtil Thread: {} ;result {}", Thread.currentThread().getName(), take);
                            consumer.accept(take);
                        } catch (InterruptedException e) {
                            log.warn("LimitUtil stop take");
                            return;
                        } catch (Exception e) {
                            log.error("LimitUtil error {}", e);
                            return;
                        }
                    }
                });
            }
        }
    }

    public void put(E e) {
        final AtomicInteger count = this.count;
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        int size;
        try {
//            队列已满，不再加入等待队列
            while (count.get() == maxSize) {
                log.warn("LimitUtil funnelRate full {}", e);
                return;
            }
            enqueue(e);
            size = count.incrementAndGet();
            log.info("LimitUtil Thread {} add  {} size {}", Thread.currentThread().getId(), e, size);
            if ((size > 0 && limitCount.get() < maxSizePerMinutes)) {
                signalNotEmpty();
            }
        } finally {
            putLock.unlock();
        }
    }

    public E take() throws InterruptedException {
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        int size;
        try {
            while (limitCount.get() >= maxSizePerMinutes) {
                try {
                    long delay = scheduledFuture.getDelay(TimeUnit.NANOSECONDS);
                    if (delay > 0) {
                        notEmpty.awaitNanos(delay);
                    }
                } catch (InterruptedException e) {
                    throw e;
                }
            }

            while (count.get() == 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                notEmpty.await();
            }
            E result = dequeue();
            size = count.decrementAndGet();
            if (size > 0) {
                notEmpty.signal();
            }
            limitCount.incrementAndGet();
            return result;
        } catch (InterruptedException e) {
            throw e;
        } finally {
            takeLock.unlock();
        }
    }

    private void signalNotEmpty() {
        takeLock.lock();
        this.notEmpty.signal();
        takeLock.unlock();
    }

//    private void signalNotFull() {
//        putLock.lock();
//        this.notFull.signal();
//        putLock.unlock();
//    }

    private void enqueue(E e) {
        Node<E> node = new Node(e);
        tail.next = node;
        tail = tail.next;
    }

    private E dequeue() {
        Node<E> h = head;
        Node<E> first = h.next;
//            help gc
        h.next = h;

        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    public void close() {
        service.shutdownNow();
        executorService.shutdownNow();
    }

    private class Node<E> {
        volatile E item;
        volatile Node<E> next;

        Node(E x) {
            item = x;
        }
    }
}