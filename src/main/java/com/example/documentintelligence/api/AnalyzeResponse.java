package com.example.documentintelligence.api;

public record AnalyzeResponse(
        String fileName,
        String contentType,
        String status,
        String markdown) {
}
