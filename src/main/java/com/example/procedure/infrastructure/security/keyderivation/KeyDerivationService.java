package com.example.procedure.infrastructure.security.keyderivation;

/**
 * Formal boundary for key-derivation capabilities used by the context update
 * pipeline.
 *
 * The current implementation may be JNI-based, but callers should depend on
 * this contract so the derivation backend can later be replaced by another
 * local binding or a remote service.
 */
public interface KeyDerivationService {

    /**
     * Derive KAMF through an explicit request/result contract.
     */
    default KeyDerivationResult deriveKamf(KamfDerivationRequest request) {
        if (request == null) {
            return KeyDerivationResult.of(null);
        }
        return KeyDerivationResult.of(
                kamfFromKseaf(request.getSupi(), request.getAbba(), request.getKseaf())
        );
    }

    /**
     * Derive one algorithm-specific key through an explicit request/result
     * contract.
     */
    default KeyDerivationResult deriveAlgorithmKey(AlgorithmKeyDerivationRequest request) {
        if (request == null) {
            return KeyDerivationResult.of(null);
        }
        return KeyDerivationResult.of(
                algorithmKeyDerivation(
                        request.getAlgorithmTypeDist(),
                        request.getAlgorithmIdentity(),
                        request.getBaseKey()
                )
        );
    }

    /**
     * Derive KSEAF from SNN and KAUSF.
     */
    String kseafFromKausf(String snn, String kausf);

    /**
     * Derive KAMF from SUPI, ABBA, and KSEAF.
     */
    String kamfFromKseaf(String supi, byte[] abba, String kseaf);

    /**
     * Derive one algorithm-specific key from KAMF or KgNB.
     */
    String algorithmKeyDerivation(int algTypeDist, int algIdentity, String kamfOrKgnb);

    /**
     * Derive KgNB from KAMF.
     */
    String kgnbFromKamf(long uplinkNasCount, int accessTypeDist, String kamf);

    /**
     * Derive NH from KAMF.
     */
    String nhFromKamf(String sync, String kamf);

    /**
     * Derive KngRAN* from KgNB or NH.
     */
    String kngRanStarFromKgnb(int pci, String arfcnDl, String nhOrKgnb);

    /**
     * Derive a new KAMF from the current KAMF.
     */
    String kamfFromKamf(int direction, long count, String kamf);
}
