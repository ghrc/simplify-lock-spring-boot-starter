package cn.xxywithpq.delay;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class DelayUtilFactory<E> {


    LRU<String, DelayUtil> map = new LRU<>(1, 0.75f);

    public synchronized DelayUtil getInstance(String key, int seconds, int maxHandleNum, int maxQueueSize, Consumer<E> consumer) {
        DelayUtil delayUtil;
        if (null != (delayUtil = map.get(key))) {
            return delayUtil;
        } else {
            delayUtil = new DelayUtil(seconds, maxHandleNum, maxQueueSize, consumer);
            map.put(key, delayUtil);
            return delayUtil;
        }

    }

    class LRU<K, V> extends LinkedHashMap<K, V> {

        // 保存缓存的容量
        private int capacity;

        public LRU(int capacity, float loadFactor) {
            super(capacity, loadFactor, true);
            this.capacity = capacity;
        }

        /**
         * 重写removeEldestEntry()方法设置何时移除旧元素
         *
         * @param eldest
         * @return
         */
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            // 当元素个数大于了缓存的容量, 就移除元素
            if (size() > this.capacity) {
                DelayUtil value = (DelayUtil) eldest.getValue();
                value.close();
            }
            return size() > this.capacity;
        }
    }
}