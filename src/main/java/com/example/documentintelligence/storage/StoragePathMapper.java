package com.example.documentintelligence.storage;

import org.springframework.stereotype.Component;

@Component
public class StoragePathMapper {
    public String outputBase(String sourceBlob, String sourcePrefix, String outputPrefix) {
        String source = normalizeBlobName(sourceBlob);
        String sourceRoot = normalizePrefix(sourcePrefix);
        String outputRoot = normalizePrefix(outputPrefix);
        String relative = source.startsWith(sourceRoot) ? source.substring(sourceRoot.length()) : source;
        int extension = relative.lastIndexOf('.');
        if (extension > relative.lastIndexOf('/')) {
            relative = relative.substring(0, extension);
        }
        return outputRoot + relative;
    }

    public String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = normalizeBlobName(value);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String normalizeBlobName(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
