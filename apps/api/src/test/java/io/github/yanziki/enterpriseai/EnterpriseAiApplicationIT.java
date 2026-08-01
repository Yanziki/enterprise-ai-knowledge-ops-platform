package io.github.yanziki.enterpriseai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class EnterpriseAiApplicationIT {

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.1-pg17-bookworm")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("enterprise_ai_test")
                    .withUsername("enterprise_ai_test")
                    .withPassword("synthetic_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", POSTGRES::getJdbcUrl);
        registry.add("app.database.username", POSTGRES::getUsername);
        registry.add("app.database.password", POSTGRES::getPassword);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort private int port;

    @Autowired private ApplicationContext applicationContext;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void applicationContextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void systemStatusEndpointReportsServiceAndVersion() throws Exception {
        HttpResponse<String> response = get("/api/v1/system/status");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"UP\"")
                .contains("\"service\":\"enterprise-ai-api\"")
                .contains("\"version\":\"0.1.0-SNAPSHOT\"");
    }

    @Test
    void unknownEndpointIsNotPubliclyAccessible() throws Exception {
        HttpResponse<String> response = get("/api/v1/not-public");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void postgresIsTheIntegrationDatabase() {
        String databaseProduct = jdbcTemplate.queryForObject("SELECT version()", String.class);

        assertThat(databaseProduct).contains("PostgreSQL");
    }

    @Test
    void flywayEnablesVectorExtension() {
        Integer extensionCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                        Integer.class);

        assertThat(extensionCount).isEqualTo(1);
    }

    @Test
    void actuatorReadinessReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
