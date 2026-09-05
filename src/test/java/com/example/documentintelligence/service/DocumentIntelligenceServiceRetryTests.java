package com.example.documentintelligence.service;

import com.example.documentintelligence.config.DocumentIntelligenceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIntelligenceServiceRetryTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesSubmissionAfterTooManyRequests()
            throws Exception {

        AtomicInteger submissions = new AtomicInteger();

        server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext(
                "/",
                exchange -> handle(exchange, submissions)
        );

        server.start();

        String endpoint =
                "http://localhost:"
                        + server.getAddress().getPort();

        DocumentIntelligenceProperties properties =
                new DocumentIntelligenceProperties(
                        endpoint,
                        "test-key",
                        "2024-11-30",
                        false,
                        1,
                        5,
                        2,
                        1,
                        100
                );

        ObjectMapper objectMapper = new ObjectMapper();

        DocumentIntelligenceService service =
                new DocumentIntelligenceService(
                        HttpClient.newHttpClient(),
                        objectMapper,
                        properties
                );

        DocumentAnalysisResult result =
                service.analyze(
                        new byte[]{1, 2, 3},
                        "image/png"
                );

        assertThat(submissions).hasValue(2);

        assertThat(result.markdown())
                .isEqualTo("# Retried successfully");

        assertThat(result.layoutJson())
                .isNotBlank();

        JsonNode layout =
                objectMapper.readTree(
                        result.layoutJson()
                );

        assertThat(
                layout.path("status").asText()
        ).isEqualTo("succeeded");

        assertThat(
                layout.path("analyzeResult")
                        .path("content")
                        .asText()
        ).isEqualTo("# Retried successfully");

        assertThat(
                layout.path("analyzeResult")
                        .path("pages")
                        .size()
        ).isEqualTo(1);

        JsonNode firstPage =
                layout.path("analyzeResult")
                        .path("pages")
                        .get(0);

        assertThat(
                firstPage.path("pageNumber").asInt()
        ).isEqualTo(1);

        assertThat(
                firstPage.path("width").asInt()
        ).isEqualTo(1600);

        assertThat(
                firstPage.path("height").asInt()
        ).isEqualTo(2200);

        assertThat(
                firstPage.path("unit").asText()
        ).isEqualTo("pixel");

        assertThat(result.figures()).isEmpty();
    }

    private void handle(
            HttpExchange exchange,
            AtomicInteger submissions
    ) throws IOException {

        if ("POST".equals(exchange.getRequestMethod())) {
            handleSubmission(exchange, submissions);
            return;
        }

        handlePolling(exchange);
    }

    private void handleSubmission(
            HttpExchange exchange,
            AtomicInteger submissions
    ) throws IOException {

        if (submissions.incrementAndGet() == 1) {
            exchange.getResponseHeaders()
                    .add("Retry-After", "0");

            respond(
                    exchange,
                    429,
                    """
                    {
                      "error": "throttled"
                    }
                    """
            );

            return;
        }

        String operation =
                "http://localhost:"
                        + server.getAddress().getPort()
                        + "/documentintelligence/documentModels/"
                        + "prebuilt-layout/analyzeResults/result-1"
                        + "?api-version=2024-11-30";

        exchange.getResponseHeaders()
                .add("Operation-Location", operation);

        respond(exchange, 202, "");
    }

    private void handlePolling(
            HttpExchange exchange
    ) throws IOException {

        respond(
                exchange,
                200,
                """
                {
                  "status": "succeeded",
                  "analyzeResult": {
                    "apiVersion": "2024-11-30",
                    "modelId": "prebuilt-layout",
                    "contentFormat": "markdown",
                    "content": "# Retried successfully",
                    "pages": [
                      {
                        "pageNumber": 1,
                        "angle": 0,
                        "width": 1600,
                        "height": 2200,
                        "unit": "pixel",
                        "words": [
                          {
                            "content": "Retried",
                            "polygon": [
                              100,
                              100,
                              250,
                              100,
                              250,
                              150,
                              100,
                              150
                            ],
                            "confidence": 0.99,
                            "span": {
                              "offset": 2,
                              "length": 7
                            }
                          }
                        ],
                        "lines": []
                      }
                    ],
                    "paragraphs": [],
                    "tables": [],
                    "figures": []
                  }
                }
                """
        );
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] content =
                body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .add(
                        "Content-Type",
                        "application/json"
                );

        exchange.sendResponseHeaders(
                status,
                content.length
        );

        exchange.getResponseBody()
                .write(content);

        exchange.close();
    }
}