package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.support.logging.SignalingDumpWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Paths;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@RequiresRedis
class RedisConnectionTest {

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testRedisConnection() {
        var connection = connectionFactory.getConnection();
        try {
            System.out.println("Redis connection succeeded: " + connection.ping());
            redisTemplate.opsForValue().set("connect:test", "ok");
            String result = redisTemplate.opsForValue().get("connect:test");
            System.out.println("Redis write/read succeeded: " + result);
        } finally {
            connection.close();
        }
    }

    /**
     * This is a destructive integration test and only runs when explicitly enabled.
     */
    @Test
    @EnabledIfSystemProperty(named = "test.redis.flush.enabled", matches = "true")
    void testFlushAllRedis() {
        var connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushAll();
            System.out.println("Executed FLUSHALL and cleared the connected Redis instance.");
            SignalingDumpWriter.deleteLogDirectory(Paths.get("logs"));
            System.out.println("Deleted the logs directory.");
        } finally {
            connection.close();
        }
    }
}
