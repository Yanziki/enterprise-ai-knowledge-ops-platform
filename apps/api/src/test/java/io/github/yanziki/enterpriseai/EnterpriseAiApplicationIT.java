package io.github.yanziki.enterpriseai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String ISSUER = "https://issuer.example.test/realms/enterprise-ai";
    private static final String AUDIENCE = "enterprise-ai-api";
    private static final String ADMIN_SUBJECT = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_SUBJECT = "00000000-0000-0000-0000-000000000002";
    private static final String OTHER_SUBJECT = "00000000-0000-0000-0000-000000000003";
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final RSAKey RSA_KEY = createRsaKey();
    private static final HttpServer JWK_SERVER = startJwkServer();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.1-pg17-bookworm")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("enterprise_ai_test")
                    .withUsername("enterprise_ai_test")
                    .withPassword("synthetic_test_password");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", POSTGRES::getJdbcUrl);
        registry.add("app.database.username", POSTGRES::getUsername);
        registry.add("app.database.password", POSTGRES::getPassword);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.identity.issuer-uri", () -> ISSUER);
        registry.add(
                "app.identity.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort() + "/jwks");
        registry.add("app.identity.audience", () -> AUDIENCE);
    }

    @LocalServerPort private int port;

    @Autowired private ApplicationContext applicationContext;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void applicationContextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void publicHealthAndSystemStatusRemainAccessible() throws Exception {
        HttpResponse<String> status = get("/api/v1/system/status", null);
        HttpResponse<String> readiness = get("/actuator/health/readiness", null);

        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body())
                .contains("\"status\":\"UP\"")
                .contains("\"service\":\"enterprise-ai-api\"")
                .contains("\"version\":\"0.1.0-SNAPSHOT\"");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void meWithoutAuthenticationReturnsUnauthorized() throws Exception {
        assertThat(get("/api/v1/me", null).statusCode()).isEqualTo(401);
    }

    @Test
    void validMemberJwtCanAccessMe() throws Exception {
        HttpResponse<String> response =
                get("/api/v1/me", token(MEMBER_SUBJECT, List.of("MEMBER"), futureExpiry()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"subject\":\"" + MEMBER_SUBJECT + "\"")
                .contains("\"email\":\"member@example.com\"")
                .contains("\"displayName\":\"Acme Member\"")
                .contains("\"platformRoles\":[\"MEMBER\"]")
                .contains("\"slug\":\"acme\"")
                .contains("\"slug\":\"operations\"");
    }

    @Test
    void memberCannotAccessPlatformAdminSummary() throws Exception {
        HttpResponse<String> response =
                get(
                        "/api/v1/admin/system-summary",
                        token(MEMBER_SUBJECT, List.of("MEMBER"), futureExpiry()));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void platformAdminCanAccessPlatformAdminSummary() throws Exception {
        HttpResponse<String> response =
                get(
                        "/api/v1/admin/system-summary",
                        token(ADMIN_SUBJECT, List.of("PLATFORM_ADMIN"), futureExpiry()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"organizationCount\":2")
                .contains("\"workspaceCount\":2")
                .contains("\"userProfileCount\":3")
                .contains("\"membershipCount\":3");
    }

    @Test
    void acmeMemberCanAccessAcmeButNotGlobex() throws Exception {
        String memberToken = token(MEMBER_SUBJECT, List.of("MEMBER"), futureExpiry());

        HttpResponse<String> acme = get("/api/v1/organizations/acme/summary", memberToken);
        HttpResponse<String> globex = get("/api/v1/organizations/globex/summary", memberToken);

        assertThat(acme.statusCode()).isEqualTo(200);
        assertThat(acme.body()).contains("\"slug\":\"acme\"").contains("\"workspaceCount\":1");
        assertThat(globex.statusCode()).isEqualTo(403);
    }

    @Test
    void globexMemberCanAccessGlobexButNotAcme() throws Exception {
        String otherToken = token(OTHER_SUBJECT, List.of("MEMBER"), futureExpiry());

        HttpResponse<String> globex = get("/api/v1/organizations/globex/summary", otherToken);
        HttpResponse<String> acme = get("/api/v1/organizations/acme/summary", otherToken);

        assertThat(globex.statusCode()).isEqualTo(200);
        assertThat(globex.body()).contains("\"slug\":\"globex\"").contains("\"workspaceCount\":1");
        assertThat(acme.statusCode()).isEqualTo(403);
    }

    @Test
    void unknownIdentitySubjectIsDeniedSafely() throws Exception {
        HttpResponse<String> response =
                get(
                        "/api/v1/me",
                        token(
                                "00000000-0000-0000-0000-000000000099",
                                List.of("MEMBER"),
                                futureExpiry()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("identity_subject", "stackTrace", "token");
    }

    @Test
    void unknownPlatformAdminSubjectIsDeniedSafely() throws Exception {
        HttpResponse<String> response =
                get(
                        "/api/v1/admin/system-summary",
                        token(
                                "00000000-0000-0000-0000-000000000099",
                                List.of("PLATFORM_ADMIN"),
                                futureExpiry()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("identity_subject", "stackTrace", "token");
    }

    @Test
    void expiredAndInvalidJwtAreRejected() throws Exception {
        String expired = token(MEMBER_SUBJECT, List.of("MEMBER"), Instant.now().minusSeconds(30));

        assertThat(get("/api/v1/me", expired).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/me", "not-a-valid-jwt").statusCode()).isEqualTo(401);
    }

    @Test
    void wrongAudienceJwtIsRejected() throws Exception {
        String token =
                token(
                        MEMBER_SUBJECT,
                        List.of("MEMBER"),
                        futureExpiry(),
                        "different-resource-server");

        assertThat(get("/api/v1/me", token).statusCode()).isEqualTo(401);
    }

    @Test
    void flywayCreatesConstrainedTenantSchemaOnPostgres() {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspaces", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO organizations"
                                                + " (id, slug, display_name) VALUES (?, 'acme', 'Duplicate')",
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private HttpResponse<String> get(String path, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .GET();
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String subject, List<String> roles, Instant expiry)
            throws JOSEException {
        return token(subject, roles, expiry, AUDIENCE);
    }

    private static String token(String subject, List<String> roles, Instant expiry, String audience)
            throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(subject)
                        .audience(audience)
                        .issueTime(Date.from(now.minusSeconds(1)))
                        .expirationTime(Date.from(expiry))
                        .claim("realm_access", Map.of("roles", roles))
                        .build();
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                        claims);
        jwt.sign(new RSASSASigner(RSA_KEY));
        return jwt.serialize();
    }

    private static Instant futureExpiry() {
        return Instant.now().plus(Duration.ofMinutes(5));
    }

    private static RSAKey createRsaKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("integration-test-key").generate();
        } catch (JOSEException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static HttpServer startJwkServer() {
        try {
            HttpServer server =
                    HttpServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            byte[] body =
                    ("{\"keys\":[" + RSA_KEY.toPublicJWK().toJSONString() + "]}")
                            .getBytes(StandardCharsets.UTF_8);
            server.createContext(
                    "/jwks",
                    exchange -> {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
