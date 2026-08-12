package io.github.yanziki.enterpriseai.retrieval.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "local", "container"})
@ConditionalOnProperty(name = "app.retrieval.embedding-mode", havingValue = "deterministic-smoke")
public class DeterministicSmokeEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER = "deterministic-smoke";
    public static final String MODEL = "hashed-token-v1-test-only";
    public static final int DIMENSION = 64;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CASE);

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public String modelId() {
        return MODEL;
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return List.copyOf(vectors);
    }

    @Override
    public float[] embedQuery(String query) {
        return embed(query);
    }

    private float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            byte[] digest = sha256(matcher.group());
            int index = Byte.toUnsignedInt(digest[0]) % DIMENSION;
            vector[index] += (digest[1] & 1) == 0 ? 1.0f : -1.0f;
        }
        double normSquared = 0.0;
        for (float value : vector) {
            normSquared += value * value;
        }
        if (normSquared == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }
        float norm = (float) Math.sqrt(normSquared);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
        return vector;
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
