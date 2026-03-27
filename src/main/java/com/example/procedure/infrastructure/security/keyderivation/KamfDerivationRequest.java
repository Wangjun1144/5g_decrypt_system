package com.example.procedure.infrastructure.security.keyderivation;

/**
 * Request contract for deriving KAMF from SUPI, ABBA, and KSEAF.
 */
public class KamfDerivationRequest {

    private final String supi;
    private final byte[] abba;
    private final String kseaf;

    /**
     * Create one KAMF derivation request.
     *
     * @param supi subscriber permanent identifier
     * @param abba ABBA parameter bytes
     * @param kseaf KSEAF input key
     */
    public KamfDerivationRequest(String supi, byte[] abba, String kseaf) {
        this.supi = supi;
        this.abba = abba;
        this.kseaf = kseaf;
    }

    public static KamfDerivationRequest of(String supi, byte[] abba, String kseaf) {
        return new KamfDerivationRequest(supi, abba, kseaf);
    }

    public String getSupi() {
        return supi;
    }

    public byte[] getAbba() {
        return abba;
    }

    public String getKseaf() {
        return kseaf;
    }
}
