package com.example.documentintelligence.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePathMapperTests {
    private final StoragePathMapper mapper = new StoragePathMapper();

    @Test
    void preservesBookAndChapterFolders() {
        assertThat(mapper.outputBase(
                "source/book-01/chapter-01/problem-011.png",
                "source/",
                "generated/"))
                .isEqualTo("generated/book-01/chapter-01/problem-011");
    }

    @Test
    void normalizesPrefixes() {
        assertThat(mapper.normalizePrefix("/source\\book-011"))
                .isEqualTo("source/book-011/");
    }
}
