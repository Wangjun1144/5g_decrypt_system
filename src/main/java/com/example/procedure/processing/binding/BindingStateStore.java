package com.example.procedure.processing.binding;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 绑定状态存储。
 *
 * 当前职责：
 * 1. 管理 ngapId -> ueId / rntiType -> ueId 的正向映射
 * 2. 管理 ueId -> ngapId / ueId -> rntiType 的反向索引
 * 3. 提供当前绑定阶段所需的“查询 / 未绑定判断 / 执行绑定”语义
 * 4. 使用本地热点缓存减少重复 Redis 读取
 *
 * 第 13 小步的重构重点：
 * - 不改变现有 Redis key 结构
 * - 不改变 TTL 策略
 * - 不改变当前缓存模型
 * - 只把状态访问整理成更清晰的语义方法
 *
 * 当前阶段定位：
 * - 它是绑定阶段的状态访问门面
 * - BindingResolver 不应该再关心 Redis key 细节
 * - 后续若要演进到更独立的状态服务，这一层会是重要过渡点
 */
@Component
public class BindingStateStore {

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
     *
     * 当前会派生出：
     * - ue:idx:ue:{ueId}:ran
     * - ue:idx:ue:{ueId}:rntiType
     */
    private static final String UE_IDX_PREFIX = "ue:idx:ue:";

    /**
     * 当前阶段绑定映射的 TTL。
     *
     * 仍保持你现有实现中的 1 小时，
     * 不在这一小步里改变状态生命周期策略。
     */
    private static final Duration REDIS_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    /**
     * 本地热点缓存：
     * - ngapId -> ueId
     * - rntiType -> ueId
     *
     * 当前只缓存正向映射，
     * 这样可以在绑定密集场景下减少 Redis 读压力。
     */
    private final Map<String, String> ngapToUeCache = new ConcurrentHashMap<>();
    private final Map<String, String> rntiTypeToUeCache = new ConcurrentHashMap<>();

    public BindingStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 根据 ngapId 查询当前已绑定的 ueId。
     *
     * 查询顺序：
     * 1. 本地 cache
     * 2. Redis
     *
     * 如果 Redis 命中，会顺便回填本地 cache。
     */
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
     * 根据 rntiType 查询当前已绑定的 ueId。
     */
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
     * 判断某个 ueId 当前是否还没有绑定 ngapId。
     *
     * 当前仍以反向索引是否存在作为判断依据。
     */
    public boolean isUeNgapUnbound(String ueId) {
        if (isEmpty(ueId)) {
            return false;
        }
        return isBlankValue(redisTemplate.opsForValue().get(redisKeyForUeRanIdx(ueId)));
    }

    /**
     * 判断某个 ueId 当前是否还没有绑定 rntiType。
     */
    public boolean isUeRntiUnbound(String ueId) {
        if (isEmpty(ueId)) {
            return false;
        }
        return isBlankValue(redisTemplate.opsForValue().get(redisKeyForUeRntiIdx(ueId)));
    }

    /**
     * 判断某个 ngapId 当前是否尚未绑定到任何 ueId。
     *
     * 当前判断顺序保持不变：
     * 1. 本地 cache
     * 2. Redis
     */
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
     * 判断某个 rntiType 当前是否尚未绑定到任何 ueId。
     */
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
     * 建立 ngapId -> ueId 正向映射，
     * 同时建立 ueId -> ngapId 反向索引。
     *
     * 当前行为保持不变：
     * - 更新本地 cache
     * - 更新 Redis
     * - 正向/反向索引共用同一 TTL
     */
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
     * 建立 rntiType -> ueId 正向映射，
     * 同时建立 ueId -> rntiType 反向索引。
     */
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
     * 读取正向绑定：
     * key 既用于 cache key，也用于语义上的“待查索引值”。
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
     * 判断某个正向绑定是否不存在。
     *
     * 当前规则：
     * - cache 命中则视为已绑定
     * - Redis 命中则视为已绑定，并回填 cache
     * - 两者都没有才视为未绑定
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
     * 写入一组正向映射与反向索引。
     *
     * 这是当前绑定动作的公共模板方法：
     * - 更新本地 cache
     * - 写正向映射
     * - 写反向索引
     * - 两个 Redis key 都设置相同 TTL
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

    private String redisKeyForRanMap(String ngapId) {
        return MAP_RAN_UE_KEY_PREFIX + ngapId;
    }

    private String redisKeyForRntiTypeMap(String rntiType) {
        return MAP_RNTI_TYPE_UE_KEY_PREFIX + rntiType;
    }

    private String redisKeyForUeRanIdx(String ueId) {
        return UE_IDX_PREFIX + ueId + ":ran";
    }

    private String redisKeyForUeRntiIdx(String ueId) {
        return UE_IDX_PREFIX + ueId + ":rntiType";
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isBlankValue(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}
