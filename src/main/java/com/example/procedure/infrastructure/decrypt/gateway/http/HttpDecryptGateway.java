package com.example.procedure.infrastructure.decrypt.gateway.http;

import com.example.procedure.infrastructure.decrypt.gateway.DecryptGatewayResult;
import com.example.procedure.infrastructure.decrypt.gateway.DecryptRequest;
import com.example.procedure.infrastructure.decrypt.gateway.DecryptGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * HTTP implementation of the decrypt gateway.
 *
 * It encapsulates the current remote invocation path so the message decrypt
 * stage can evolve independently from transport details.
 */
@Service
public class HttpDecryptGateway implements DecryptGateway {

    /**
     * Parses the remote JSON response body.
     */
    private final ObjectMapper objectMapper;

    /**
     * External decrypt gateway configuration.
     */
    private final DecryptGatewayProperties properties;

    /**
     * Create the HTTP decrypt gateway.
     *
     * @param objectMapper JSON mapper used for the response body
     * @param properties gateway configuration
     */
    public HttpDecryptGateway(
            ObjectMapper objectMapper,
            DecryptGatewayProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Invoke the external HTTP decrypt service and map its JSON response.
     *
     * @param request decrypt request payload
     * @return parsed decrypt response
     * @throws Exception when the HTTP call or JSON parsing fails
     */
    @Override
    public DecryptGatewayResult decrypt(DecryptRequest request) throws Exception {
        String responseJson = DecryptClient.decrypt(properties.getUrl(), toHttpRequest(request));
        DecryptResponse response = objectMapper.readValue(responseJson, DecryptResponse.class);
        if (response == null) {
            return DecryptGatewayResult.of(null, null, null, null);
        }
        return DecryptGatewayResult.of(
                response.getDecryptStatus(),
                response.getPlainData(),
                response.getPlainMac(),
                response.getErrorMsg()
        );
    }

    private DecryptClient.DecryptRequest toHttpRequest(DecryptRequest request) {
        DecryptClient.DecryptRequest httpRequest = new DecryptClient.DecryptRequest();
        httpRequest.messageId = request.getMessageId();
        httpRequest.ueId = request.getUeId();
        httpRequest.contextRef = request.getContextRef();
        httpRequest.layer = request.getLayer();
        httpRequest.encKey = request.getEncKey();
        httpRequest.intKey = request.getIntKey();
        httpRequest.encAlgo = request.getEncAlgo();
        httpRequest.intAlgo = request.getIntAlgo();
        httpRequest.count = request.getCount();
        httpRequest.bearer = request.getBearer();
        httpRequest.direction = request.getDirection();
        httpRequest.ciphertext = request.getCiphertext();
        httpRequest.mac = request.getMac();
        httpRequest.dataLength = request.getDataLength();
        return httpRequest;
    }
}
