package com.example.procedure.infrastructure.decrypt.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the external decrypt gateway adapter.
 */
@Component
@ConfigurationProperties(prefix = "decrypt.gateway")
public class DecryptGatewayProperties {

    /**
     * Base URL exposed by the decrypt service.
     */
    private String url = "http://127.0.0.1:8004/decrypt";

    /**
     * Returns the configured decrypt service base URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Updates the configured decrypt service base URL.
     */
    public void setUrl(String url) {
        this.url = url;
    }
}
