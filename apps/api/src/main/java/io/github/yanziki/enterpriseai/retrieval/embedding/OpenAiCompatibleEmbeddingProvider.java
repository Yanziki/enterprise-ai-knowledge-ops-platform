package io.github.yanziki.enterpriseai.retrieval.embedding;

import io.github.yanziki.enterpriseai.retrieval.RetrievalFailureCode;
import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import io.github.yanziki.enterpriseai.retrieval.indexing.RetrievalIndexingException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.retrieval.embedding-mode", havingValue = "openai-compatible")
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER = "openai-compatible";

    private final RetrievalProperties.OpenAiEmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingProvider(
            RetrievalProperties retrievalProperties, ObjectMapper objectMapper) {
        this.properties = retrievalProperties.openai();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        validateConfiguration();
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public String modelId() {
        return properties.model();
    }

    @Override
    public int dimension() {
        return properties.dimension();
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId());
        body.put("input", texts);
        body.put("encoding_format", "float");
        if (properties.sendDimensions()) {
            body.put("dimensions", dimension());
        }
        HttpResponse<String> response = send(body);
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            throw unavailable(true);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable(false);
        }
        return parse(response.body(), texts.size());
    }

    @Override
    public float[] embedQuery(String query) {
        return embedDocuments(List.of(query)).getFirst();
    }

    private HttpResponse<String> send(Map<String, Object> body) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(endpoint())
                            .timeout(properties.timeout())
                            .header("Authorization", "Bearer " + properties.apiKey())
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)))
                            .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new RetrievalIndexingException(
                    RetrievalFailureCode.EMBEDDING_TIMEOUT,
                    "The embedding provider did not respond in time",
                    true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(true);
        } catch (IOException exception) {
            throw unavailable(true);
        }
    }

    private List<float[]> parse(String responseBody, int expectedCount) {
        JsonNode data;
        try {
            data = objectMapper.readTree(responseBody).path("data");
        } catch (RuntimeException exception) {
            throw invalid();
        }
        if (!data.isArray() || data.size() != expectedCount) {
            throw invalid();
        }
        float[][] vectors = new float[expectedCount][];
        for (JsonNode item : data) {
            JsonNode index = item.path("index");
            JsonNode embedding = item.path("embedding");
            if (!index.isIntegralNumber()
                    || index.intValue() < 0
                    || index.intValue() >= expectedCount
                    || vectors[index.intValue()] != null
                    || !embedding.isArray()) {
                throw invalid();
            }
            float[] vector = new float[embedding.size()];
            for (int position = 0; position < vector.length; position++) {
                JsonNode value = embedding.get(position);
                if (!value.isNumber()) {
                    throw invalid();
                }
                vector[position] = value.floatValue();
            }
            vectors[index.intValue()] = vector;
        }
        return List.copyOf(Arrays.asList(vectors));
    }

    private URI endpoint() {
        String base = properties.baseUrl().toString();
        return URI.create((base.endsWith("/") ? base : base + "/") + "embeddings");
    }

    private RetrievalIndexingException unavailable(boolean retryable) {
        return new RetrievalIndexingException(
                RetrievalFailureCode.EMBEDDING_PROVIDER_UNAVAILABLE,
                "The configured embedding provider is unavailable",
                retryable);
    }

    private RetrievalIndexingException invalid() {
        return new RetrievalIndexingException(
                RetrievalFailureCode.EMBEDDING_RESPONSE_INVALID,
                "The embedding provider returned an invalid response",
                false);
    }

    private void validateConfiguration() {
        if (modelId().isBlank() || properties.apiKey().isBlank() || dimension() < 1) {
            throw new IllegalStateException(
                    "OpenAI-compatible embeddings require a model, dimension, and API key"
                            + " from configuration");
        }
        String scheme = properties.baseUrl().getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !("http".equalsIgnoreCase(scheme)
                        && properties.baseUrl().getHost().matches("localhost|127\\.0\\.0\\.1"))) {
            throw new IllegalStateException(
                    "Remote OpenAI-compatible embedding endpoints must use HTTPS");
        }
    }
}
