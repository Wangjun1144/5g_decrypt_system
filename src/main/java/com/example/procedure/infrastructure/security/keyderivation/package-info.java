/**
 * Key-derivation integration boundary.
 *
 * This package isolates key-derivation access behind a formal service
 * contract, lightweight request/result DTOs, and the current native JNI
 * implementation so the backend can later be replaced by another local binding
 * or a remote cryptographic service.
 */
package com.example.procedure.infrastructure.security.keyderivation;
