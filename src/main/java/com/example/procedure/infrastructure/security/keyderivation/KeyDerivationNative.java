package com.example.procedure.infrastructure.security.keyderivation;

/**
 * Stable facade for local key-derivation JNI capabilities.
 *
 * <p>The actual native symbol binding must stay on the historical
 * {@code com.example.procedure.keyderivation.KeyDerivationNative} class because
 * the shipped JNI library was compiled against that fully qualified name. This
 * facade lets the refactored package layout keep using a clearer infrastructure
 * package without breaking the native entry points.</p>
 */
public class KeyDerivationNative {

    /**
     * Derive KSEAF from SNN and KAUSF.
     */
    public static String kseafFromKausf(String snn, String kausf) {
        return com.example.procedure.keyderivation.KeyDerivationNative.kseafFromKausf(snn, kausf);
    }

    /**
     * Derive KAMF from SUPI, ABBA, and KSEAF.
     */
    public static String kamfFromKseaf(String supi, byte[] abba, String kseaf) {
        return com.example.procedure.keyderivation.KeyDerivationNative.kamfFromKseaf(supi, abba, kseaf);
    }

    /**
     * Derive one algorithm-specific key from KAMF or KgNB.
     */
    public static String algorithmKeyDerivation(
            int algTypeDist,
            int algIdentity,
            String kamfOrKgnb
    ) {
        return com.example.procedure.keyderivation.KeyDerivationNative.algorithmKeyDerivation(
                algTypeDist,
                algIdentity,
                kamfOrKgnb
        );
    }

    /**
     * Derive KgNB from KAMF.
     */
    public static String kgnbFromKamf(long uplinkNasCount, int accessTypeDist, String kamf) {
        return com.example.procedure.keyderivation.KeyDerivationNative.kgnbFromKamf(
                uplinkNasCount,
                accessTypeDist,
                kamf
        );
    }

    /**
     * Derive NH from KAMF.
     */
    public static String nhFromKamf(String sync, String kamf) {
        return com.example.procedure.keyderivation.KeyDerivationNative.nhFromKamf(sync, kamf);
    }

    /**
     * Derive KngRAN* from KgNB or NH.
     */
    public static String kngRanStarFromKgnb(int pci, String arfcnDl, String nhOrKgnb) {
        return com.example.procedure.keyderivation.KeyDerivationNative.kngRanStarFromKgnb(
                pci,
                arfcnDl,
                nhOrKgnb
        );
    }

    /**
     * Derive a new KAMF from the current KAMF.
     */
    public static String kamfFromKamf(int direction, long count, String kamf) {
        return com.example.procedure.keyderivation.KeyDerivationNative.kamfFromKamf(direction, count, kamf);
    }
}
