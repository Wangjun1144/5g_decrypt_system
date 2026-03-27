package com.example.procedure.infrastructure.security.keyderivation;

/**
 * Request contract for deriving one algorithm-specific key from KAMF or KgNB.
 */
public class AlgorithmKeyDerivationRequest {

    private final int algorithmTypeDist;
    private final int algorithmIdentity;
    private final String baseKey;

    /**
     * Create one algorithm-key derivation request.
     *
     * @param algorithmTypeDist derivation discriminator for the key family
     * @param algorithmIdentity algorithm identity within the family
     * @param baseKey source key such as KAMF or KgNB
     */
    public AlgorithmKeyDerivationRequest(int algorithmTypeDist, int algorithmIdentity, String baseKey) {
        this.algorithmTypeDist = algorithmTypeDist;
        this.algorithmIdentity = algorithmIdentity;
        this.baseKey = baseKey;
    }

    public static AlgorithmKeyDerivationRequest of(int algorithmTypeDist, int algorithmIdentity, String baseKey) {
        return new AlgorithmKeyDerivationRequest(algorithmTypeDist, algorithmIdentity, baseKey);
    }

    public int getAlgorithmTypeDist() {
        return algorithmTypeDist;
    }

    public int getAlgorithmIdentity() {
        return algorithmIdentity;
    }

    public String getBaseKey() {
        return baseKey;
    }
}
