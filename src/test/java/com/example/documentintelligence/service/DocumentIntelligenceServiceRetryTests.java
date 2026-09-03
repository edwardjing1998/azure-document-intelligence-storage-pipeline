package com.example.documentintelligence.service;

import com.example.documentintelligence.config.DocumentIntelligenceProperties;
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
    void retriesSubmissionAfterTooManyRequests() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handle(exchange, submissions));
        server.start();

        String endpoint = "http://localhost:" + server.getAddress().getPort();
        DocumentIntelligenceProperties properties = new DocumentIntelligenceProperties(
                endpoint,
                "test-key",
                "2024-11-30",
                false,
                1,
                5,
                2,
                1,
                100);
        DocumentIntelligenceService service = new DocumentIntelligenceService(
                HttpClient.newHttpClient(), new ObjectMapper(), properties);

        DocumentAnalysisResult result = service.analyze(new byte[]{1, 2, 3}, "image/png");

        assertThat(submissions).hasValue(2);
        assertThat(result.markdown()).isEqualTo("# Retried successfully");
        assertThat(result.figures()).isEmpty();
    }

    private void handle(HttpExchange exchange, AtomicInteger submissions) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            if (submissions.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                respond(exchange, 429, "{\"error\":\"throttled\"}");
                return;
            }
            String operation = "http://localhost:" + server.getAddress().getPort()
                    + "/documentintelligence/documentModels/prebuilt-layout/analyzeResults/result-1"
                    + "?api-version=2024-11-30";
            exchange.getResponseHeaders().add("Operation-Location", operation);
            respond(exchange, 202, "");
            return;
        }

        respond(exchange, 200, """
                {
                  "status": "succeeded",
                  "analyzeResult": {
                    "content": "# Retried successfully",
                    "figures": []
                  }
                }
                """);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }
}
