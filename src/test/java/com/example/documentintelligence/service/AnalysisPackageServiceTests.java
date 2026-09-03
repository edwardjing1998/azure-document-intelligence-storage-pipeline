package com.example.documentintelligence.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPackageServiceTests {
    @Test
    void replacesFigureBlockWithRelativeImageLinkAndPackagesFiles() throws Exception {
        var analysis = new DocumentAnalysisResult(
                "Problem text\n\n<figure>ABC</figure>",
                List.of(new ExtractedFigure("1.1", "figure-1.png", new byte[]{1, 2, 3})));

        byte[] result = new AnalysisPackageService().create(
                new byte[]{4, 5, 6}, "geometry-problem.png", analysis);

        boolean markdownFound = false;
        boolean figureFound = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("content.md")) {
                    markdownFound = new String(zip.readAllBytes(), StandardCharsets.UTF_8)
                            .contains("![Detected geometry diagram](figures/figure-1.png)");
                }
                if (entry.getName().equals("figures/figure-1.png")) {
                    figureFound = true;
                }
            }
        }
        assertThat(markdownFound).isTrue();
        assertThat(figureFound).isTrue();
    }
}
