package com.example.procedure.infrastructure.binding;

import com.example.procedure.processing.binding.resolve.BindingStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的绑定状态存储实现。
 *
 * 当前定位：
 * 1. 这是 binding 阶段正式的基础设施状态实现
 * 2. 负责维护 ngapId / rntiType 与 ueId 之间的映射关系
 * 3. 业务层不应直接关心 Redis key 细节
 */
@Component
public class RedisBindingStateStore implements BindingStateStore {

    /**
     * ranUeNgapId -> ueId 映射的 Redis key 前缀。
     */
    private static final String MAP_RAN_UE_KEY_PREFIX = "ue:map:ran:";

    /**
     * rntiType -> ueId 映射的 Redis key 前缀。
     */
    private static final String MAP_RNTI_TYPE_UE_KEY_PREFIX = "ue:map:rntiType:";

    /**
     * ueId 反向索引的 Redis key 前缀。
     */
    private static final String UE_IDX_PREFIX = "ue:idx:ue:";

    /**
     * 当前绑定映射的 TTL。
     */
    private static final Duration REDIS_TTL = Duration.ofHours(1);

    /**
     * Redis 字符串模板。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * ngapId -> ueId 本地热点缓存。
     */
    private final Map<String, String> ngapToUeCache = new ConcurrentHashMap<>();

    /**
     * rntiType -> ueId 本地热点缓存。
     */
    private final Map<String, String> rntiTypeToUeCache = new ConcurrentHashMap<>();

    /**
     * 构造 Redis 绑定状态存储实现。
     *
     * @param redisTemplate Redis 字符串模板
     */
    public RedisBindingStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 根据 ngapId 查找 ueId。
     *
     * @param ngapId ngapId
     * @return 已绑定的 ueId；不存在则返回 null
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public String lookupUeIdByNgapId(String ngapId) {
        if (isEmpty(ngapId)) {
            return null;
        }

        return readForwardBinding(
                ngapId,
                ngapToUeCache,
                redisKeyForRanMap(ngapId)
        );
    }

    /**
     * 根据 rntiType 查找 ueId。
     *
     * @param rntiType rntiType
     * @return 已绑定的 ueId；不存在则返回 null
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public String lookupUeIdByRntiType(String rntiType) {
        if (isEmpty(rntiType)) {
            return null;
        }

        return readForwardBinding(
                rntiType,
                rntiTypeToUeCache,
                redisKeyForRntiTypeMap(rntiType)
        );
    }

    /**
     * 判断某个 ueId 是否还没有绑定 ngapId。
     *
     * @param ueId ueId
     * @return true 表示未绑定
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public boolean isUeNgapUnbound(String ueId) {
        if (isEmpty(ueId)) {
            return false;
        }
        return isBlankValue(redisTemplate.opsForValue().get(redisKeyForUeRanIdx(ueId)));
    }

    /**
     * 判断某个 ueId 是否还没有绑定 rntiType。
     *
     * @param ueId ueId
     * @return true 表示未绑定
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public boolean isUeRntiUnbound(String ueId) {
        if (isEmpty(ueId)) {
            return false;
        }
        return isBlankValue(redisTemplate.opsForValue().get(redisKeyForUeRntiIdx(ueId)));
    }

    /**
     * 判断某个 ngapId 是否尚未绑定 ueId。
     *
     * @param ngapId ngapId
     * @return true 表示尚未绑定
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public boolean isNgapUnbound(String ngapId) {
        if (isEmpty(ngapId)) {
            return false;
        }

        return isForwardBindingAbsent(
                ngapId,
                ngapToUeCache,
                redisKeyForRanMap(ngapId)
        );
    }

    /**
     * 判断某个 rntiType 是否尚未绑定 ueId。
     *
     * @param rntiType rntiType
     * @return true 表示尚未绑定
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public boolean isRntiTypeUnbound(String rntiType) {
        if (isEmpty(rntiType)) {
            return false;
        }

        return isForwardBindingAbsent(
                rntiType,
                rntiTypeToUeCache,
                redisKeyForRntiTypeMap(rntiType)
        );
    }

    /**
     * 建立 ngapId -> ueId 映射。
     *
     * @param ngapId ngapId
     * @param ueId ueId
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public void bindNgapIdToUe(String ngapId, String ueId) {
        if (isEmpty(ngapId) || isEmpty(ueId)) {
            return;
        }

        writeForwardAndReverseBinding(
                ngapId,
                ueId,
                ngapToUeCache,
                redisKeyForRanMap(ngapId),
                redisKeyForUeRanIdx(ueId)
        );
    }

    /**
     * 建立 rntiType -> ueId 映射。
     *
     * @param rntiType rntiType
     * @param ueId ueId
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    @Override
    public void bindRntiTypeToUe(String rntiType, String ueId) {
        if (isEmpty(rntiType) || isEmpty(ueId)) {
            return;
        }

        writeForwardAndReverseBinding(
                rntiType,
                ueId,
                rntiTypeToUeCache,
                redisKeyForRntiTypeMap(rntiType),
                redisKeyForUeRntiIdx(ueId)
        );
    }

    /**
     * 读取正向绑定。
     *
     * @param key 本地缓存 key
     * @param localCache 本地缓存
     * @param redisKey Redis key
     * @return 已绑定值；不存在则返回 null
     */
    private String readForwardBinding(
            String key,
            Map<String, String> localCache,
            String redisKey
    ) {
        String cached = localCache.get(key);
        if (!isBlankValue(cached)) {
            return cached;
        }

        String redisValue = redisTemplate.opsForValue().get(redisKey);
        if (!isBlankValue(redisValue)) {
            localCache.put(key, redisValue);
        }

        return normalize(redisValue);
    }

