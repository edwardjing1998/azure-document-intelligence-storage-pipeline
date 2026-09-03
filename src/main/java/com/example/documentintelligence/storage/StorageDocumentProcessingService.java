package com.example.documentintelligence.storage;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.example.documentintelligence.api.BatchProcessResponse;
import com.example.documentintelligence.api.FileProcessResult;
import com.example.documentintelligence.api.SourceBlobResponse;
import com.example.documentintelligence.config.AzureStorageProperties;
import com.example.documentintelligence.service.AnalysisPackageService;
import com.example.documentintelligence.service.DocumentAnalysisResult;
import com.example.documentintelligence.service.DocumentIntelligenceService;
import com.example.documentintelligence.service.ExtractedFigure;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class StorageDocumentProcessingService {
    private final BlobContainerClientProvider clientProvider;
    private final AzureStorageProperties properties;
    private final StoragePathMapper pathMapper;
    private final DocumentIntelligenceService documentIntelligence;
    private final AnalysisPackageService packageService;

    public StorageDocumentProcessingService(
            BlobContainerClientProvider clientProvider,
            AzureStorageProperties properties,
            StoragePathMapper pathMapper,
            DocumentIntelligenceService documentIntelligence,
            AnalysisPackageService packageService) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.pathMapper = pathMapper;
        this.documentIntelligence = documentIntelligence;
        this.packageService = packageService;
    }

    public List<SourceBlobResponse> listSourceImages(String requestedPrefix, int maxFiles) {
        String prefix = effectivePrefix(requestedPrefix, properties.sourcePrefix());
        return imageBlobs(prefix, maxFiles).stream()
                .map(item -> new SourceBlobResponse(
                        item.getName(),
                        item.getProperties().getContentLength(),
                        item.getProperties().getContentType()))
                .toList();
    }

    public BatchProcessResponse process(
            String requestedSourcePrefix,
            String requestedOutputPrefix,
            int maxFiles,
            boolean overwrite) {
        String sourcePrefix = effectivePrefix(requestedSourcePrefix, properties.sourcePrefix());
        String outputPrefix = effectivePrefix(requestedOutputPrefix, properties.outputPrefix());
        List<BlobItem> sources = imageBlobs(sourcePrefix, maxFiles);
        List<FileProcessResult> results = new ArrayList<>();

        for (BlobItem source : sources) {
            try {
                results.add(processOne(source, sourcePrefix, outputPrefix, overwrite));
            } catch (Exception exception) {
                results.add(FileProcessResult.failure(source.getName(), exception));
            }
        }

        int succeeded = (int) results.stream().filter(r -> "SUCCEEDED".equals(r.status())).count();
        int skipped = (int) results.stream().filter(r -> "SKIPPED".equals(r.status())).count();
        return new BatchProcessResponse(
                sourcePrefix,
                outputPrefix,
                sources.size(),
                succeeded,
                skipped,
                results.size() - succeeded - skipped,
                results);
    }

    private FileProcessResult processOne(
            BlobItem source,
            String sourcePrefix,
            String outputPrefix,
            boolean overwrite) throws Exception {
        BlobContainerClient container = clientProvider.get();
        String outputBase = pathMapper.outputBase(source.getName(), sourcePrefix, outputPrefix);
        String markdownBlob = outputBase + "/content.md";
        BlobClient markdownClient = container.getBlobClient(markdownBlob);

        // Avoid consuming Document Intelligence quota for work that is already complete.
        if (!overwrite && markdownClient.exists()) {
            return FileProcessResult.skipped(source.getName(), markdownBlob);
        }

        BlobClient sourceClient = container.getBlobClient(source.getName());
        byte[] image = sourceClient.downloadContent().toBytes();
        String contentType = contentType(source);
        DocumentAnalysisResult analysis = documentIntelligence.analyze(image, contentType);

        List<String> figureBlobs = new ArrayList<>();
        for (ExtractedFigure figure : analysis.figures()) {
            String figureBlob = outputBase + "/figures/" + figure.fileName();
            // content.md is the completion marker. Existing figures without it are partial output.
            upload(container.getBlobClient(figureBlob), figure.content(), "image/png", true);
            figureBlobs.add(figureBlob);
        }

        String markdown = frontMatter(source.getName()) + packageService.connectFigures(analysis);
        upload(
                markdownClient,
                markdown.getBytes(StandardCharsets.UTF_8),
                "text/markdown; charset=UTF-8",
                overwrite);
        return FileProcessResult.success(source.getName(), markdownBlob, figureBlobs);
    }

    private List<BlobItem> imageBlobs(String prefix, int maxFiles) {
        int safeLimit = Math.max(1, Math.min(maxFiles, 100));
        return clientProvider.get()
                .listBlobs(new ListBlobsOptions().setPrefix(prefix), null)
                .stream()
                .filter(item -> !Boolean.TRUE.equals(item.isPrefix()))
                .filter(item -> isSupportedImage(item.getName()))
                .limit(safeLimit)
                .toList();
    }

    private boolean isSupportedImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private String contentType(BlobItem item) {
        String stored = item.getProperties().getContentType();
        if (stored != null && stored.startsWith("image/")) {
            return stored;
        }
        return item.getName().toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private void upload(BlobClient client, byte[] content, String contentType, boolean overwrite) {
        client.upload(BinaryData.fromBytes(content), overwrite);
        client.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
    }

    private String effectivePrefix(String requested, String configured) {
        return pathMapper.normalizePrefix(
                requested == null || requested.isBlank() ? configured : requested);
    }

    private String frontMatter(String sourceBlob) {
        return "---\n"
                + "sourceBlob: \"" + sourceBlob.replace("\"", "\\\"") + "\"\n"
                + "generatedAt: \"" + OffsetDateTime.now() + "\"\n"
                + "---\n\n";
    }
}
