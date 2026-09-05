package com.example.documentintelligence.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileProcessResultTests {

    @Test
    void skippedResultRetainsSourceMarkdownAndLayoutPaths() {

        FileProcessResult result =
                FileProcessResult.skipped(
                        "source/book-01/chapter-01/problem-01.png",
                        "generated/book-01/chapter-01/"
                                + "problem-01/content.md",
                        "generated/book-01/chapter-01/"
                                + "problem-01/layout.json"
                );

        assertThat(result.status())
                .isEqualTo("SKIPPED");

        assertThat(result.sourceBlob())
                .endsWith("problem-01.png");

        assertThat(result.markdownBlob())
                .endsWith("content.md");

        assertThat(result.layoutBlob())
                .endsWith("layout.json");

        assertThat(result.figureBlobs())
                .isEmpty();

        assertThat(result.error())
                .isNull();
    }

    @Test
    void successfulResultRetainsAllGeneratedPaths() {

        FileProcessResult result =
                FileProcessResult.success(
                        "source/book-01/chapter-01/problem-01.png",
                        "generated/book-01/chapter-01/"
                                + "problem-01/content.md",
                        "generated/book-01/chapter-01/"
                                + "problem-01/layout.json",
                        java.util.List.of(
                                "generated/book-01/chapter-01/"
                                        + "problem-01/figures/figure-1.png"
                        )
                );

        assertThat(result.status())
                .isEqualTo("SUCCEEDED");

        assertThat(result.markdownBlob())
                .endsWith("content.md");

        assertThat(result.layoutBlob())
                .endsWith("layout.json");

        assertThat(result.figureBlobs())
                .hasSize(1);

        assertThat(result.figureBlobs().get(0))
                .endsWith("figure-1.png");

        assertThat(result.error())
                .isNull();
    }

    @Test
    void failedResultDoesNotContainOutputPaths() {

        FileProcessResult result =
                FileProcessResult.failure(
                        "source/book-01/chapter-01/problem-01.png",
                        new RuntimeException("Analysis failed")
                );

        assertThat(result.status())
                .isEqualTo("FAILED");

        assertThat(result.markdownBlob())
                .isNull();

        assertThat(result.layoutBlob())
                .isNull();

        assertThat(result.figureBlobs())
                .isEmpty();

        assertThat(result.error())
                .isEqualTo("Analysis failed");
    }
}