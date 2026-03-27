package com.example.procedure.processing.context.update;

import com.example.procedure.infrastructure.security.keyderivation.AlgorithmKeyDerivationRequest;
import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationService;
import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationResult;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Shared helper for UE-context update execution.
 *
 * It centralizes Redis mapping writes, reusable picker and parsing helpers, and
 * compensating key-derivation logic through the formal
 * {@link KeyDerivationService} boundary.
 */
@Component
public class UeContextUpdateSupport {

    private static final String MAP_RAN_UE_KEY_PREFIX = "ue:map:ran:";
    private static final String MAP_CRNTI_KEY_PREFIX = "ue:map:crnti:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final KeyDerivationService keyDerivationService;

    public UeContextUpdateSupport(
            StringRedisTemplate redisTemplate,
            KeyDerivationService keyDerivationService
    ) {
        this.redisTemplate = redisTemplate;
        this.keyDerivationService = keyDerivationService;
    }

    /**
     * Save the reverse lookup from RAN UE NGAP ID to UE id.
     */
    public void saveRanMap(String ranUeNgapId, String ueId) {
        if (ranUeNgapId == null || ranUeNgapId.isEmpty() || ueId == null || ueId.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(redisKeyForRanMap(ranUeNgapId), ueId, TTL);
    }

    /**
     * Save the reverse lookup from cell-id plus C-RNTI to UE id.
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
     * Pick the NAS item corresponding to Security Mode Command.
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
     * Pick the first NGAP item as the current effective NGAP payload.
     */
    public NgapInfo pickNgap(List<NgapInfo> ngapList) {
        if (ngapList == null || ngapList.isEmpty()) {
            return null;
        }
        return ngapList.get(0);
    }

    /**
     * Parse algorithm numbers represented as 1, 2, or 3.
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
     * Map algorithm numbers to 5G key-derivation identities.
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
     * Derive NAS keys when KAMF and NAS algorithm selections are both known.
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
            KeyDerivationResult result = keyDerivationService.deriveAlgorithmKey(
                    AlgorithmKeyDerivationRequest.of(0x01, encAlgIdentity, kamf)
            );
            if (result.hasDerivedKey()) {
                ctx.setKNasEnc(result.getDerivedKey());
            }
        }

        if (needInt) {
            KeyDerivationResult result = keyDerivationService.deriveAlgorithmKey(
                    AlgorithmKeyDerivationRequest.of(0x02, intAlgIdentity, kamf)
            );
            if (result.hasDerivedKey()) {
                ctx.setKNasInt(result.getDerivedKey());
            }
        }
    }

    /**
     * Derive RRC keys when KgNB and RRC algorithm selections are both known.
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
            KeyDerivationResult result = keyDerivationService.deriveAlgorithmKey(
                    AlgorithmKeyDerivationRequest.of(0x03, encAlgIdentity, kgnb)
            );
            if (result.hasDerivedKey()) {
                ctx.setKRrcEnc(result.getDerivedKey());
            }
        }

        if (needInt) {
            KeyDerivationResult result = keyDerivationService.deriveAlgorithmKey(
                    AlgorithmKeyDerivationRequest.of(0x04, intAlgIdentity, kgnb)
            );
            if (result.hasDerivedKey()) {
                ctx.setKRrcInt(result.getDerivedKey());
            }
        }
    }
}
