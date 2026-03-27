package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationNative;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 鐢ㄤ簬娴嬭瘯 ProClassify_Service 鐨勭畝鍗?DEMO 娴嬭瘯绫?
 */
@SpringBootTest(classes = Application.class)
class KeyDerivationNativeTest {
    // 杩欓噷鐨勫嚑涓父閲忛渶瑕佷綘鑷繁鏍规嵁 constant_key_derivation.h 閲岀殑鏋氫妇鍊间慨鏀?
    // ================== TODO: 鏍规嵁 C++ 鏋氫妇瀹為檯鏁板€兼敼 ==================
    /** 瀵瑰簲 C++ 鐨?algorithm_type_distinguisher::N_NAS_ENC_ALG */
    private static final int ALG_TYPE_N_NAS_ENC_ALG = 1;   // TODO: 鏀规垚鐪熷疄鍊?

    /** 瀵瑰簲 C++ 鐨?algorithm_identity::NEA1_NIA1 */
    private static final int ALG_ID_NEA1_NIA1 = 1;         // TODO: 鏀规垚鐪熷疄鍊?

    /** 瀵瑰簲 C++ 鐨?access_type_distinguisher::KgNB */
    private static final int ACCESS_TYPE_KGNB = 1;         // TODO: 鏀规垚鐪熷疄鍊?

    /** 瀵瑰簲 C++ 鐨?Direction::DIRECTION_DL */
    private static final int DIRECTION_DL = 1;             // TODO: 鏀规垚鐪熷疄鍊?
    // ===================================================================

    @Test
    void testall() {
        // ========== 1. Testing derive KSEAF from KAUSF... ==========
        System.out.println("Testing derive KSEAF from KAUSF...");
        String snn   = "5G:mnc001.mcc001.3gppnetwork.org";
        String kausf = "2a4b148e8b6831ffef59c107a8a325ad2dbb3035660d487f42a7d83a3a4fb606";

        String kseaf = "5a8960ff6a8b013c7bec61a8a46123f2186209446a334c90b563da7af45bbf12";
        System.out.println("KSEAF: " + kseaf);
        System.out.println("// correct KSEAF: 166128D8111E632A84FC0F9A531A34CD33891B8F8FDB5648989FCA0B637003EC");
        System.out.println();

        // ========== 2. Testing derive KAMF from KSEAF... ==========
        System.out.println("Testing derive KAMF from KSEAF...");
        String supi = "001010000000001";
        // 杩欓噷鐩存帴鐢ㄤ笂涓€姝ュ緱鍒扮殑 kseaf
        byte[] abba = new byte[]{0x00, 0x00}; // C++ 閲?ABBA[] = {0x00, 0x00}
        String kamf = KeyDerivationNative.kamfFromKseaf(supi, abba, kseaf);
        System.out.println("KAMF: " + kamf);
        System.out.println("// correct KAMF: 26E1FC1550C96063B33847E9F2AFC85CC05A2FA9A7F902BCD46C9FCA1C2DEC7E");
        System.out.println();

        // ========== 3. Testing derive algorithm key from KAMF/KgNB... ==========
        System.out.println("Testing derive algorithm key from KAMF/KgNB...");
        // C++: std::string KAMF_KgNB = result2.out_key; 涔熷氨鏄?KAMF
        String kamfKgNb = kamf;
        int algTypeDist = ALG_TYPE_N_NAS_ENC_ALG;  // C++: N_NAS_ENC_ALG
        int algIdentity = ALG_ID_NEA1_NIA1;        // C++: NEA1_NIA1

        String algKey = KeyDerivationNative.algorithmKeyDerivation(
                algTypeDist,
                algIdentity,
                kamfKgNb
        );
        System.out.println("Algorithm Key: " + algKey);
        System.out.println("// correct algorithm key: AF44E132B69821903DBBB229C19CB38E140EAAC075364B0528617CC39CB4859E");
        System.out.println();

        // ========== 4. Testing derive KgNB from KAMF... ==========
        System.out.println("Testing derive KgNB from KAMF...");
        long uplinkNasCount = 0x00L;           // C++: u32 UplinkNASCount = 0x00;
        int accessTypeDist  = ACCESS_TYPE_KGNB; // C++: access_type_distinguisher accessTypeDist = KgNB;

        String kgNb = KeyDerivationNative.kgnbFromKamf(
                uplinkNasCount,
                accessTypeDist,
                kamf /* KAMF */
        );
        System.out.println("KgNB: " + kgNb);
        System.out.println("// correct KgNB: FD1B305CDA7E7EB5008A614DB72DE59DFB78A657272ABE4053AD301EB6F28F38");
        System.out.println();

        // ========== 5. Testing derive NH from KAMF... ==========
        System.out.println("Testing derive NH from KAMF...");
        // C++: std::string SYNC = result4.out_key; 涔熷氨鏄笂涓€姝?KgNB
        String sync = kgNb;
        String nh = KeyDerivationNative.nhFromKamf(sync, kamf);
        System.out.println("NH: " + nh);
        System.out.println("// correct NH: FD92A02CCD42C769125E5F3A972B12E95B9C91400D9D58E5A4241694F1439BA2");
        System.out.println();

        // ========== 6. Testing derive KngRANStar from KgNB... ==========
        System.out.println("Testing derive KngRANStar from KgNB...");
        // C++: std::string KgNB = result5.out_key; 杩欓噷浠栦滑鎶?NH 褰撴垚 KgNB 鍙橀噺鐢ㄤ簡
        // 涔熷氨鏄 KngRANStar 鐨勮緭鍏ユ槸 NH锛岃繖閲岀収鎶?C++ 閫昏緫
        String kgNbForKngRanStar = nh;
        int pci = 0x0800;                   // u16 PCI = 0x0800;
        String arfcnDl = "00001388";        // std::string ARFCN_DL = "00001388";

        String kngRanStar = KeyDerivationNative.kngRanStarFromKgnb(
                pci,
                arfcnDl,
                kgNbForKngRanStar
        );
        System.out.println("KngRANStar: " + kngRanStar);
        System.out.println("// correct KngRANStar: 366B20A388A448A6999BEA3DF31162713BA0F101CAA59A03A28197F7498139CE");
        System.out.println();

        // ========== 7. Testing derive KAMF from KAMF... ==========
        System.out.println("Testing derive KAMF from KAMF...");
        int direction = DIRECTION_DL;   // C++: Direction direction = DIRECTION_DL;
        long count    = 0x0aL;          // C++: u32 Count = 0x0a;

        String kamf2 = KeyDerivationNative.kamfFromKamf(
                direction,
                count,
                kamf
        );
        System.out.println("KAMF(2nd): " + kamf2);
        System.out.println("// correct KAMF: 028CAFDD3F0652AB1B95B0BB5730E8106DB964F1D098F4D1E6BAEF822CDA9CEC");
        System.out.println();

        System.out.println("All key derivation JNI tests finished.");
    }
}
