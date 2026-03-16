package com.example.procedure.service;

import com.example.procedure.context.UeContextUpdateDispatcher;
import com.example.procedure.context.UeContextUpdateSupport;
import com.example.procedure.keyderivation.KeyDerivationNative;
import com.example.procedure.parser.*;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class UEContextService {
    private static final String UE_CTX_KEY_PREFIX      = "ue:ctx:";
    private static final String MAP_AMF_UE_KEY_PREFIX  = "ue:map:amf:";
    private static final String MAP_RAN_UE_KEY_PREFIX  = "ue:map:ran:";
    private static final String MAP_CRNTI_KEY_PREFIX   = "ue:map:crnti:";

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UeContextUpdateDispatcher updateDispatcher;
    private final UeContextUpdateSupport updateSupport;

    public UEContextService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            UeContextUpdateDispatcher updateDispatcher,
                            UeContextUpdateSupport updateSupport) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.updateDispatcher = updateDispatcher;
        this.updateSupport = updateSupport;
    }

    private String redisKeyForCtx(String ueId) {
        return UE_CTX_KEY_PREFIX + ueId;
    }

    private String redisKeyForAmfMap(String amfUeId) {
        return MAP_AMF_UE_KEY_PREFIX + amfUeId;
    }

    private String redisKeyForRanMap(String ranUeId) {
        return MAP_RAN_UE_KEY_PREFIX + ranUeId;
    }

    private String redisKeyForCrntiMap(String cellId, String crnti) {
        return MAP_CRNTI_KEY_PREFIX + cellId + ":" + crnti;
    }

    public UEContext getContext(String ueId){
        Map<Object, Object> map = redisTemplate.opsForHash().entries(redisKeyForCtx(ueId));
        if (map == null || map.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(map, UEContext.class);
    }

    public void saveContext(UEContext ctx){
        try{
            Map<String, String> map = objectMapper.convertValue(ctx, Map.class);
            redisTemplate.opsForHash().putAll(redisKeyForCtx(ctx.getUeId()), map);
            redisTemplate.expire(redisKeyForCtx(ctx.getUeId()), TTL);
        }catch (IllegalArgumentException e){
            throw new RuntimeException("Failed to serialize UEContext", e);
        }
    }

    public UEContext getOrCreate(String ueId) {
        UEContext ctx = getContext(ueId);
        if (ctx == null) {
            ctx = new UEContext();
            ctx.setUeId(ueId);
            ctx.setAttachState("INIT");
        }
        return ctx;
    }

    // ============== 核心改造：根据 6 条关键消息更新 UE 上下文 ==============

    /**
     * 根据 IA 流程关键消息更新 UEContext
     *
     * 本次重构后：
     * - UEContextService 只保留“加载上下文 / 保存上下文 / 分发更新规则”的职责
     * - 具体每种消息如何更新，由各个 UeContextUpdater 单独负责
     */
    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        String ueId = msg.getUeId();
        if (ueId == null || ueId.isEmpty()) {
            return;
        }

        UEContext ctx = getOrCreate(ueId);

        // 将“按消息类型更新上下文”的规则交给 updater 分发器处理
        updateDispatcher.dispatch(msg, ctx, procedureId, updateSupport);

        // 最后统一落库，保持原行为
        saveContext(ctx);
    }

    private int parseAlgNo123(String s) {
        if (s == null || s.isEmpty()) return 1;
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 1 && v <= 3) ? v : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private int mapAlgIdentity(int algNo) {
        switch (algNo) {
            case 2: return 0x02; // NEA2_NIA2
            case 3: return 0x03; // NEA3_NIA3
            case 1:
            default: return 0x01; // NEA1_NIA1
        }
    }

    /**
     * 从一条消息的 NAS 列表中，挑出 SecurityModeCommand 对应的 NAS 记录：
     *  - 优先 mmMessageType = 0x5d
     *  - 如果没有，就返回第一个 NAS（兜底）
     */
    private NasInfo pickNasSecurityMode(List<NasInfo> nasList) {
        if (nasList == null || nasList.isEmpty()) {
            return null;
        }
        // 1) 先按 mmType=0x5d 精确匹配
        for (NasInfo nas : nasList) {
            if ("0x5d".equalsIgnoreCase(nas.getMmMessageType())) {
                return nas;
            }
        }
        // 2) 没匹配到就随便拿第一条，当兜底
        return nasList.get(0);
    }

    private NgapInfo pickNAGPSecurityMode(List<NgapInfo> nasList) {
        if (nasList == null || nasList.isEmpty()) {
            return null;
        }
        // 2) 没匹配到就随便拿第一条，当兜底
        return nasList.get(0);
    }
}
