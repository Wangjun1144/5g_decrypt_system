package com.example.procedure.infrastructure.security.keyderivation;

import org.springframework.stereotype.Service;

/**
 * Native JNI-backed implementation of {@link KeyDerivationService}.
 *
 * This keeps the rest of the system insulated from direct JNI calls while
 * preserving the current local-library execution model.
 */
@Service
public class NativeKeyDerivationService implements KeyDerivationService {

    @Override
    public String kseafFromKausf(String snn, String kausf) {
        return KeyDerivationNative.kseafFromKausf(snn, kausf);
    }

    @Override
    public String kamfFromKseaf(String supi, byte[] abba, String kseaf) {
        return KeyDerivationNative.kamfFromKseaf(supi, abba, kseaf);
    }

    @Override
    public String algorithmKeyDerivation(int algTypeDist, int algIdentity, String kamfOrKgnb) {
        return KeyDerivationNative.algorithmKeyDerivation(algTypeDist, algIdentity, kamfOrKgnb);
    }

    @Override
    public String kgnbFromKamf(long uplinkNasCount, int accessTypeDist, String kamf) {
        return KeyDerivationNative.kgnbFromKamf(uplinkNasCount, accessTypeDist, kamf);
    }

    @Override
    public String nhFromKamf(String sync, String kamf) {
        return KeyDerivationNative.nhFromKamf(sync, kamf);
    }

    @Override
    public String kngRanStarFromKgnb(int pci, String arfcnDl, String nhOrKgnb) {
        return KeyDerivationNative.kngRanStarFromKgnb(pci, arfcnDl, nhOrKgnb);
    }

    @Override
    public String kamfFromKamf(int direction, long count, String kamf) {
        return KeyDerivationNative.kamfFromKamf(direction, count, kamf);
    }
}
