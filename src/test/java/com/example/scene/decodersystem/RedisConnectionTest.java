package com.example.scene.decodersystem;


import com.example.procedure.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(classes = Application.class)
public class RedisConnectionTest {

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testRedisConnection() {
        try {
            // 直接用底层连接验证
            var connection = connectionFactory.getConnection();
            System.out.println("✅ Redis 连接成功: " + connection.ping());
            connection.close();

            // 也可以用 RedisTemplate 简单写入/读取
            redisTemplate.opsForValue().set("connect:test", "ok");
            String result = redisTemplate.opsForValue().get("connect:test");
            System.out.println("✅ Redis 写入/读取成功: " + result);
        } catch (Exception e) {
            System.err.println("❌ Redis 连接失败: " + e.getMessage());
        }
    }

    /**
     * ⚠️ 注意：会清空当前 Redis 实例所有 DB，慎用
     */
    @Test
    public void testFlushAllRedis() {
        var connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushAll();
            System.out.println("✅ 已执行 FLUSHALL，清空所有 Redis 数据库");
        } finally {
            connection.close();
        }
    }
}
