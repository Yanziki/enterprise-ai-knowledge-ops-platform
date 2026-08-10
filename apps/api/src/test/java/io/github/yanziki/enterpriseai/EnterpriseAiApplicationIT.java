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
import io.github.yanziki.enterpriseai.knowledge.storage.BoundedUploadStager;
import io.github.yanziki.enterpriseai.knowledge.storage.DocumentObjectKeyFactory;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorage;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorageException;
import io.github.yanziki.enterpriseai.knowledge.storage.StagedUpload;
import io.github.yanziki.enterpriseai.knowledge.storage.StorageProperties;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessRole;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAuthorizationService;
import io.github.yanziki.enterpriseai.tenant.WorkspaceOperation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class EnterpriseAiApplicationIT {

    private static final String ISSUER = "https://issuer.example.test/realms/enterprise-ai";
    private static final String AUDIENCE = "enterprise-ai-api";
    private static final String ADMIN_SUBJECT = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_SUBJECT = "00000000-0000-0000-0000-000000000002";
    private static final String OTHER_SUBJECT = "00000000-0000-0000-0000-000000000003";
    private static final UUID ACME_ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACME_WORKSPACE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GLOBEX_ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID GLOBEX_WORKSPACE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres");
    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final String MINIO_ACCESS_KEY = "enterprise_ai_test";
    private static final String MINIO_SECRET_KEY = "enterprise_ai_test_secret";
    private static final String DOCUMENT_BUCKET = "enterprise-ai-documents-test";
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final RSAKey RSA_KEY = createRsaKey();
    private static final HttpServer JWK_SERVER = startJwkServer();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                    .withDatabaseName("enterprise_ai_test")
                    .withUsername("enterprise_ai_test")
                    .withPassword("synthetic_test_password");

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(MINIO_IMAGE)
                    .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(
                            Wait.forHttp("/minio/health/ready")
                                    .forPort(9000)
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofSeconds(60)));

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
        registry.add(
                "app.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("app.storage.region", () -> "us-east-1");
        registry.add("app.storage.bucket", () -> DOCUMENT_BUCKET);
        registry.add("app.storage.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("app.storage.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("app.storage.path-style-access", () -> true);
    }

    @LocalServerPort private int port;

    @Autowired private ApplicationContext applicationContext;

    @Autowired private Environment environment;

    @Autowired private Flyway flyway;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private WorkspaceAuthorizationService workspaceAuthorizationService;

    @Autowired private S3Client s3Client;

    @Autowired private ObjectStorage objectStorage;

    @Autowired private StorageProperties storageProperties;

    @Autowired private DocumentObjectKeyFactory documentObjectKeyFactory;

    @Autowired private BoundedUploadStager boundedUploadStager;

    @BeforeEach
    void ensurePrivateDocumentBucket() {
        try {
            s3Client.headBucket(builder -> builder.bucket(storageProperties.bucket()));
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            s3Client.createBucket(builder -> builder.bucket(storageProperties.bucket()));
        }
    }

    @Test
    void applicationContextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void objectStorageUsesOpaqueKeysAndRoundTripsBytesThroughMinio() throws Exception {
        byte[] source = "storage-boundary".getBytes(StandardCharsets.UTF_8);
        String objectKey =
                documentObjectKeyFactory.create(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(objectKey)
                .startsWith("organizations/")
                .endsWith("/original")
                .doesNotContain("..", "customer-file.txt");

        try (StagedUpload staged =
                boundedUploadStager.stage(new ByteArrayInputStream(source), source.length)) {
            assertThat(staged.byteSize()).isEqualTo(source.length);
            assertThat(staged.sha256Hex())
                    .isEqualTo("cb32b6f09ba925b838563672dc76f90f0d293d2e2eee9fc69363f20ed936d3c0");
            objectStorage.put(
                    objectKey, staged.path(), staged.byteSize(), "text/plain; charset=utf-8");
        }

        try (var stored = objectStorage.get(objectKey)) {
            assertThat(stored.contentLength()).isEqualTo(source.length);
            assertThat(stored.content().readAllBytes()).isEqualTo(source);
        }

        objectStorage.delete(objectKey);
        assertThatThrownBy(() -> objectStorage.get(objectKey))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Could not read document bytes");
    }

    @Test
    void securedDocumentFlowPreservesProvenanceAndTenantIsolation() throws Exception {
        byte[] source =
                "Acme synthetic policy: expenses are due within 30 days."
                        .getBytes(StandardCharsets.UTF_8);
        String adminToken = token(ADMIN_SUBJECT, List.of("TENANT_ADMIN"), futureExpiry());
        String memberToken = token(MEMBER_SUBJECT, List.of("MEMBER"), futureExpiry());
        String globexToken = token(OTHER_SUBJECT, List.of("MEMBER"), futureExpiry());
        String base = "/api/v1/organizations/acme/workspaces/operations/documents";

        assertThat(multipart(base, null, "policy.txt", "text/plain", source, null).statusCode())
                .isEqualTo(401);
        assertThat(
                        multipart(base, memberToken, "policy.txt", "text/plain", source, null)
                                .statusCode())
                .isEqualTo(403);

        HttpResponse<String> accepted =
                multipart(
                        base,
                        adminToken,
                        "customer-policy.txt",
                        "text/plain",
                        source,
                        "Synthetic policy");
        assertThat(accepted.statusCode()).isEqualTo(202);
        UUID documentId = UUID.fromString(jsonString(accepted.body(), "documentId"));
        UUID versionId = UUID.fromString(jsonString(accepted.body(), "versionId"));
        String sha256 = jsonString(accepted.body(), "sha256Hex");
        assertThat(sha256)
                .isEqualTo("6f234b9b564c542076291348946839da91bb0ec5f14d6d3404b3b1b6cff7b685");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT created_by_subject FROM document_versions WHERE id = ?",
                                String.class,
                                versionId))
                .isEqualTo(ADMIN_SUBJECT);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT object_key FROM document_versions WHERE id = ?",
                                String.class,
                                versionId))
                .contains(documentId.toString(), versionId.toString())
                .doesNotContain("customer-policy.txt");

        String detailPath = base + "/" + documentId;
        HttpResponse<String> ready = awaitIngestion(detailPath, adminToken, "READY");
        assertThat(ready.body())
                .contains("\"sha256Hex\":\"" + sha256 + "\"")
                .contains("\"byteSize\":" + source.length)
                .contains("\"parserName\":\"JDK UTF-8\"")
                .contains("\"textUnitCount\":1");

        assertThat(get(base, memberToken).body()).contains(documentId.toString());
        HttpResponse<byte[]> download =
                getBytes(detailPath + "/versions/" + versionId + "/download", memberToken);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(source);
        assertThat(sha256(download.body())).isEqualTo(sha256);

        assertThat(get(base, globexToken).statusCode()).isEqualTo(403);
        assertThat(get(detailPath, globexToken).statusCode()).isEqualTo(403);
        assertThat(
                        getBytes(detailPath + "/versions/" + versionId + "/download", globexToken)
                                .statusCode())
                .isEqualTo(403);
        assertThat(post(detailPath + "/archive", globexToken).statusCode()).isEqualTo(403);
        assertThat(
                        get(
                                        "/api/v1/organizations/acme/workspaces/research/documents/"
                                                + documentId,
                                        memberToken)
                                .statusCode())
                .isIn(403, 404);

        assertThat(post(detailPath + "/archive", memberToken).statusCode()).isEqualTo(403);
        HttpResponse<String> archived = post(detailPath + "/archive", adminToken);
        assertThat(archived.statusCode()).isEqualTo(200);
        assertThat(archived.body()).contains("\"status\":\"ARCHIVED\"");
        assertThat(post(detailPath + "/archive", adminToken).statusCode()).isEqualTo(409);
        assertThat(
                        multipart(
                                        detailPath + "/versions",
                                        adminToken,
                                        "new.txt",
                                        "text/plain",
                                        "new".getBytes(StandardCharsets.UTF_8),
                                        null)
                                .statusCode())
                .isEqualTo(409);
    }

    @Test
    void archiveRejectsInFlightIngestionAndSucceedsAfterItBecomesReady() throws Exception {
        String adminToken = token(ADMIN_SUBJECT, List.of("TENANT_ADMIN"), futureExpiry());
        String base = "/api/v1/organizations/acme/workspaces/operations/documents";
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String archivePath = base + "/" + documentId + "/archive";

        insertTestDocument(documentId);
        insertTestVersion(versionId, documentId, 1, "archive-lifecycle");
        try {
            HttpResponse<String> stored = post(archivePath, adminToken);
            assertThat(stored.statusCode()).isEqualTo(409);
            assertThat(stored.body()).contains("\"code\":\"INGESTION_IN_PROGRESS\"");
            assertDocumentStatus(documentId, "ACTIVE");

            jdbcTemplate.update(
                    "UPDATE document_versions SET ingestion_status = 'QUEUED' WHERE id = ?",
                    versionId);
            insertQueuedTestJob(jobId, versionId);
            HttpResponse<String> queued = post(archivePath, adminToken);
            assertThat(queued.statusCode()).isEqualTo(409);
            assertThat(queued.body()).contains("\"code\":\"INGESTION_IN_PROGRESS\"");
            assertDocumentStatus(documentId, "ACTIVE");

            jdbcTemplate.update(
                    "UPDATE document_versions SET ingestion_status = 'PROCESSING' WHERE id = ?",
                    versionId);
            jdbcTemplate.update(
                    "UPDATE document_ingestion_jobs"
                            + " SET status = 'PROCESSING', attempt_count = 1,"
                            + " claimed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP"
                            + " WHERE id = ?",
                    jobId);
            HttpResponse<String> processing = post(archivePath, adminToken);
            assertThat(processing.statusCode()).isEqualTo(409);
            assertThat(processing.body()).contains("\"code\":\"INGESTION_IN_PROGRESS\"");
            assertDocumentStatus(documentId, "ACTIVE");

            jdbcTemplate.update(
                    "UPDATE document_versions"
                            + " SET ingestion_status = 'READY', ready_at = CURRENT_TIMESTAMP"
                            + " WHERE id = ?",
                    versionId);
            jdbcTemplate.update(
                    "UPDATE document_ingestion_jobs"
                            + " SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP,"
                            + " updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    jobId);
            HttpResponse<String> archived = post(archivePath, adminToken);
            assertThat(archived.statusCode()).isEqualTo(200);
            assertThat(archived.body()).contains("\"status\":\"ARCHIVED\"");
        } finally {
            deleteTestDocument(documentId);
        }
    }

    @Test
    void failedDocumentCanBeArchivedButCannotBeRetried() throws Exception {
        String adminToken = token(ADMIN_SUBJECT, List.of("TENANT_ADMIN"), futureExpiry());
        String globexToken = token(OTHER_SUBJECT, List.of("MEMBER"), futureExpiry());
        String base = "/api/v1/organizations/acme/workspaces/operations/documents";
        HttpResponse<String> accepted =
                multipart(
                        base,
                        adminToken,
                        "archived-failure.pdf",
                        "application/pdf",
                        "%PDF-1.4\ninvalid archived failure fixture"
                                .getBytes(StandardCharsets.US_ASCII),
                        null);
        UUID documentId = UUID.fromString(jsonString(accepted.body(), "documentId"));
        UUID versionId = UUID.fromString(jsonString(accepted.body(), "versionId"));
        String detailPath = base + "/" + documentId;
        String retryPath = detailPath + "/versions/" + versionId + "/retry";

        awaitIngestion(detailPath, adminToken, "FAILED");
        assertThat(post(retryPath, globexToken).statusCode()).isEqualTo(403);

        HttpResponse<String> archived = post(detailPath + "/archive", adminToken);
        assertThat(archived.statusCode()).isEqualTo(200);
        assertThat(archived.body()).contains("\"status\":\"ARCHIVED\"");

        HttpResponse<String> retry = post(retryPath, adminToken);
        assertThat(retry.statusCode()).isEqualTo(409);
        assertThat(retry.body()).contains("\"code\":\"DOCUMENT_ARCHIVED\"");
    }

    @Test
    void supportedFormatsExtractDeterministicallyAndUnsafeUploadsFailSafely() throws Exception {
        String adminToken = token(ADMIN_SUBJECT, List.of("TENANT_ADMIN"), futureExpiry());
        String base = "/api/v1/organizations/acme/workspaces/operations/documents";

        HttpResponse<String> markdown =
                multipart(
                        base,
                        adminToken,
                        "handbook.md",
                        "text/markdown",
                        "# Synthetic handbook\n\nOnly test content."
                                .getBytes(StandardCharsets.UTF_8),
                        null);
        assertThat(markdown.statusCode()).isEqualTo(202);
        UUID markdownDocument = UUID.fromString(jsonString(markdown.body(), "documentId"));
        awaitIngestion(base + "/" + markdownDocument, adminToken, "READY");

        HttpResponse<String> pdf =
                multipart(
                        base,
                        adminToken,
                        "policy.pdf",
                        "application/pdf",
                        syntheticPdf("Synthetic PDF page provenance"),
                        null);
        assertThat(pdf.statusCode()).isEqualTo(202);
        UUID pdfDocument = UUID.fromString(jsonString(pdf.body(), "documentId"));
        UUID pdfVersion = UUID.fromString(jsonString(pdf.body(), "versionId"));
        awaitIngestion(base + "/" + pdfDocument, adminToken, "READY");
        List<Map<String, Object>> pdfUnits =
                jdbcTemplate.queryForList(
                        "SELECT ordinal, locator_type, locator_value"
                                + " FROM document_text_units"
                                + " WHERE document_version_id = ? ORDER BY ordinal",
                        pdfVersion);
        assertThat(pdfUnits)
                .hasSize(2)
                .extracting(unit -> unit.get("ordinal"))
                .containsExactly(1, 2);
        assertThat(pdfUnits)
                .extracting(unit -> unit.get("locator_type"))
                .containsExactly("PAGE", "PAGE");
        assertThat(pdfUnits)
                .extracting(unit -> unit.get("locator_value"))
                .containsExactly("1", "2");

        assertThat(
                        multipart(
                                        base,
                                        adminToken,
                                        "unsafe.html",
                                        "text/html",
                                        "<p>no</p>".getBytes(StandardCharsets.UTF_8),
                                        null)
                                .statusCode())
                .isEqualTo(415);
        HttpResponse<String> spoofed =
                multipart(
                        base,
                        adminToken,
                        "spoofed.pdf",
                        "application/pdf",
                        "plain text".getBytes(StandardCharsets.UTF_8),
                        null);
        assertThat(spoofed.statusCode()).isEqualTo(415);
        assertThat(spoofed.body()).contains("CONTENT_TYPE_MISMATCH").doesNotContain("stackTrace");
        assertThat(
                        multipart(base, adminToken, "empty.txt", "text/plain", new byte[0], null)
                                .statusCode())
                .isEqualTo(422);
        assertThat(
                        multipart(
                                        base,
                                        adminToken,
                                        "../escape.txt",
                                        "text/plain",
                                        "unsafe".getBytes(StandardCharsets.UTF_8),
                                        null)
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        multipart(
                                        base,
                                        adminToken,
                                        "too-large.txt",
                                        "text/plain",
                                        new byte[20 * 1024 * 1024 + 1],
                                        null)
                                .statusCode())
                .isEqualTo(413);

        HttpResponse<String> brokenPdf =
                multipart(
                        base,
                        adminToken,
                        "broken.pdf",
                        "application/pdf",
                        "%PDF-1.4\nsynthetic invalid body".getBytes(StandardCharsets.US_ASCII),
                        null);
        assertThat(brokenPdf.statusCode()).isEqualTo(202);
        UUID brokenDocument = UUID.fromString(jsonString(brokenPdf.body(), "documentId"));
        HttpResponse<String> failed =
                awaitIngestion(base + "/" + brokenDocument, adminToken, "FAILED");
        assertThat(failed.body())
                .contains("\"failureCode\":\"PARSER_FAILURE\"")
                .doesNotContain("Exception", "stackTrace");
    }

    @Test
    void immutableVersionsRejectCurrentBinaryDuplicates() throws Exception {
        String adminToken = token(ADMIN_SUBJECT, List.of("TENANT_ADMIN"), futureExpiry());
        String base = "/api/v1/organizations/acme/workspaces/operations/documents";
        byte[] first = "immutable version one".getBytes(StandardCharsets.UTF_8);
        byte[] second = "immutable version two".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> initial =
                multipart(base, adminToken, "version.txt", "text/plain", first, null);
        UUID documentId = UUID.fromString(jsonString(initial.body(), "documentId"));
        UUID firstVersionId = UUID.fromString(jsonString(initial.body(), "versionId"));
        String versionsPath = base + "/" + documentId + "/versions";
        awaitIngestion(base + "/" + documentId, adminToken, "READY");

        HttpResponse<String> duplicate =
                multipart(versionsPath, adminToken, "copy.txt", "text/plain", first, null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("DUPLICATE_CURRENT_VERSION");

        HttpResponse<String> next =
                multipart(versionsPath, adminToken, "version.txt", "text/plain", second, null);
        assertThat(next.statusCode()).isEqualTo(202);
        UUID secondVersionId = UUID.fromString(jsonString(next.body(), "versionId"));
        awaitVersion(base + "/" + documentId, adminToken, secondVersionId, "READY");
        HttpResponse<String> versions = get(versionsPath, adminToken);
        assertThat(versions.body())
                .contains(firstVersionId.toString(), secondVersionId.toString())
                .contains("\"versionNumber\":1", "\"versionNumber\":2")
                .contains(sha256(first), sha256(second));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM document_versions WHERE document_id = ?",
                                Integer.class,
                                documentId))
                .isEqualTo(2);
    }

    @Test
    void failedIngestionRetryCountIsDurablyBounded() throws Exception {
        String adminToken = token(ADMIN_SUBJECT, List.of("TENANT_ADMIN"), futureExpiry());
        String base = "/api/v1/organizations/acme/workspaces/operations/documents";
        HttpResponse<String> accepted =
                multipart(
                        base,
                        adminToken,
                        "retry.pdf",
                        "application/pdf",
                        "%PDF-1.4\ninvalid retry fixture".getBytes(StandardCharsets.US_ASCII),
                        null);
        UUID documentId = UUID.fromString(jsonString(accepted.body(), "documentId"));
        UUID versionId = UUID.fromString(jsonString(accepted.body(), "versionId"));
        String detailPath = base + "/" + documentId;
        String retryPath = detailPath + "/versions/" + versionId + "/retry";

        awaitIngestion(detailPath, adminToken, "FAILED");
        assertDocumentStatus(documentId, "ACTIVE");
        for (int expectedAttempt = 2; expectedAttempt <= 3; expectedAttempt++) {
            assertThat(post(retryPath, adminToken).statusCode()).isEqualTo(200);
            awaitFailedAttempt(detailPath, adminToken, versionId, expectedAttempt);
        }
        assertThat(post(retryPath, adminToken).statusCode()).isEqualTo(409);
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
    void testProfileExplicitlyLoadsFixturesAndCreatesConstrainedTenantSchema() {
        assertThat(environment.getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration,classpath:db/devdata");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspaces", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profiles", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Integer.class))
                .isEqualTo(3);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history"
                                        + " WHERE version = '900' AND success",
                                Integer.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT MAX(version::integer) FROM flyway_schema_history"
                                        + " WHERE success AND version ~ '^[0-9]+$'",
                                Integer.class))
                .isEqualTo(3);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history"
                                        + " WHERE version IS NULL"
                                        + " AND description = 'synthetic identity fixtures'"
                                        + " AND success",
                                Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO organizations"
                                                + " (id, slug, display_name) VALUES (?, 'acme', 'Duplicate')",
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productionMigrationLocationCreatesSchemaWithoutSyntheticData() throws Exception {
        try (PostgreSQLContainer<?> schemaOnlyPostgres =
                new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                        .withDatabaseName("enterprise_ai_schema_only")
                        .withUsername("enterprise_ai_schema_only")
                        .withPassword("synthetic_schema_only_password")) {
            schemaOnlyPostgres.start();

            Flyway.configure()
                    .dataSource(
                            schemaOnlyPostgres.getJdbcUrl(),
                            schemaOnlyPostgres.getUsername(),
                            schemaOnlyPostgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection =
                    DriverManager.getConnection(
                            schemaOnlyPostgres.getJdbcUrl(),
                            schemaOnlyPostgres.getUsername(),
                            schemaOnlyPostgres.getPassword())) {
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'"))
                        .isEqualTo(1);
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM organizations")).isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM workspaces")).isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM user_profiles")).isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM memberships")).isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM documents")).isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM document_versions"))
                        .isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM document_ingestion_jobs"))
                        .isZero();
                assertThat(queryForInt(connection, "SELECT COUNT(*) FROM document_text_units"))
                        .isZero();
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM flyway_schema_history"
                                                + " WHERE version IN ('1', '2', '3') AND success"))
                        .isEqualTo(3);
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM flyway_schema_history"
                                                + " WHERE version = '900' AND success"))
                        .isZero();
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT MAX(version::integer) FROM flyway_schema_history"
                                                + " WHERE success AND version ~ '^[0-9]+$'"))
                        .isEqualTo(3);
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM flyway_schema_history"
                                                + " WHERE version IS NULL AND success"))
                        .isZero();
            }
        }
    }

    @Test
    void repeatableFixtureCanMigrateAgainWithoutDuplicatingRecords() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspaces", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profiles", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void futureVersionedMigrationRunsAfterRepeatableFixture(@TempDir Path futureMigrationLocation)
            throws Exception {
        try (PostgreSQLContainer<?> futureMigrationPostgres =
                new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                        .withDatabaseName("enterprise_ai_future_migration")
                        .withUsername("enterprise_ai_future_migration")
                        .withPassword("synthetic_future_migration_password")) {
            futureMigrationPostgres.start();

            Flyway.configure()
                    .dataSource(
                            futureMigrationPostgres.getJdbcUrl(),
                            futureMigrationPostgres.getUsername(),
                            futureMigrationPostgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/devdata")
                    .load()
                    .migrate();

            try (Connection connection =
                    DriverManager.getConnection(
                            futureMigrationPostgres.getJdbcUrl(),
                            futureMigrationPostgres.getUsername(),
                            futureMigrationPostgres.getPassword())) {
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT MAX(version::integer) FROM flyway_schema_history"
                                                + " WHERE success AND version ~ '^[0-9]+$'"))
                        .isEqualTo(3);
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM flyway_schema_history"
                                                + " WHERE version IS NULL"
                                                + " AND description = 'synthetic identity fixtures'"
                                                + " AND success"))
                        .isEqualTo(1);
            }

            Files.writeString(
                    futureMigrationLocation.resolve("V4__future_migration_probe.sql"),
                    "CREATE TABLE future_migration_probe (id INTEGER PRIMARY KEY);\n",
                    StandardCharsets.UTF_8);

            Flyway.configure()
                    .dataSource(
                            futureMigrationPostgres.getJdbcUrl(),
                            futureMigrationPostgres.getUsername(),
                            futureMigrationPostgres.getPassword())
                    .locations(
                            "classpath:db/migration",
                            "classpath:db/devdata",
                            "filesystem:" + futureMigrationLocation.toAbsolutePath())
                    .load()
                    .migrate();

            try (Connection connection =
                    DriverManager.getConnection(
                            futureMigrationPostgres.getJdbcUrl(),
                            futureMigrationPostgres.getUsername(),
                            futureMigrationPostgres.getPassword())) {
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM flyway_schema_history"
                                                + " WHERE version = '4' AND success"))
                        .isEqualTo(1);
                assertThat(
                                queryForInt(
                                        connection,
                                        "SELECT COUNT(*) FROM information_schema.tables"
                                                + " WHERE table_schema = 'public'"
                                                + " AND table_name = 'future_migration_probe'"))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void workspaceMembershipAcceptsWorkspaceFromSameOrganization() {
        UUID profileId = insertTemporaryProfile("valid-workspace");
        try {
            assertThat(
                            jdbcTemplate.update(
                                    "INSERT INTO memberships"
                                            + " (id, user_profile_id, organization_id, workspace_id, role)"
                                            + " VALUES (?, ?, ?, ?, 'MEMBER')",
                                    UUID.randomUUID(),
                                    profileId,
                                    ACME_ORGANIZATION_ID,
                                    ACME_WORKSPACE_ID))
                    .isEqualTo(1);
        } finally {
            deleteTemporaryProfile(profileId);
        }
    }

    @Test
    void organizationMembershipStillAcceptsNullWorkspace() {
        UUID profileId = insertTemporaryProfile("organization-level");
        try {
            assertThat(
                            jdbcTemplate.update(
                                    "INSERT INTO memberships"
                                            + " (id, user_profile_id, organization_id, workspace_id, role)"
                                            + " VALUES (?, ?, ?, NULL, 'MEMBER')",
                                    UUID.randomUUID(),
                                    profileId,
                                    ACME_ORGANIZATION_ID))
                    .isEqualTo(1);
        } finally {
            deleteTemporaryProfile(profileId);
        }
    }

    @Test
    void workspaceMembershipRejectsWorkspaceFromDifferentOrganization() {
        UUID profileId = insertTemporaryProfile("invalid-workspace");
        try {
            assertThatThrownBy(
                            () ->
                                    jdbcTemplate.update(
                                            "INSERT INTO memberships"
                                                    + " (id, user_profile_id, organization_id, workspace_id, role)"
                                                    + " VALUES (?, ?, ?, ?, 'MEMBER')",
                                            UUID.randomUUID(),
                                            profileId,
                                            ACME_ORGANIZATION_ID,
                                            GLOBEX_WORKSPACE_ID))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("memberships_workspace_organization_fk");
        } finally {
            deleteTemporaryProfile(profileId);
        }
    }

    @Test
    void workspaceResolverPermitsReadButRejectsUploadForMember() {
        JwtAuthenticationToken member = authentication(MEMBER_SUBJECT, "MEMBER");

        assertThat(
                        workspaceAuthorizationService
                                .requireAccess(
                                        member,
                                        "acme",
                                        "operations",
                                        WorkspaceOperation.READ_METADATA)
                                .accessRole())
                .isEqualTo(WorkspaceAccessRole.MEMBER);
        assertThatThrownBy(
                        () ->
                                workspaceAuthorizationService.requireAccess(
                                        member,
                                        "acme",
                                        "operations",
                                        WorkspaceOperation.UPLOAD_DOCUMENT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void workspaceResolverPermitsTenantAdministratorUpload() {
        assertThat(
                        workspaceAuthorizationService
                                .requireAccess(
                                        authentication(ADMIN_SUBJECT, "TENANT_ADMIN"),
                                        "acme",
                                        "operations",
                                        WorkspaceOperation.UPLOAD_DOCUMENT)
                                .accessRole())
                .isEqualTo(WorkspaceAccessRole.TENANT_ADMIN);
    }

    @Test
    void workspaceResolverRejectsOrganizationWorkspaceConfusion() {
        assertThatThrownBy(
                        () ->
                                workspaceAuthorizationService.requireAccess(
                                        authentication(MEMBER_SUBJECT, "MEMBER"),
                                        "acme",
                                        "research",
                                        WorkspaceOperation.READ_METADATA))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void documentWorkspaceOrganizationMismatchIsRejected() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO documents"
                                                + " (id, organization_id, workspace_id, title, status,"
                                                + " created_by_subject) VALUES (?, ?, ?, 'Invalid',"
                                                + " 'ACTIVE', 'integration-test')",
                                        UUID.randomUUID(),
                                        ACME_ORGANIZATION_ID,
                                        GLOBEX_WORKSPACE_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("documents_workspace_organization_fk");
    }

    @Test
    void documentVersionNumberIsUniqueWithinDocument() {
        UUID documentId = UUID.randomUUID();
        UUID firstVersionId = UUID.randomUUID();
        try {
            insertTestDocument(documentId);
            insertTestVersion(firstVersionId, documentId, 1, "a");

            assertThatThrownBy(() -> insertTestVersion(UUID.randomUUID(), documentId, 1, "b"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("document_versions_number_unique");
        } finally {
            deleteTestDocument(documentId);
        }
    }

    @Test
    void ingestionJobCannotReferenceVersionUnderDifferentTenant() {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        try {
            insertTestDocument(documentId);
            insertTestVersion(versionId, documentId, 1, "c");

            assertThatThrownBy(
                            () ->
                                    jdbcTemplate.update(
                                            "INSERT INTO document_ingestion_jobs"
                                                    + " (id, document_version_id, organization_id,"
                                                    + " workspace_id, status) VALUES (?, ?, ?, ?, 'QUEUED')",
                                            UUID.randomUUID(),
                                            versionId,
                                            GLOBEX_ORGANIZATION_ID,
                                            GLOBEX_WORKSPACE_ID))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("document_ingestion_jobs_version_tenant_fk");
        } finally {
            deleteTestDocument(documentId);
        }
    }

    @Test
    void textUnitOrdinalIsUniqueWithinVersion() {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        try {
            insertTestDocument(documentId);
            insertTestVersion(versionId, documentId, 1, "d");
            insertTestTextUnit(UUID.randomUUID(), versionId, 1, "First unit");

            assertThatThrownBy(
                            () ->
                                    insertTestTextUnit(
                                            UUID.randomUUID(), versionId, 1, "Duplicate ordinal"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("document_text_units_order_unique");
        } finally {
            deleteTestDocument(documentId);
        }
    }

    private HttpResponse<String> multipart(
            String path,
            String accessToken,
            String filename,
            String contentType,
            byte[] content,
            String title)
            throws IOException, InterruptedException {
        String boundary = "codex-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (title != null) {
            body.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(
                    "Content-Disposition: form-data; name=\"title\"\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
            body.writeBytes(title.getBytes(StandardCharsets.UTF_8));
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(
                ("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(
                ("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody());
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET();
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> awaitIngestion(
            String detailPath, String accessToken, String expectedStatus)
            throws IOException, InterruptedException {
        HttpResponse<String> response = get(detailPath, accessToken);
        for (int attempt = 0;
                attempt < 100
                        && !response.body()
                                .contains("\"ingestionStatus\":\"" + expectedStatus + "\"");
                attempt++) {
            Thread.sleep(100);
            response = get(detailPath, accessToken);
        }
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"ingestionStatus\":\"" + expectedStatus + "\"");
        return response;
    }

    private void awaitVersion(
            String detailPath, String accessToken, UUID versionId, String expectedStatus)
            throws IOException, InterruptedException {
        Pattern expected =
                Pattern.compile(
                        "\\\"id\\\":\\\""
                                + Pattern.quote(versionId.toString())
                                + "\\\".*?\\\"ingestionStatus\\\":\\\""
                                + expectedStatus
                                + "\\\"");
        HttpResponse<String> response = get(detailPath, accessToken);
        for (int attempt = 0;
                attempt < 100 && !expected.matcher(response.body()).find();
                attempt++) {
            Thread.sleep(100);
            response = get(detailPath, accessToken);
        }
        assertThat(expected.matcher(response.body()).find()).isTrue();
    }

    private void awaitFailedAttempt(
            String detailPath, String accessToken, UUID versionId, int expectedAttempt)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Integer actualAttempt =
                    jdbcTemplate.queryForObject(
                            "SELECT attempt_count FROM document_ingestion_jobs"
                                    + " WHERE document_version_id = ?",
                            Integer.class,
                            versionId);
            String status =
                    jdbcTemplate.queryForObject(
                            "SELECT ingestion_status FROM document_versions WHERE id = ?",
                            String.class,
                            versionId);
            if (actualAttempt == expectedAttempt && status.equals("FAILED")) {
                assertThat(get(detailPath, accessToken).body())
                        .contains("\"ingestionStatus\":\"FAILED\"");
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Ingestion did not fail at attempt " + expectedAttempt);
    }

    private static String jsonString(String json, String field) {
        Matcher matcher =
                Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":\\\"([^\\\"]+)\\\"")
                        .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + field + " in " + json);
        }
        return matcher.group(1);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] syntheticPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int pageNumber = 1; pageNumber <= 2; pageNumber++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(text + " page " + pageNumber);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
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

    private UUID insertTemporaryProfile(String label) {
        UUID profileId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_profiles"
                        + " (id, identity_subject, email, display_name) VALUES (?, ?, ?, ?)",
                profileId,
                "integration-" + label + "-" + profileId,
                label + "-" + profileId + "@example.test",
                "Integration Test Profile");
        return profileId;
    }

    private void deleteTemporaryProfile(UUID profileId) {
        jdbcTemplate.update("DELETE FROM user_profiles WHERE id = ?", profileId);
    }

    private void insertTestDocument(UUID documentId) {
        jdbcTemplate.update(
                "INSERT INTO documents"
                        + " (id, organization_id, workspace_id, title, status, created_by_subject)"
                        + " VALUES (?, ?, ?, 'Integration document', 'ACTIVE', 'integration-test')",
                documentId,
                ACME_ORGANIZATION_ID,
                ACME_WORKSPACE_ID);
    }

    private void insertTestVersion(
            UUID versionId, UUID documentId, int versionNumber, String keySuffix) {
        jdbcTemplate.update(
                "INSERT INTO document_versions"
                        + " (id, document_id, organization_id, workspace_id, version_number,"
                        + " original_filename, declared_content_type, detected_content_type,"
                        + " byte_size, sha256_hex, object_key, ingestion_status, created_by_subject)"
                        + " VALUES (?, ?, ?, ?, ?, 'fixture.txt', 'text/plain', 'text/plain',"
                        + " 7, ?, ?, 'STORED', 'integration-test')",
                versionId,
                documentId,
                ACME_ORGANIZATION_ID,
                ACME_WORKSPACE_ID,
                versionNumber,
                "a".repeat(64),
                "integration/" + versionId + "/" + keySuffix);
    }

    private void insertTestTextUnit(UUID unitId, UUID versionId, int ordinal, String content) {
        jdbcTemplate.update(
                "INSERT INTO document_text_units"
                        + " (id, document_version_id, organization_id, workspace_id, ordinal,"
                        + " locator_type, locator_value, text_content, character_count)"
                        + " VALUES (?, ?, ?, ?, ?, 'DOCUMENT', 'body', ?, ?)",
                unitId,
                versionId,
                ACME_ORGANIZATION_ID,
                ACME_WORKSPACE_ID,
                ordinal,
                content,
                content.length());
    }

    private void insertQueuedTestJob(UUID jobId, UUID versionId) {
        jdbcTemplate.update(
                "INSERT INTO document_ingestion_jobs"
                        + " (id, document_version_id, organization_id, workspace_id, status,"
                        + " attempt_count, next_attempt_at)"
                        + " VALUES (?, ?, ?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                jobId,
                versionId,
                ACME_ORGANIZATION_ID,
                ACME_WORKSPACE_ID);
    }

    private void assertDocumentStatus(UUID documentId, String expectedStatus) {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM documents WHERE id = ?",
                                String.class,
                                documentId))
                .isEqualTo(expectedStatus);
    }

    private void deleteTestDocument(UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM document_text_units WHERE document_version_id IN"
                        + " (SELECT id FROM document_versions WHERE document_id = ?)",
                documentId);
        jdbcTemplate.update(
                "DELETE FROM document_ingestion_jobs WHERE document_version_id IN"
                        + " (SELECT id FROM document_versions WHERE document_id = ?)",
                documentId);
        jdbcTemplate.update("DELETE FROM document_versions WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", documentId);
    }

    private static JwtAuthenticationToken authentication(String subject, String role) {
        Instant now = Instant.now();
        Jwt jwt =
                Jwt.withTokenValue("synthetic-integration-token")
                        .header("alg", "RS256")
                        .issuer(ISSUER)
                        .subject(subject)
                        .audience(List.of(AUDIENCE))
                        .issuedAt(now.minusSeconds(1))
                        .expiresAt(now.plusSeconds(300))
                        .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
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
