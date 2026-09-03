package com.example.documentintelligence.api;

import java.util.List;

public record BatchProcessResponse(
        String sourcePrefix,
        String outputPrefix,
        int discovered,
        int succeeded,
        int skipped,
        int failed,
        List<FileProcessResult> results
) {
}
