package com.example.procedure.infrastructure.context;

import com.example.procedure.processing.context.UeContextRepository;
import com.example.procedure.model.UEContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 鍩轰簬 Redis 鐨?UEContext 瀛樺偍瀹炵幇銆?
 *
 * 褰撳墠瀹氫綅锛?
 * 1. 杩欐槸 UEContext 浠撳偍鐨勬寮忓熀纭€璁炬柦瀹炵幇
 * 2. 璐熻矗鎶?UEContext 浠?JSON 褰㈠紡瀛樺彇鍒?Redis
 * 3. 涓氬姟灞傚彧渚濊禆 UeContextRepository锛屼笉鐩存帴渚濊禆杩欎釜瀹炵幇
 *
 * 杩欐牱鍋氱殑鎰忎箟锛?
 * 1. 涓庨」鐩綋鍓嶅叾浠?Redis 瀛樺偍椋庢牸淇濇寔涓€鑷?
 * 2. 閬垮厤棰濆瑕佹眰 RedisTemplate<String, Object> Bean
 * 3. 璁?context 鍖呬繚鐣欎笟鍔¤涔夛紝鍩虹璁炬柦缁嗚妭鏀惧湪 infrastructure.context
 */
@Repository
public class RedisUeContextStore implements UeContextRepository {

    /**
     * Redis key 鍓嶇紑銆?
     */
    private static final String PREFIX = "ue:ctx:";

    /**
     * Redis 瀛楃涓叉ā鏉裤€?
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final StringRedisTemplate redisTemplate;

    /**
     * JSON 搴忓垪鍖栧伐鍏枫€?
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final ObjectMapper objectMapper;

    /**
     * 鏋勯€?Redis UEContext 瀛樺偍瀹炵幇銆?
     *
     * @param redisTemplate Redis 瀛楃涓叉ā鏉?
     * @param objectMapper JSON 搴忓垪鍖栧伐鍏?
     */
    public RedisUeContextStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 鏍规嵁 UE ID 鍔犺浇涓婁笅鏂囥€?
     *
     * @param ueId UE 鏍囪瘑
     * @return UEContext锛涘鏋滀笉瀛樺湪鎴栬В鏋愬け璐ュ垯杩斿洖 null
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
     * 淇濆瓨 UEContext銆?
     *
     * @param ctx 褰撳墠涓婁笅鏂?
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
     * 鏋勯€?Redis key銆?
     *
     * @param ueId UE 鏍囪瘑
     * @return Redis key
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private String buildKey(String ueId) {
        return PREFIX + ueId;
    }
}
