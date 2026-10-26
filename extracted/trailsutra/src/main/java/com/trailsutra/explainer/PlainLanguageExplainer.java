package com.trailsutra.explainer;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PlainLanguageExplainer {
    private static final Logger LOGGER = Logger.getLogger(PlainLanguageExplainer.class.getName());
    private static final URI ENDPOINT = URI.create("https://api.sarvam.ai/v2/chat/completions");
    private static final String MODEL = "sarvam-105b";
    private static final String INSTRUCTIONS =
            "Rewrite the supplied reason in clear, plain language without adding facts. "
                    + "Return only the rewritten reason.";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final boolean enabled;
    private final String apiKey;
    private final URI endpoint;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public PlainLanguageExplainer(
            @Value("${trailsutra.explainer.enabled:false}") boolean enabled,
            ObjectMapper mapper) {
        this(enabled, System.getenv("SARVAM_API_KEY"), ENDPOINT, mapper,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
    }

    PlainLanguageExplainer(boolean enabled, String apiKey, URI endpoint, ObjectMapper mapper,
                           HttpClient client) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.mapper = mapper;
        this.client = client;
    }

    public String explain(String reason) {
        if (!enabled || apiKey == null || apiKey.isBlank() || reason == null || reason.isBlank()) {
            return reason;
        }

        try {
            byte[] requestBody = mapper.writeValueAsBytes(Map.of(
                    "model", MODEL,
                    "messages", new Object[]{
                            Map.of("role", "system", "content", INSTRUCTIONS),
                            Map.of("role", "user", "content", reason)
                    },
                    "stream", false,
                    "max_tokens", 200));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("api-subscription-key", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warning("Sarvam explainer request returned HTTP " + response.statusCode());
                return reason;
            }
            JsonNode choices = mapper.readTree(response.body()).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                LOGGER.warning("Sarvam explainer response did not contain a completion");
                return reason;
            }
            String content = choices.get(0).path("message").path("content").asText("").trim();
            return content.isEmpty() ? reason : content;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Sarvam explainer request failed", e);
            return reason;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Sarvam explainer request was interrupted", e);
            return reason;
        }
    }
}
