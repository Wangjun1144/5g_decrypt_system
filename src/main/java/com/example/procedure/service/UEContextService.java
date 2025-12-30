package com.example.procedure.service;

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
    private static final String MAP_CRNTI_KEY_PREFIX   = "ue:map:crnti:"; // 示例：加 cellId 再拼 crnti

    private static final Duration TTL = Duration.ofHours(1); // DEMO：先给 1 小时

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UEContextService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
     * 根据单条信令消息更新 UE 上下文。
     *
     * 约定 msgType（和你前面 parseAndMerge 改名保持一致）：
     *  1) "RRCSetupComplete"
     *  2) "Initial UE Message"
     *  3) "Nausf_UEAuthentication_Authenticate Response"
     *  4) "NAS SecurityModeCommand"
     *  5) "Initial Context Setup Request"
     *  6) "RRC SecurityModeCommand"
     */
    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        String ueId = msg.getUeId();
        if (ueId == null || ueId.isEmpty()) {
            // 没有 ueId 的包这里先忽略（或者打日志）
            return;
        }

        UEContext ctx = getOrCreate(ueId);
        // 如果 UEContext 里有 procedureId / lastProcedureId 字段，也可以顺便记一下
        // ctx.setLastProcedureId(procedureId);

        String type = msg.getMsgType();

        // 1) RRCSetupComplete：保存 C-RNTI
        if ("RRCSetupComplete".equals(type)) {
            MacInfo mac = msg.getMacInfo();
            if (mac != null) {
                String crnti = mac.getRnti();
                if (crnti != null && !crnti.isEmpty()) {
                    ctx.setCrnti(crnti);
                    // attachState 也可以顺便推进一下
                    ctx.setAttachState("RRC_SETUP_COMPLETE");
                    // TODO: 如果以后有小区 ID，可以在这里做 cellId+crnti 反查映射
                    // redisTemplate.opsForValue().set(redisKeyForCrntiMap(cellId, crnti), ueId, TTL);
                }
            }
        }

        // 2) Initial UE Message：保存 RAN_UE_NGAP_ID
        else if ("Initial UE Message".equals(type)) {
            NgapInfo ngap = pickNAGPSecurityMode(msg.getNgapInfoList());
            if (ngap != null) {
                String ranUeNgapId = ngap.getRanUeNgapId();
                if (ranUeNgapId != null && !ranUeNgapId.isEmpty()) {
                    ctx.setRanUeNgapId(ranUeNgapId);
                    ctx.setAttachState("NGAP_INITIAL_UE_MESSAGE");
                    // 反查映射（RAN_UE_NGAP_ID -> ueId）也可以建起来，后面通过 N2 消息找 UE
                    redisTemplate.opsForValue().set(
                            redisKeyForRanMap(ranUeNgapId), ueId, TTL
                    );
                }
            }
        }

        // 3) Nausf_UEAuthentication_Authenticate Response：保存 KSEAF
        else if ("Nausf_UEAuthentication_Authenticate Response".equals(type)) {
            NUARInfo nuar = msg.getNuarInfo();
            if (nuar != null) {
                String kseaf = nuar.getKseafHex();
                String imsi  = nuar.getImsi(); // 这里就是 kamf 函数要的 supi 参数（纯数字）

                if (kseaf == null || kseaf.isEmpty()) {
                    // 有的结构里字段可能叫 kseaf，这里兼容一下
                    kseaf = nuar.getKseafHex();
                }

                if (imsi != null && !imsi.isEmpty()) {
                    ctx.setSupi(imsi);
                }

                if (kseaf != null && !kseaf.isEmpty()) {
                    ctx.setKSeaf(kseaf);
                    ctx.setAttachState("AUTH_COMPLETED");
                }

                // 推导 KAMF：supi 用 imsi（001010...）
                if ((imsi != null && !imsi.isEmpty()) && (kseaf != null && !kseaf.isEmpty())) {
                    byte[] abba = new byte[]{0x00, 0x00};
                    String kamf = KeyDerivationNative.kamfFromKseaf(imsi, abba, kseaf);
                    if (kamf != null && !kamf.isEmpty()) {
                        ctx.setKAmf(kamf);
                    }
                }

            }
        }

        // 4) NAS SecurityModeCommand：保存 NAS 加密/完整性算法
        else if ("NAS SecurityModeCommand".equals(type)) {
            NasInfo smcNas = pickNasSecurityMode(msg.getNasList());
            if (smcNas != null) {

                // 1) 算法编号（字符串 "1"/"2"/"3"）
                String nasIntAlgStr = smcNas.getNas_integrityProtAlgorithm(); // 完保算法号
                String nasEncAlgStr = smcNas.getNas_cipheringAlgorithm();     // 加密算法号

                if (nasIntAlgStr != null && !nasIntAlgStr.isEmpty()) {
                    ctx.setNasIntAlg(nasIntAlgStr);
                }
                if (nasEncAlgStr != null && !nasEncAlgStr.isEmpty()) {
                    ctx.setNasCipherAlg(nasEncAlgStr);
                }

                // 2) 获取 KAMF（优先 ctx，没有就从 Redis 再拉一次）
                String kamf = ctx.getKAmf();
                if (kamf == null || kamf.isEmpty()) {
                    UEContext latest = getContext(ctx.getUeId());
                    if (latest != null) {
                        kamf = latest.getKAmf();
                        if (ctx.getKAmf() == null) ctx.setKAmf(kamf);
                    }
                }

                // 没有 KAMF 无法推导
                if (kamf == null || kamf.isEmpty()) {
                    ctx.setAttachState("NAS_SMC");
                    saveContext(ctx);
                    return;
                }

                // 3) 解析并映射 algorithm_identity
                int encNo = parseAlgNo123(nasEncAlgStr); // 1/2/3
                int intNo = parseAlgNo123(nasIntAlgStr); // 1/2/3

                int encAlgIdentity = mapAlgIdentity(encNo); // NEA*_NIA*
                int intAlgIdentity = mapAlgIdentity(intNo); // NEA*_NIA*

                // 4) 推导 NAS ENC key：N_NAS_ENC_ALG = 0x01
                String kNasEnc = KeyDerivationNative.algorithmKeyDerivation(
                        0x01,
                        encAlgIdentity,
                        kamf
                );

                // 5) 推导 NAS INT key：N_NAS_INT_ALG = 0x02
                String kNasInt = KeyDerivationNative.algorithmKeyDerivation(
                        0x02,
                        intAlgIdentity,
                        kamf
                );

                if (kNasEnc != null && !kNasEnc.isEmpty()) {
                    ctx.setKNasEnc(kNasEnc);
                }
                if (kNasInt != null && !kNasInt.isEmpty()) {
                    ctx.setKNasInt(kNasInt);
                }

                ctx.setAttachState("NAS_SMC");
            }
        }

        // 5) Initial Context Setup Request：保存 NGAP SecurityKey（KgNB）
        else if ("Initial Context Setup Request".equals(type)) {
            NgapInfo ngap = pickNAGPSecurityMode(msg.getNgapInfoList());
            if (ngap != null) {
                String securityKeyHex = ngap.getSecurityKeyHex();
                if (securityKeyHex != null && !securityKeyHex.isEmpty()) {
                    ctx.setSecurityKeyHex(securityKeyHex);
                    ctx.setAttachState("INITIAL_CONTEXT_SETUP");
                }
            }
        }

        // 6) RRC SecurityModeCommand：保存 RRC 层完整性/加密算法 + 推导 RRC ENC/INT KEY（用 KGNB）
        else if ("RRC SecurityModeCommand".equals(type)) {
            RrcInfo rrc = msg.getRrcInfo();
            if (rrc != null) {
                String integrityAlgStr = rrc.getIntegrityProtAlgorithm(); // "1"/"2"/"3"
                String cipherAlgStr    = rrc.getCipheringAlgorithm();     // "1"/"2"/"3"

                if (integrityAlgStr != null && !integrityAlgStr.isEmpty()) {
                    ctx.setRrcIntAlg(integrityAlgStr);
                }
                if (cipherAlgStr != null && !cipherAlgStr.isEmpty()) {
                    ctx.setRrcCipherAlg(cipherAlgStr);
                }

                // 1) 取 KGNB（你这里存的是 Initial Context Setup Request 里的 SecurityKeyHex）
                String kgnb = ctx.getSecurityKeyHex();
                if (kgnb == null || kgnb.isEmpty()) {
                    // 兜底：从 Redis 再拉一次
                    UEContext latest = getContext(ctx.getUeId());
                    if (latest != null) {
                        kgnb = latest.getSecurityKeyHex();
                        if (ctx.getSecurityKeyHex() == null) ctx.setSecurityKeyHex(kgnb);
                    }
                }

                // 没有 KGNB 推不了 key：只记录算法与状态
                if (kgnb == null || kgnb.isEmpty()) {
                    ctx.setAttachState("RRC_SMC");
                    saveContext(ctx);
                    return;
                }

                // 2) 解析并映射 algorithm_identity（各用各的编号）
                int encNo = parseAlgNo123(cipherAlgStr);       // 1/2/3
                int intNo = parseAlgNo123(integrityAlgStr);    // 1/2/3

                int encAlgIdentity = mapAlgIdentity(encNo);    // NEA*_NIA*
                int intAlgIdentity = mapAlgIdentity(intNo);

                // 3) 推导 RRC ENC key：N_RRC_ENC_ALG = 0x03
                String kRrcEnc = KeyDerivationNative.algorithmKeyDerivation(
                        0x03,
                        encAlgIdentity,
                        kgnb
                );

                // 4) 推导 RRC INT key：N_RRC_INT_ALG = 0x04
                String kRrcInt = KeyDerivationNative.algorithmKeyDerivation(
                        0x04,
                        intAlgIdentity,
                        kgnb
                );

                if (kRrcEnc != null && !kRrcEnc.isEmpty()) {
                    ctx.setKRrcEnc(kRrcEnc);
                }
                if (kRrcInt != null && !kRrcInt.isEmpty()) {
                    ctx.setKRrcInt(kRrcInt);
                }

                ctx.setAttachState("RRC_SMC");
            }
        }


        // 最后统一落库
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
