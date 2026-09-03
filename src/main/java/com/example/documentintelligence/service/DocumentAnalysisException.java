package com.example.documentintelligence.service;

public class DocumentAnalysisException extends RuntimeException {
    public DocumentAnalysisException(String message) {
        super(message);
    }

    public DocumentAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
