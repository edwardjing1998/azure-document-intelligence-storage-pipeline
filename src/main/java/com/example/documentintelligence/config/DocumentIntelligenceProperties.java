package com.example.documentintelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "azure.document-intelligence")
public record DocumentIntelligenceProperties(
        String endpoint,
        String key,
        String apiVersion,
        boolean premiumFeatures,
        int pollingIntervalMillis,
        int maximumPollingAttempts,
        int maximumRetryAttempts,
        long defaultRetryDelayMillis,
        long maximumRetryDelayMillis) {
}