    /**
     * 判断正向绑定是否不存在。
     *
     * @param key 本地缓存 key
     * @param localCache 本地缓存
     * @param redisKey Redis key
     * @return true 表示不存在
     */
    private boolean isForwardBindingAbsent(
            String key,
            Map<String, String> localCache,
            String redisKey
    ) {
        String cached = localCache.get(key);
        if (!isBlankValue(cached)) {
            return false;
        }

        String redisValue = redisTemplate.opsForValue().get(redisKey);
        if (!isBlankValue(redisValue)) {
            localCache.put(key, redisValue);
            return false;
        }

        return true;
    }

    /**
     * 写入一组正向映射和反向索引。
     *
     * @param sourceKey 正向 key
     * @param ueId ueId
     * @param localCache 本地缓存
     * @param forwardRedisKey 正向 Redis key
     * @param reverseRedisKey 反向 Redis key
     */
    private void writeForwardAndReverseBinding(
            String sourceKey,
            String ueId,
            Map<String, String> localCache,
            String forwardRedisKey,
            String reverseRedisKey
    ) {
        localCache.put(sourceKey, ueId);
        redisTemplate.opsForValue().set(forwardRedisKey, ueId, REDIS_TTL);
        redisTemplate.opsForValue().set(reverseRedisKey, sourceKey, REDIS_TTL);
    }

    /**
     * 构造 ngapId -> ueId 的 Redis key。
     *
     * @param ngapId ngapId
     * @return Redis key
     */
    private String redisKeyForRanMap(String ngapId) {
        return MAP_RAN_UE_KEY_PREFIX + ngapId;
    }

    /**
     * 构造 rntiType -> ueId 的 Redis key。
     *
     * @param rntiType rntiType
     * @return Redis key
     */
    private String redisKeyForRntiTypeMap(String rntiType) {
        return MAP_RNTI_TYPE_UE_KEY_PREFIX + rntiType;
    }

    /**
     * 构造 ue -> ran 反向索引 Redis key。
     *
     * @param ueId ueId
     * @return Redis key
     */
    private String redisKeyForUeRanIdx(String ueId) {
        return UE_IDX_PREFIX + ueId + ":ran";
    }

    /**
     * 构造 ue -> rnti 反向索引 Redis key。
     *
     * @param ueId ueId
     * @return Redis key
     */
    private String redisKeyForUeRntiIdx(String ueId) {
        return UE_IDX_PREFIX + ueId + ":rntiType";
    }

    /**
     * 判断字符串是否为空。
     *
     * @param s 输入字符串
     * @return true 表示为空
     */
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 判断 Redis 值是否为空白。
     *
     * @param value Redis 值
     * @return true 表示为空白
     */
    private static boolean isBlankValue(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 规范化字符串。
     *
     * @param s 输入字符串
     * @return 规范化后的字符串；为空则返回 null
     */
    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}
