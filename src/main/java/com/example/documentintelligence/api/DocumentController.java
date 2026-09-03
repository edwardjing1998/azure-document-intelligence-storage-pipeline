package com.example.documentintelligence.api;

import com.example.documentintelligence.service.DocumentIntelligenceService;
import com.example.documentintelligence.service.AnalysisPackageService;
import com.example.documentintelligence.service.DocumentAnalysisResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private static final Set<String> ACCEPTED_TYPES = Set.of("image/png", "image/jpeg");
    private final DocumentIntelligenceService service;
    private final AnalysisPackageService packageService;

    public DocumentController(DocumentIntelligenceService service, AnalysisPackageService packageService) {
        this.service = service;
        this.packageService = packageService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalyzeResponse> analyze(@RequestPart("file") MultipartFile file)
            throws IOException, InterruptedException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        if (!ACCEPTED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only PNG and JPEG files are accepted.");
        }

        String markdown = service.analyze(file.getBytes(), file.getContentType()).markdown();
        return ResponseEntity.ok(new AnalyzeResponse(
                file.getOriginalFilename(), file.getContentType(), "succeeded", markdown));
    }

    @PostMapping(value = "/analyze-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/zip")
    public ResponseEntity<byte[]> analyzePackage(@RequestPart("file") MultipartFile file)
            throws IOException, InterruptedException {
        validate(file);
        DocumentAnalysisResult result = service.analyze(file.getBytes(), file.getContentType());
        byte[] zip = packageService.create(file.getBytes(), file.getOriginalFilename(), result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=geometry-analysis.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        if (!ACCEPTED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only PNG and JPEG files are accepted.");
        }
    }
}
