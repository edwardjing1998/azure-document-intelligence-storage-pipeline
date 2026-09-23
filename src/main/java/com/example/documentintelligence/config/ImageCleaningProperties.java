package com.example.documentintelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "image-cleaning")
public record ImageCleaningProperties(
        boolean enabled,
        int saturationThreshold,
        int minimumBrightness,
        int radius,
        boolean failOnError
) {
    public ImageCleaningProperties {
        saturationThreshold = Math.max(0, Math.min(255, saturationThreshold));
        minimumBrightness = Math.max(0, Math.min(255, minimumBrightness));
        radius = Math.max(1, Math.min(8, radius));
    }
}
