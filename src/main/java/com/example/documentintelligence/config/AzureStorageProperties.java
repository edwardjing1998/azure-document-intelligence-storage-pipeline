package com.example.documentintelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "azure.storage")
public record AzureStorageProperties(
        String endpoint,
        String connectionString,
        String container,
        String sourcePrefix,
        String outputPrefix
) {
}
