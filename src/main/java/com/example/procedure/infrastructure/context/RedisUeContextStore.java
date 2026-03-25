package com.example.procedure.infrastructure.context;

import com.example.procedure.context.UeContextRepository;
import com.example.procedure.model.UEContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 Redis 的 UEContext 存储实现。
 *
 * 当前定位：
 * 1. 这是 UEContext 仓储的正式基础设施实现
 * 2. 负责把 UEContext 以 JSON 形式存取到 Redis
 * 3. 业务层只依赖 UeContextRepository，不直接依赖这个实现
 *
 * 这样做的意义：
 * 1. 与项目当前其他 Redis 存储风格保持一致
 * 2. 避免额外要求 RedisTemplate<String, Object> Bean
 * 3. 让 context 包保留业务语义，基础设施细节放在 infrastructure.context
 */
@Repository
public class RedisUeContextStore implements UeContextRepository {

    /**
     * Redis key 前缀。
     */
    private static final String PREFIX = "ue:ctx:";

    /**
     * Redis 字符串模板。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final StringRedisTemplate redisTemplate;

    /**
     * JSON 序列化工具。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final ObjectMapper objectMapper;

    /**
     * 构造 Redis UEContext 存储实现。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper JSON 序列化工具
     */
    public RedisUeContextStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据 UE ID 加载上下文。
     *
     * @param ueId UE 标识
     * @return UEContext；如果不存在或解析失败则返回 null
     */
    @Override
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    public UEContext findByUeId(String ueId) {
        if (ueId == null || ueId.isBlank()) {
            return null;
        }

        String json = redisTemplate.opsForValue().get(buildKey(ueId));
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, UEContext.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize UEContext for ueId=" + ueId, e);
        }
    }

    /**
     * 保存 UEContext。
     *
     * @param ctx 当前上下文
     */
    @Override
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    public void save(UEContext ctx) {
        if (ctx == null || ctx.getUeId() == null || ctx.getUeId().isBlank()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(ctx);
            redisTemplate.opsForValue().set(buildKey(ctx.getUeId()), json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize UEContext for ueId=" + ctx.getUeId(), e);
        }
    }

    /**
     * 构造 Redis key。
     *
     * @param ueId UE 标识
     * @return Redis key
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private String buildKey(String ueId) {
        return PREFIX + ueId;
    }
}
