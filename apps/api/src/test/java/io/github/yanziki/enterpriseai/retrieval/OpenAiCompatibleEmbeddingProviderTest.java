package io.github.yanziki.enterpriseai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.yanziki.enterpriseai.retrieval.embedding.OpenAiCompatibleEmbeddingProvider;
import io.github.yanziki.enterpriseai.retrieval.indexing.RetrievalIndexingException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiCompatibleEmbeddingProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";
    private volatile long responseDelayMillis = 0;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    requestPath.set(exchange.getRequestURI().getPath());
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    requestBody.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    if (responseDelayMillis > 0) {
                        try {
                            Thread.sleep(responseDelayMillis);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsBoundedRequestAndRestoresInputOrder() {
        responseBody =
                """
                {"data": [
                  {"object": "embedding", "index": 1, "embedding": [0.0, 1.0, 0.0]},
                  {"object": "embedding", "index": 0, "embedding": [1.0, 0.0, 0.0]}
                ], "model": "embed-test", "usage": {"prompt_tokens": 4}}
                """;
        OpenAiCompatibleEmbeddingProvider provider = provider(3, false, 5000);

        List<float[]> vectors = provider.embedDocuments(List.of("first chunk", "second chunk"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.0f, 0.0f);
        assertThat(vectors.get(1)).containsExactly(0.0f, 1.0f, 0.0f);
        assertThat(requestPath.get()).isEqualTo("/v1/embeddings");
        assertThat(authorization.get()).isEqualTo("Bearer test-only-key");
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("embed-test");
        assertThat(body.path("encoding_format").asText()).isEqualTo("float");
        assertThat(body.path("input").get(0).asText()).isEqualTo("first chunk");
        assertThat(body.path("input").get(1).asText()).isEqualTo("second chunk");
        assertThat(body.has("dimensions")).isFalse();
        assertThat(provider.providerId()).isEqualTo("openai-compatible");
        assertThat(provider.modelId()).isEqualTo("embed-test");
        assertThat(provider.dimension()).isEqualTo(3);
    }

    @Test
    void requestsShortenedDimensionsOnlyWhenConfigured() {
        responseBody = "{\"data\": [{\"index\": 0, \"embedding\": [0.6, 0.8]}]}";

        float[] vector = provider(2, true, 5000).embedQuery("expense policy");

        assertThat(vector).containsExactly(0.6f, 0.8f);
        assertThat(objectMapper.readTree(requestBody.get()).path("dimensions").asInt())
                .isEqualTo(2);
    }

    @Test
    void emptyBatchesDoNotCallTheProvider() {
        assertThat(provider(3, false, 5000).embedDocuments(List.of())).isEmpty();
        assertThat(requestBody.get()).isNull();
    }

    @Test
    void throttlingAndServerErrorsAreRetryableButClientErrorsAreNot() {
        OpenAiCompatibleEmbeddingProvider provider = provider(3, false, 5000);

        for (int status : List.of(429, 500, 503)) {
            responseStatus = status;
            assertThatThrownBy(() -> provider.embedQuery("query"))
                    .isInstanceOfSatisfying(
                            RetrievalIndexingException.class,
                            exception -> {
                                assertThat(exception.failureCode())
                                        .isEqualTo(
                                                RetrievalFailureCode
                                                        .EMBEDDING_PROVIDER_UNAVAILABLE);
                                assertThat(exception.retryable()).isTrue();
                            });
        }
        for (int status : List.of(400, 401, 404)) {
            responseStatus = status;
            assertThatThrownBy(() -> provider.embedQuery("query"))
                    .isInstanceOfSatisfying(
                            RetrievalIndexingException.class,
                            exception -> {
                                assertThat(exception.failureCode())
                                        .isEqualTo(
                                                RetrievalFailureCode
                                                        .EMBEDDING_PROVIDER_UNAVAILABLE);
                                assertThat(exception.retryable()).isFalse();
                            });
        }
    }

    @Test
    void malformedResponsesAreRejectedWithoutPartialVectors() {
        OpenAiCompatibleEmbeddingProvider provider = provider(2, false, 5000);
        List<String> malformed =
                List.of(
                        "not json",
                        "{\"data\": []}",
                        "{\"data\": [{\"index\": 0, \"embedding\": [1, 0]}]}",
                        "{\"data\": [{\"index\": 0, \"embedding\": [1, 0]},"
                                + " {\"index\": 0, \"embedding\": [0, 1]}]}",
                        "{\"data\": [{\"index\": 0, \"embedding\": [1, 0]},"
                                + " {\"index\": 2, \"embedding\": [0, 1]}]}",
                        "{\"data\": [{\"index\": 0, \"embedding\": [1, 0]},"
                                + " {\"index\": 1, \"embedding\": [\"x\", 1]}]}");

        for (String body : malformed) {
            responseBody = body;
            assertThatThrownBy(() -> provider.embedDocuments(List.of("a", "b")))
                    .isInstanceOfSatisfying(
                            RetrievalIndexingException.class,
                            exception ->
                                    assertThat(exception.failureCode())
                                            .isEqualTo(
                                                    RetrievalFailureCode
                                                            .EMBEDDING_RESPONSE_INVALID));
        }
    }

    @Test
    void slowProvidersTimeOutAsRetryableFailures() {
        responseDelayMillis = 1500;
        responseBody = "{\"data\": [{\"index\": 0, \"embedding\": [1, 0]}]}";

        assertThatThrownBy(() -> provider(2, false, 200).embedQuery("query"))
                .isInstanceOfSatisfying(
                        RetrievalIndexingException.class,
                        exception -> {
                            assertThat(exception.failureCode())
                                    .isEqualTo(RetrievalFailureCode.EMBEDDING_TIMEOUT);
                            assertThat(exception.retryable()).isTrue();
                        });
    }

    @Test
    void incompleteOrInsecureConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> provider(URI.create("http://embeddings.example.com/v1/"), "m", 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> provider(URI.create("https://embeddings.example.com/v1/"), "", 3))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider(URI.create("https://embeddings.example.com/v1/"), "m", 0))
                .isInstanceOf(IllegalStateException.class);
        assertThat(provider(URI.create("https://embeddings.example.com/v1/"), "m", 3).dimension())
                .isEqualTo(3);
    }

    private OpenAiCompatibleEmbeddingProvider provider(
            int dimension, boolean sendDimensions, int timeoutMillis) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        return new OpenAiCompatibleEmbeddingProvider(
                properties(baseUrl, "embed-test", dimension, sendDimensions, timeoutMillis),
                objectMapper);
    }

    private OpenAiCompatibleEmbeddingProvider provider(URI baseUrl, String model, int dimension) {
        return new OpenAiCompatibleEmbeddingProvider(
                properties(baseUrl, model, dimension, false, 5000), objectMapper);
    }

    private static RetrievalProperties properties(
            URI baseUrl, String model, int dimension, boolean sendDimensions, int timeoutMillis) {
        return new RetrievalProperties(
                100,
                20,
                10,
                750,
                4,
                2,
                300,
                2000,
                20,
                50,
                32,
                "openai-compatible",
                new RetrievalProperties.OpenAiEmbeddingProperties(
                        baseUrl, model, "test-only-key", dimension, sendDimensions, timeoutMillis));
    }
}
