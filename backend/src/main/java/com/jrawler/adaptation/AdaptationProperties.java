package com.jrawler.adaptation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adaptation")
public record AdaptationProperties(String apiKey, String model, int fetchTimeoutSeconds) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
