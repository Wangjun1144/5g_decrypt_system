package com.example.procedure.infrastructure.decode.nativews;

import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Phase-1 Java entrypoint for direct NAS-5GS byte decoding through the native
 * bridge.
 */
@Service
public class NativeWiresharkNasDecodeService {

    private final NativeWiresharkBridgeClient bridgeClient;
    private final NativeWiresharkResultMapper resultMapper;
    private final NativeWiresharkBridgeProperties properties;

    public NativeWiresharkNasDecodeService(
            NativeWiresharkBridgeClient bridgeClient,
            NativeWiresharkResultMapper resultMapper,
            NativeWiresharkBridgeProperties properties
    ) {
        this.bridgeClient = Objects.requireNonNull(bridgeClient, "bridgeClient must not be null");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public NativeWiresharkNasResult decodeNas5gs(byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        NativeWiresharkNasRequest request = new NativeWiresharkNasRequest(
                payload,
                properties.isIncludeFieldTree(),
                properties.isIncludeOffsets()
        );
        String rawJson = bridgeClient.decodeNas5gs(request);
        return resultMapper.mapNas5gs(rawJson);
    }
}
