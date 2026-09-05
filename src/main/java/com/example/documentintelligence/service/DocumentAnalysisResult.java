package com.example.documentintelligence.service;

import java.util.List;

public record DocumentAnalysisResult(
        String markdown,
        String layoutJson,
        List<ExtractedFigure> figures
) {
}