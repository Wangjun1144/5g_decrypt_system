package com.example.procedure.keyderivation;

/**
 * Historical JNI anchor class.
 *
 * <p>The native library exports symbols against this original fully qualified
 * class name, so it must remain stable even after the surrounding package
 * layout is refactored.</p>
 */
public class KeyDerivationNative {

    static {
        System.loadLibrary("libkey_derivation_jni_win");
    }

    public static native String kseafFromKausf(String snn, String kausf);

    public static native String kamfFromKseaf(String supi, byte[] abba, String kseaf);

    public static native String algorithmKeyDerivation(
            int algTypeDist,
            int algIdentity,
            String kamfOrKgnb
    );

    public static native String kgnbFromKamf(long uplinkNasCount, int accessTypeDist, String kamf);

    public static native String nhFromKamf(String sync, String kamf);

    public static native String kngRanStarFromKgnb(int pci, String arfcnDl, String nhOrKgnb);

    public static native String kamfFromKamf(int direction, long count, String kamf);
}
