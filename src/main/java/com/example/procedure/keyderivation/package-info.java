/**
 * Historical JNI anchor package.
 *
 * <p>This package intentionally survives the package reorganization because the
 * shipped native library exports symbols against
 * {@code com.example.procedure.keyderivation.KeyDerivationNative}. Moving or
 * renaming that anchor would break runtime key derivation.</p>
 */
package com.example.procedure.keyderivation;
