package com.example.procedure.processing.message.decrypt;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import org.springframework.stereotype.Component;

/**
 * Centralizes decrypt readiness checks so the coordinator can focus on routing.
 */
@Component
public class DecryptPrerequisitePolicy {

    private static final int MAX_DECRYPT_DEPTH = 4;

    /**
     * Guards decrypt recursion depth so malformed or looping messages cannot churn forever.
     */
    public DecryptAttemptResult validateDepth(SignalingMessage message, String normalizedEncType) {
        int depth = safeDecryptDepth(message);
        if (depth >= MAX_DECRYPT_DEPTH) {
            return DecryptAttemptResult.failed(
                    "decrypt max depth reached: " + depth + ", encType=" + normalizedEncType
            );
        }
        return null;
    }

    /**
     * Validates NAS decrypt prerequisites and returns an early result when decrypt cannot proceed yet.
     */
    public DecryptAttemptResult validateNasPrerequisites(UEContext context, NasInfo nas) {
        if (context == null || isBlank(context.getKNasEnc()) || isBlank(context.getKNasInt())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS);
        }

        if (isBlank(context.getNasCipherAlg()) || isBlank(context.getNasIntAlg())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
        }

        if (isBlank(nas.getCipherTextHex()) || isBlank(nas.getMsgAuthCodeHex())) {
            return DecryptAttemptResult.skip();
        }

        return null;
    }

    /**
     * Validates AS/PDCP decrypt prerequisites and returns an early result when decrypt cannot proceed yet.
     */
    public DecryptAttemptResult validateAsPrerequisites(UEContext context, PdcpInfo pdcp) {
        if (context == null) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        if (isBlank(context.getKRrcEnc()) || isBlank(context.getKRrcInt())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        if (isBlank(context.getRrcCipherAlg()) || isBlank(context.getRrcIntAlg())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
        }

        if (isBlank(pdcp.getSignallingDataHex()) || isBlank(pdcp.getMacHex())) {
            return DecryptAttemptResult.failed("AS decrypt missing ciphertext/mac");
        }

        return null;
    }

    /**
     * Reads decrypt depth defensively so malformed messages do not break decrypt control.
     */
    private int safeDecryptDepth(SignalingMessage message) {
        if (message == null || message.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(message.getDecryptDepth(), 0);
    }

    /**
     * Shared blank check for keying material and algorithm guards.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
