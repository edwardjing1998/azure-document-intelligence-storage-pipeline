package com.example.documentintelligence.api;

import java.util.List;

public record FileProcessResult(
        String sourceBlob,
        String markdownBlob,
        String layoutBlob,
        List<String> figureBlobs,
        String status,
        String error
) {
    public static FileProcessResult success(
            String source,
            String markdown,
            String layout,
            List<String> figures
    ) {
        return new FileProcessResult(
                source,
                markdown,
                layout,
                figures,
                "SUCCEEDED",
                null
        );
    }

    public static FileProcessResult skipped(
            String source,
            String markdown,
            String layout
    ) {
        return new FileProcessResult(
                source,
                markdown,
                layout,
                List.of(),
                "SKIPPED",
                null
        );
    }

    public static FileProcessResult failure(
            String source,
            Exception exception
    ) {
        return new FileProcessResult(
                source,
                null,
                null,
                List.of(),
                "FAILED",
                exception.getMessage()
        );
    }
}