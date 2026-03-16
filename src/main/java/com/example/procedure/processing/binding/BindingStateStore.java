package com.example.procedure.processing.binding;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 绑定状态存储。
 *
 * 职责：
 * 1. 管理 ngapId -> ueId / rntiType -> ueId 的当前映射
 * 2. 管理 ueId 的反向索引（是否已绑定某种索引）
 * 3. 本地 cache 加速热点读取
 *
 * 说明：
 * - 当前阶段仍沿用 Redis + 本地 cache
 * - 只是把这些“状态读写细节”从 UeIdBinder 主逻辑里拆出来
 */
@Component
public class BindingStateStore {

    private static final String MAP_RAN_UE_KEY_PREFIX = "ue:map:ran:";
    private static final String MAP_RNTI_TYPE_UE_KEY_PREFIX = "ue:map:rntiType:";
    private static final String UE_IDX_PREFIX = "ue:idx:ue:";
    private static final Duration REDIS_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    /** 本地热点 cache */
    private final Map<String, String> ngapToUeCache = new ConcurrentHashMap<>();
    private final Map<String, String> rntiTypeToUeCache = new ConcurrentHashMap<>();

    public BindingStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String lookupUeIdByNgapId(String ngapId) {
        if (isEmpty(ngapId)) {
            return null;
        }

        String cached = ngapToUeCache.get(ngapId);
        if (!isEmpty(cached)) {
            return cached;
        }

        String redisValue = redisTemplate.opsForValue().get(redisKeyForRanMap(ngapId));
        if (!isEmpty(redisValue)) {
            ngapToUeCache.put(ngapId, redisValue);
        }
        return normalize(redisValue);
    }

    public String lookupUeIdByRntiType(String rntiType) {
        if (isEmpty(rntiType)) {
            return null;
        }

        String cached = rntiTypeToUeCache.get(rntiType);
        if (!isEmpty(cached)) {
            return cached;
        }

        String redisValue = redisTemplate.opsForValue().get(redisKeyForRntiTypeMap(rntiType));
        if (!isEmpty(redisValue)) {
            rntiTypeToUeCache.put(rntiType, redisValue);
        }
        return normalize(redisValue);
    }

    public boolean isUeNgapUnbound(String ueId) {
        if (isEmpty(ueId)) {
            return false;
        }
        String v = redisTemplate.opsForValue().get(redisKeyForUeRanIdx(ueId));
        return isEmpty(v);
    }

    public boolean isUeRntiUnbound(String ueId) {
        if (isEmpty(ueId)) {
            return false;
        }
        String v = redisTemplate.opsForValue().get(redisKeyForUeRntiIdx(ueId));
        return isEmpty(v);
    }

    public boolean isNgapUnbound(String ngapId) {
        if (isEmpty(ngapId)) {
            return false;
        }

        String cached = ngapToUeCache.get(ngapId);
        if (!isEmpty(cached)) {
            return false;
        }

        String redisValue = redisTemplate.opsForValue().get(redisKeyForRanMap(ngapId));
        if (!isEmpty(redisValue)) {
            ngapToUeCache.put(ngapId, redisValue);
            return false;
        }
        return true;
    }

    public boolean isRntiTypeUnbound(String rntiType) {
        if (isEmpty(rntiType)) {
            return false;
        }

        String cached = rntiTypeToUeCache.get(rntiType);
        if (!isEmpty(cached)) {
            return false;
        }

        String redisValue = redisTemplate.opsForValue().get(redisKeyForRntiTypeMap(rntiType));
        if (!isEmpty(redisValue)) {
            rntiTypeToUeCache.put(rntiType, redisValue);
            return false;
        }
        return true;
    }

    public void bindNgapIdToUe(String ngapId, String ueId) {
        if (isEmpty(ngapId) || isEmpty(ueId)) {
            return;
        }

        ngapToUeCache.put(ngapId, ueId);
        redisTemplate.opsForValue().set(redisKeyForRanMap(ngapId), ueId, REDIS_TTL);
        redisTemplate.opsForValue().set(redisKeyForUeRanIdx(ueId), ngapId, REDIS_TTL);
    }

    public void bindRntiTypeToUe(String rntiType, String ueId) {
        if (isEmpty(rntiType) || isEmpty(ueId)) {
            return;
        }

        rntiTypeToUeCache.put(rntiType, ueId);
        redisTemplate.opsForValue().set(redisKeyForRntiTypeMap(rntiType), ueId, REDIS_TTL);
        redisTemplate.opsForValue().set(redisKeyForUeRntiIdx(ueId), rntiType, REDIS_TTL);
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

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}