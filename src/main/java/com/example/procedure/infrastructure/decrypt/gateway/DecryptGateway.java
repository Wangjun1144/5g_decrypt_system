package com.example.procedure.infrastructure.decrypt.gateway;

/**
 * Boundary for calling external decrypt capabilities.
 *
 * The upper processing pipeline depends on this contract instead of any
 * transport-specific implementation such as HTTP, RPC, or local mocks.
 */
public interface DecryptGateway {

    /**
     * Execute one decrypt request against the configured external capability.
     *
     * @param request decrypt request payload
     * @return decrypt response from the external capability
     * @throws Exception when the remote call or response parsing fails
     */
    DecryptGatewayResult decrypt(DecryptRequest request) throws Exception;
}
