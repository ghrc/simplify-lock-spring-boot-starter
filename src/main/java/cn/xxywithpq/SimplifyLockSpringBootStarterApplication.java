package cn.xxywithpq;

import cn.xxywithpq.lock.funnelRete.properties.RedissonProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@SpringBootApplication
@EnableConfigurationProperties
public class SimplifyLockSpringBootStarterApplication {
    @Autowired
    RedissonProperties redissonProperties;

    public static void main(String[] args) {
        SpringApplication.run(SimplifyLockSpringBootStarterApplication.class, args);
    }

    @Bean
    public JedisPool redisPoolFactory() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(20);
        jedisPoolConfig.setBlockWhenExhausted(true);
        jedisPoolConfig.setMaxWaitMillis(redissonProperties.getTimeout());
        JedisPool jedisPool = new JedisPool(jedisPoolConfig, redissonProperties.getHost(), Integer.valueOf(redissonProperties.getPort()), redissonProperties.getTimeout(), redissonProperties.getPassword(), null != redissonProperties.getDatabase() ? redissonProperties.getDatabase() : 0);
        return jedisPool;
    }

}
