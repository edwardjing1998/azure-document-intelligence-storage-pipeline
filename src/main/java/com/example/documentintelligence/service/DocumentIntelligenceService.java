package com.example.documentintelligence.service;

import com.example.documentintelligence.config.DocumentIntelligenceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIntelligenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIntelligenceService.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DocumentIntelligenceProperties properties;

    public DocumentIntelligenceService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            DocumentIntelligenceProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public DocumentAnalysisResult analyze(byte[] file, String contentType)
            throws IOException, InterruptedException {
        validateConfiguration();
        String operationUrl = submit(file, contentType);
        return poll(operationUrl);
    }

    private String submit(byte[] file, String contentType)
            throws IOException, InterruptedException {
        String base = removeTrailingSlash(properties.endpoint());
        String features = properties.premiumFeatures()
                ? "&features=formulas,ocrHighResolution"
                : "";
        URI uri = URI.create(base
                + "/documentintelligence/documentModels/prebuilt-layout:analyze"
                + "?api-version=" + properties.apiVersion()
                + "&outputContentFormat=markdown"
                + "&output=figures"
                + features);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("Ocp-Apim-Subscription-Key", properties.key())
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(file))
                .build();

        HttpResponse<String> response = sendWithRetry(
                "submit analysis", request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202) {
            throw new DocumentAnalysisException(
                    "Azure submission failed with HTTP " + response.statusCode()
                            + ": " + response.body());
        }

        return response.headers().firstValue("operation-location")
                .orElseThrow(() -> new DocumentAnalysisException(
                        "Azure response did not contain Operation-Location."));
    }

    private DocumentAnalysisResult poll(String operationUrl) throws IOException, InterruptedException {
        for (int attempt = 1; attempt <= properties.maximumPollingAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(operationUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Ocp-Apim-Subscription-Key", properties.key())
                    .GET()
                    .build();

            HttpResponse<String> response = sendWithRetry(
                    "poll analysis", request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new DocumentAnalysisException(
                        "Azure polling failed with HTTP " + response.statusCode()
                                + ": " + response.body());
            }

            JsonNode result = objectMapper.readTree(response.body());
            String status = result.path("status").asText();

            if ("succeeded".equalsIgnoreCase(status)) {
                String markdown = result.path("analyzeResult").path("content").asText();
                String resultId = resultIdFrom(operationUrl);
                List<ExtractedFigure> figures = downloadFigures(
                        resultId, result.path("analyzeResult").path("figures"));
                return new DocumentAnalysisResult(markdown, figures);
            }
            if ("failed".equalsIgnoreCase(status)) {
                throw new DocumentAnalysisException(
                        "Azure analysis failed: " + result.path("error").toString());
            }

            Thread.sleep(properties.pollingIntervalMillis());
        }
        throw new DocumentAnalysisException("Azure analysis did not finish before the timeout.");
    }

    private List<ExtractedFigure> downloadFigures(String resultId, JsonNode figures)
            throws IOException, InterruptedException {
        List<ExtractedFigure> extracted = new ArrayList<>();
        int number = 1;
        for (JsonNode figure : figures) {
            String figureId = figure.path("id").asText();
            if (figureId.isBlank()) {
                continue;
            }
            URI uri = URI.create(removeTrailingSlash(properties.endpoint())
                    + "/documentintelligence/documentModels/prebuilt-layout/analyzeResults/"
                    + resultId + "/figures/" + figureId
                    + "?api-version=" + properties.apiVersion());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("Ocp-Apim-Subscription-Key", properties.key())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = sendWithRetry(
                    "download figure", request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new DocumentAnalysisException(
                        "Figure download failed with HTTP " + response.statusCode());
            }
            extracted.add(new ExtractedFigure(
                    figureId, "figure-" + number++ + ".png", response.body()));
        }
        return List.copyOf(extracted);
    }

    private <T> HttpResponse<T> sendWithRetry(
            String operation,
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        int maximumRetries = Math.max(0, properties.maximumRetryAttempts());
        for (int retry = 0; ; retry++) {
            HttpResponse<T> response = httpClient.send(request, bodyHandler);
            if (response.statusCode() != 429 || retry >= maximumRetries) {
                return response;
            }

            long delayMillis = retryDelayMillis(response, retry);
            LOGGER.warn(
                    "Azure returned HTTP 429 while attempting {}. Retrying in {} ms ({}/{}).",
                    operation,
                    delayMillis,
                    retry + 1,
                    maximumRetries);
            Thread.sleep(delayMillis);
        }
    }

    private long retryDelayMillis(HttpResponse<?> response, int retry) {
        long fallback = exponentialDelayMillis(retry);
        String header = response.headers().firstValue("Retry-After").orElse(null);
        if (header == null || header.isBlank()) {
            return fallback;
        }
        try {
            return cappedDelayMillis(Math.max(0L, Long.parseLong(header.trim())) * 1000L);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(
                        header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return cappedDelayMillis(Math.max(0L, Duration.between(Instant.now(), retryAt).toMillis()));
            } catch (DateTimeParseException ignoredDate) {
                return fallback;
            }
        }
    }

    private long exponentialDelayMillis(int retry) {
        long base = Math.max(1L, properties.defaultRetryDelayMillis());
        int shift = Math.min(retry, 20);
        long multiplier = 1L << shift;
        if (base > Long.MAX_VALUE / multiplier) {
            return maximumRetryDelayMillis();
        }
        return cappedDelayMillis(base * multiplier);
    }

    private long cappedDelayMillis(long value) {
        long maximum = Math.max(1L, properties.maximumRetryDelayMillis());
        return Math.min(Math.max(1L, value), maximum);
    }

    private long maximumRetryDelayMillis() {
        return Math.max(1L, properties.maximumRetryDelayMillis());
    }

    private String resultIdFrom(String operationUrl) {
        String marker = "/analyzeResults/";
        int start = operationUrl.indexOf(marker);
        if (start < 0) {
            throw new DocumentAnalysisException("Cannot find result ID in Operation-Location.");
        }
        start += marker.length();
        int end = operationUrl.indexOf('?', start);
        return end < 0 ? operationUrl.substring(start) : operationUrl.substring(start, end);
    }

    private void validateConfiguration() {
        if (properties.endpoint() == null || properties.endpoint().isBlank()
                || properties.key() == null || properties.key().isBlank()) {
            throw new DocumentAnalysisException(
                    "Set DOCUMENT_INTELLIGENCE_ENDPOINT and DOCUMENT_INTELLIGENCE_KEY.");
        }
    }

    private String removeTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
