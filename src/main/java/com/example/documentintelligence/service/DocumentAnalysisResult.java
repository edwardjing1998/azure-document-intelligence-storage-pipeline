package com.example.documentintelligence.service;

import java.util.List;

public record DocumentAnalysisResult(String markdown, List<ExtractedFigure> figures) {
}
