package com.example.procedure.infrastructure.security.keyderivation;

/**
 * Lightweight result wrapper for one key-derivation operation.
 */
public class KeyDerivationResult {

    private final String derivedKey;

    /**
     * Create one key-derivation result.
     *
     * @param derivedKey derived key material, or null when derivation failed or
     *                   was skipped upstream
     */
    public KeyDerivationResult(String derivedKey) {
        this.derivedKey = derivedKey;
    }

    public static KeyDerivationResult of(String derivedKey) {
        return new KeyDerivationResult(derivedKey);
    }

    public String getDerivedKey() {
        return derivedKey;
    }

    /**
     * @return true when the result contains non-empty derived key material
     */
    public boolean hasDerivedKey() {
        return derivedKey != null && !derivedKey.isEmpty();
    }
}
