package com.example.procedure.context;

import com.example.procedure.keyderivation.KeyDerivationNative;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.NgapInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * UEContext 更新辅助类
 *
 * 职责：
 * 1. 提供 pick / parse / map 等通用工具方法
 * 2. 提供 Redis 反查映射写入能力
 * 3. 提供 NAS / RRC key 的补偿推导能力
 *
 * 注意：
 * - 这里不改变原功能，只是把 UEContextService 中的工具与推导逻辑抽出来
 */
@Component
public class UeContextUpdateSupport {

    private static final String MAP_RAN_UE_KEY_PREFIX = "ue:map:ran:";
    private static final String MAP_CRNTI_KEY_PREFIX  = "ue:map:crnti:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public UeContextUpdateSupport(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 建立 RAN_UE_NGAP_ID -> ueId 反查映射
     */
    public void saveRanMap(String ranUeNgapId, String ueId) {
        if (ranUeNgapId == null || ranUeNgapId.isEmpty() || ueId == null || ueId.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(redisKeyForRanMap(ranUeNgapId), ueId, TTL);
    }

    /**
     * 预留：如果未来有 cellId + crnti 反查能力，可直接调用这里
     */
    public void saveCrntiMap(String cellId, String crnti, String ueId) {
        if (cellId == null || cellId.isEmpty()
                || crnti == null || crnti.isEmpty()
                || ueId == null || ueId.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(redisKeyForCrntiMap(cellId, crnti), ueId, TTL);
    }

    private String redisKeyForRanMap(String ranUeId) {
        return MAP_RAN_UE_KEY_PREFIX + ranUeId;
    }

    private String redisKeyForCrntiMap(String cellId, String crnti) {
        return MAP_CRNTI_KEY_PREFIX + cellId + ":" + crnti;
    }

    /**
     * 从 NAS 列表里挑 Security Mode Command 对应的 NAS
     * 优先 mmMessageType = 0x5d，否则兜底取第一条。
     */
    public NasInfo pickNasSecurityMode(List<NasInfo> nasList) {
        if (nasList == null || nasList.isEmpty()) {
            return null;
        }
        for (NasInfo nas : nasList) {
            if ("0x5d".equalsIgnoreCase(nas.getMmMessageType())) {
                return nas;
            }
        }
        return nasList.get(0);
    }

    /**
     * 当前项目里 NGAP 相关消息只取第一条作为兜底。
     */
    public NgapInfo pickNgap(List<NgapInfo> ngapList) {
        if (ngapList == null || ngapList.isEmpty()) {
            return null;
        }
        return ngapList.get(0);
    }

    /**
     * 把 "1"/"2"/"3" 映射到算法编号。
     */
    public int parseAlgNo123(String s) {
        if (s == null || s.isEmpty()) {
            return 1;
        }
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 1 && v <= 3) ? v : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 5G 算法 identity 映射：
     * 1 -> 0x01
     * 2 -> 0x02
     * 3 -> 0x03
     */
    public int mapAlgIdentity(int algNo) {
        switch (algNo) {
            case 2:
                return 0x02;
            case 3:
                return 0x03;
            case 1:
            default:
                return 0x01;
        }
    }

    /**
     * 补偿推导 NAS key：
     * 当 KAMF 已到位，而 NAS 算法号此前已保存但 key 还没推出来时，在这里补偿推导。
     */
    public void deriveNasKeysIfPossible(UEContext ctx) {
        String kamf = ctx.getKAmf();
        if (kamf == null || kamf.isEmpty()) {
            return;
        }

        boolean needEnc = ctx.getKNasEnc() == null || ctx.getKNasEnc().isEmpty();
        boolean needInt = ctx.getKNasInt() == null || ctx.getKNasInt().isEmpty();

        String nasEncAlgStr = ctx.getNasCipherAlg();
        String nasIntAlgStr = ctx.getNasIntAlg();

        if (!(needEnc || needInt)) {
            return;
        }
        if (nasEncAlgStr == null || nasEncAlgStr.isEmpty()
                || nasIntAlgStr == null || nasIntAlgStr.isEmpty()) {
            return;
        }

        int encNo = parseAlgNo123(nasEncAlgStr);
        int intNo = parseAlgNo123(nasIntAlgStr);
        int encAlgIdentity = mapAlgIdentity(encNo);
        int intAlgIdentity = mapAlgIdentity(intNo);

        if (needEnc) {
            String kNasEnc = KeyDerivationNative.algorithmKeyDerivation(0x01, encAlgIdentity, kamf);
            if (kNasEnc != null && !kNasEnc.isEmpty()) {
                ctx.setKNasEnc(kNasEnc);
            }
        }

        if (needInt) {
            String kNasInt = KeyDerivationNative.algorithmKeyDerivation(0x02, intAlgIdentity, kamf);
            if (kNasInt != null && !kNasInt.isEmpty()) {
                ctx.setKNasInt(kNasInt);
            }
        }
    }

    /**
     * 补偿推导 RRC key：
     * 当 KGNB(SecurityKeyHex) 已到位，而 RRC 算法号已知但 key 尚未生成时，在这里补偿推导。
     */
    public void deriveRrcKeysIfPossible(UEContext ctx) {
        String kgnb = ctx.getSecurityKeyHex();
        if (kgnb == null || kgnb.isEmpty()) {
            return;
        }

        boolean needEnc = ctx.getKRrcEnc() == null || ctx.getKRrcEnc().isEmpty();
        boolean needInt = ctx.getKRrcInt() == null || ctx.getKRrcInt().isEmpty();

        String cipherAlgStr = ctx.getRrcCipherAlg();
        String integrityAlgStr = ctx.getRrcIntAlg();

        if (!(needEnc || needInt)) {
            return;
        }
        if (cipherAlgStr == null || cipherAlgStr.isEmpty()
                || integrityAlgStr == null || integrityAlgStr.isEmpty()) {
            return;
        }

        int encNo = parseAlgNo123(cipherAlgStr);
        int intNo = parseAlgNo123(integrityAlgStr);
        int encAlgIdentity = mapAlgIdentity(encNo);
        int intAlgIdentity = mapAlgIdentity(intNo);

        if (needEnc) {
            String kRrcEnc = KeyDerivationNative.algorithmKeyDerivation(0x03, encAlgIdentity, kgnb);
            if (kRrcEnc != null && !kRrcEnc.isEmpty()) {
                ctx.setKRrcEnc(kRrcEnc);
            }
        }

        if (needInt) {
            String kRrcInt = KeyDerivationNative.algorithmKeyDerivation(0x04, intAlgIdentity, kgnb);
            if (kRrcInt != null && !kRrcInt.isEmpty()) {
                ctx.setKRrcInt(kRrcInt);
            }
        }
    }
}
