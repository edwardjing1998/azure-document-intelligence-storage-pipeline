package com.example.documentintelligence.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileProcessResultTests {
    @Test
    void skippedResultRetainsSourceAndMarkdownPath() {
        FileProcessResult result = FileProcessResult.skipped(
                "source/book-01/chapter-01/problem-011.png",
                "generated/book-01/chapter-01/problem-01/content.md");

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.sourceBlob()).endsWith("problem-011.png");
        assertThat(result.markdownBlob()).endsWith("content.md");
        assertThat(result.figureBlobs()).isEmpty();
        assertThat(result.error()).isNull();
    }
}
