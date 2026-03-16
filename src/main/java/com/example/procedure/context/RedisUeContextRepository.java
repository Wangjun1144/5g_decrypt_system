package com.example.procedure.context;

import com.example.procedure.model.UEContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;

/**
 * UeContextRepository 的 Redis 实现。
 *
 * 设计说明：
 * - 将 Redis key 规则与序列化细节收口到仓储层
 * - 让上层服务不再直接操作 redisTemplate
 *
 * 当前阶段：
 * - 只先承接 UEContext 主体读写
 * - 未来再把 amf/ran/crnti 等映射逐步并入独立 repository 或补充接口
 */
@Repository
public class RedisUeContextRepository implements UeContextRepository {

    private static final String UE_CTX_KEY_PREFIX = "ue:ctx:";

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUeContextRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UEContext findByUeId(String ueId) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(redisKeyForCtx(ueId));
        if (map == null || map.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(map, UEContext.class);
    }

    @Override
    public void save(UEContext context) {
        try {
            Map<String, String> map = objectMapper.convertValue(context, Map.class);
            redisTemplate.opsForHash().putAll(redisKeyForCtx(context.getUeId()), map);
            redisTemplate.expire(redisKeyForCtx(context.getUeId()), TTL);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Failed to serialize UEContext", e);
        }
    }

    private String redisKeyForCtx(String ueId) {
        return UE_CTX_KEY_PREFIX + ueId;
    }
}