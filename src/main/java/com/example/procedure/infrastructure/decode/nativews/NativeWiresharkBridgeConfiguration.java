package com.example.procedure.infrastructure.decode.nativews;

import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDissector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Selects the phase-1 bridge client without changing the consumer-facing
 * service contract.
 */
@Configuration
public class NativeWiresharkBridgeConfiguration {

    @Bean
    public NativeWiresharkBridgeClient nativeWiresharkBridgeClient(
            NativeWiresharkBridgeProperties properties,
            Nas5gsStructuredDissector structuredDissector,
            ObjectMapper objectMapper
    ) {
        Path jniPath = properties.jniLibraryPathOrNull();
        if (properties.isEnabled() && jniPath != null && Files.exists(jniPath)) {
            return new NativeWiresharkJniBridgeClient(properties);
        }
        if (properties.isEnabled() && properties.isFailFast()) {
            return new UnavailableNativeWiresharkBridgeClient(properties);
        }
        return new StructuredNasBridgeClient(structuredDissector, objectMapper);
    }
}
