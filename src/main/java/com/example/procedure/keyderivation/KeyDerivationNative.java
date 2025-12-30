package com.example.procedure.keyderivation;

public class KeyDerivationNative {

    static {
        System.loadLibrary("libkey_derivation_jni_win"); // 对应 key_derivation_jni.dll
    }

    // 1) KSEAFfromKAUSF
    public static native String kseafFromKausf(String snn, String kausf);

    // 2) KAMFfromKSEAF (ABBA 是 2 字节)
    public static native String kamfFromKseaf(String supi, byte[] abba, String kseaf);

    // 3) algorithmKEYDerivation
    //    algTypeDist: 1~6; algIdentity: 1/2/3 (NEA1/2/3, NIA1/2/3等)
    public static native String algorithmKeyDerivation(int algTypeDist,
                                                       int algIdentity,
                                                       String kamfOrKgnb);

    // 4) KgNBfromKAMF
    public static native String kgnbFromKamf(long uplinkNasCount,
                                             int accessTypeDist,
                                             String kamf);

    // 5) NHfromKAMF
    public static native String nhFromKamf(String sync, String kamf);

    // 6) KngRANStarfromKgNB
    public static native String kngRanStarFromKgnb(int pci,
                                                   String arfcnDl,
                                                   String nhOrKgnb);

    // 7) KAMFfromKAMF
    //    direction: 0x00=移动性注册更新, 0x01=N2切换
    public static native String kamfFromKamf(int direction,
                                             long count,
                                             String kamf);
}

