package com.example.documentintelligence.api;

import com.example.documentintelligence.storage.StorageDocumentProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/storage-documents")
public class StorageDocumentController {
    private final StorageDocumentProcessingService service;

    public StorageDocumentController(StorageDocumentProcessingService service) {
        this.service = service;
    }

    @GetMapping("/source-files")
    public ResponseEntity<List<SourceBlobResponse>> list(
            @RequestParam(required = false) String sourcePrefix,
            @RequestParam(defaultValue = "20") int maxFiles) {
        return ResponseEntity.ok(service.listSourceImages(sourcePrefix, maxFiles));
    }

    @PostMapping("/process")
    public ResponseEntity<BatchProcessResponse> process(
            @RequestParam(required = false) String sourcePrefix,
            @RequestParam(required = false) String outputPrefix,
            @RequestParam(defaultValue = "10") int maxFiles,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        return ResponseEntity.ok(service.process(sourcePrefix, outputPrefix, maxFiles, overwrite));
    }
}
