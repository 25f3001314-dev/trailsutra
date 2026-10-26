package com.trailsutra.explainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlainLanguageExplainerTest {
    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void sendsOnlyOneReasonAndUsesTheChatCompletionResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        AtomicReference<String> subscriptionKey = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(mapper.readTree(exchange.getRequestBody()));
            subscriptionKey.set(exchange.getRequestHeaders().getFirst("api-subscription-key"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"choices":[{"message":{"role":"assistant","content":"Money moved quickly between connected accounts."}}]}
                    """);
        });

        PlainLanguageExplainer explainer = explainer(true, "local-key", mapper);
        String output = explainer.explain("Rapid movement across linked accounts");

        assertThat(output).isEqualTo("Money moved quickly between connected accounts.");
        assertThat(subscriptionKey.get()).isEqualTo("local-key");
        assertThat(authorization.get()).isEqualTo("Bearer local-key");
        JsonNode body = requestBody.get();
        assertThat(body.path("model").asText()).isEqualTo("sarvam-105b");
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("messages")).hasSize(2);
        assertThat(body.path("messages").get(1).path("content").asText())
                .isEqualTo("Rapid movement across linked accounts");
        String serialized = body.toString().toLowerCase();
        assertThat(serialized).doesNotContain("acct-token-481", "account holder name", "amount=74000");
    }

    @Test
    void disabledFeatureDoesNotMakeARequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"Rewritten reason"}}]}
                    """);
        });

        PlainLanguageExplainer explainer = explainer(false, "local-key", new ObjectMapper());
        String reason = "High pass-through; short dwell";

        assertThat(explainer.explain(reason)).isEqualTo(reason);
        assertThat(requests).hasValue(0);
    }

    @Test
    void timeoutReturnsTheOriginalReason() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(6000);
                respond(exchange, 200, """
                        {"choices":[{"message":{"content":"Rewritten reason"}}]}
                        """);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        PlainLanguageExplainer explainer = explainer(true, "local-key", new ObjectMapper());
        String reason = "Funds moved soon after receipt";

        assertThat(explainer.explain(reason)).isEqualTo(reason);
    }

    private PlainLanguageExplainer explainer(boolean enabled, String key, ObjectMapper mapper) {
        return new PlainLanguageExplainer(enabled, key, URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v2/chat/completions"),
                mapper, HttpClient.newBuilder().build());
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/v2/chat/completions", handler);
        server.start();
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
