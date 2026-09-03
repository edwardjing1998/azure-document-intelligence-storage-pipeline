package com.example.documentintelligence.api;

import java.util.List;

public record FileProcessResult(
        String sourceBlob,
        String markdownBlob,
        List<String> figureBlobs,
        String status,
        String error
) {
    public static FileProcessResult success(String source, String markdown, List<String> figures) {
        return new FileProcessResult(source, markdown, figures, "SUCCEEDED", null);
    }

    public static FileProcessResult skipped(String source, String markdown) {
        return new FileProcessResult(source, markdown, List.of(), "SKIPPED", null);
    }

    public static FileProcessResult failure(String source, Exception exception) {
        return new FileProcessResult(source, null, List.of(), "FAILED", exception.getMessage());
    }
}
