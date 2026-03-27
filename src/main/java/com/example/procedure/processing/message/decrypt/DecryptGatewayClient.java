package com.example.procedure.processing.message.decrypt;

import com.example.procedure.infrastructure.decrypt.gateway.DecryptGateway;
import com.example.procedure.infrastructure.decrypt.gateway.DecryptGatewayResult;
import com.example.procedure.infrastructure.decrypt.gateway.DecryptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wraps decrypt gateway transport calls so error handling stays outside the coordinator.
 */
@Component
public class DecryptGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(DecryptGatewayClient.class);

    private final DecryptGateway decryptGateway;

    public DecryptGatewayClient(DecryptGateway decryptGateway) {
        this.decryptGateway = decryptGateway;
    }

    /**
     * Calls the decrypt gateway and converts transport exceptions into a null result.
     */
    public DecryptGatewayResult decrypt(DecryptRequest request, String targetLayer) {
        try {
            return decryptGateway.decrypt(request);
        } catch (Exception e) {
            log.warn("{} decrypt gateway call failed: {}", targetLayer, e.getMessage());
            return null;
        }
    }
}
