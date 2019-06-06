package cn.xxywithpq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@SpringBootApplication
@EnableConfigurationProperties
public class SimplifyLockSpringBootStarterApplication {

    @Autowired
    RedisProperties redisProperties;

    public static void main(String[] args) {
        SpringApplication.run(SimplifyLockSpringBootStarterApplication.class, args);
    }

    @Bean
    public JedisPool redisPoolFactory() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(20);
        jedisPoolConfig.setBlockWhenExhausted(true);
        jedisPoolConfig.setMaxWaitMillis(redisProperties.getTimeout());
        JedisPool jedisPool = new JedisPool(jedisPoolConfig, redisProperties.getHost(), Integer.valueOf(redisProperties.getPort()), redisProperties.getTimeout(), redisProperties.getPassword(), redisProperties.getDatabase());
        return jedisPool;
    }

}
