package com.iovchukandrew.dropvox.metadata;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.db.FlywayRunner;
import com.iovchukandrew.dropvox.metadata.db.PgPoolCreator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignerFactory;
import com.iovchukandrew.dropvox.metadata.server.HttpHeader;
import com.iovchukandrew.dropvox.metadata.server.Server;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class IntegrationTest {
    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin";
    private static final String S3_BUCKET = "files";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1"))
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live")
                    .forPort(9000)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));

    private static Vertx vertx;
    private static Pool pool;
    private static S3Presigner s3Presigner;
    private static String serverBaseUrl;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();
        int serverPort = randomAvailablePort();

        JsonObject config = new JsonObject()
                .put("server.port", serverPort)
                .put("db.host", POSTGRES.getHost())
                .put("db.port", POSTGRES.getMappedPort(5432))
                .put("db.database", POSTGRES.getDatabaseName())
                .put("db.user", POSTGRES.getUsername())
                .put("db.password", POSTGRES.getPassword())
                .put("db.pool.maxSize", 5)
                .put("db.scheme", "metadata")
                .put("s3.endpoint", "http://%s:%s".formatted(MINIO.getHost(), MINIO.getMappedPort(9000)))
                .put("s3.accessKey", MINIO_ACCESS_KEY)
                .put("s3.secretKey", MINIO_SECRET_KEY)
                .put("s3.bucket", S3_BUCKET)
                .put("s3.region", "us-east-1")
                .put("s3.pathStyle", true);

        new FlywayRunner().runMigration(config);
        createS3Bucket(config);

        pool = PgPoolCreator.create(vertx, config);
        FilesDAO filesDAO = new FilesDAO(pool);
        s3Presigner = S3PresignerFactory.create(config);
        S3PresignedUrlGenerator s3PresignedUrlGenerator = new S3PresignedUrlGenerator(s3Presigner);

        vertx.deployVerticle(new Server(filesDAO, s3PresignedUrlGenerator, config))
                .await(10, TimeUnit.SECONDS);

        serverBaseUrl = "http://localhost:" + serverPort;
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pool != null) {
            pool.close().await(10, TimeUnit.SECONDS);
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
        if (vertx != null) {
            vertx.close().await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldUploadAndDownloadFileUsingPostgresAndMinio() throws Exception {
        UUID userId = UUID.randomUUID();
        byte[] fileContent = "dropvox integration payload".getBytes(StandardCharsets.UTF_8);

        // Initialize upload and create metadata in DB with PENDING status.
        HttpResponse<String> initResponse = postJson(
                serverBaseUrl + "/files/init",
                userId,
                new JsonObject()
                        .put("filename", "voice test.wav")
                        .put("size", fileContent.length)
                        .put("contentType", "audio/wav")
                        .encode()
        );

        assertThat(initResponse.statusCode()).isEqualTo(200);
        JsonObject initBody = new JsonObject(initResponse.body());
        assertThat(initBody.getString("bucket")).isEqualTo(S3_BUCKET);
        assertThat(initBody.getString("uploadUrl")).isNotBlank();

        UUID fileId = UUID.fromString(initBody.getString("fileId"));
        JsonObject fileAfterInit = fetchFileRecord(fileId, userId);
        assertThat(fileAfterInit.getString("status")).isEqualTo("PENDING");
        assertThat(fileAfterInit.getString("name")).isEqualTo("voice test.wav");
        assertThat(fileAfterInit.getLong("size")).isEqualTo((long) fileContent.length);
        assertThat(fileAfterInit.getString("contentType")).isEqualTo("audio/wav");
        assertThat(fileAfterInit.getString("bucket")).isEqualTo(S3_BUCKET);
        assertThat(fileAfterInit.getString("s3Key")).isNotBlank();

        // Upload content directly to MinIO using presigned PUT URL.
        HttpResponse<byte[]> uploadResponse = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(initBody.getString("uploadUrl")))
                        .header("Content-Type", "audio/wav")
                        .timeout(Duration.ofSeconds(10))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(uploadResponse.statusCode()).isEqualTo(200);

        // Confirm uploading by DB status transitions from PENDING to UPLOADED.
        HttpResponse<String> completeResponse = postWithoutBody(
                serverBaseUrl + "/files/complete/" + fileId,
                userId
        );
        assertThat(completeResponse.statusCode()).isEqualTo(200);
        JsonObject fileAfterComplete = fetchFileRecord(fileId, userId);
        assertThat(fileAfterComplete.getString("status")).isEqualTo("UPLOADED");

        // Request download metadata and receive presigned GET URL.
        HttpResponse<String> downloadResponse = get(
                serverBaseUrl + "/files/" + fileId,
                userId
        );
        assertThat(downloadResponse.statusCode()).isEqualTo(200);
        JsonObject downloadBody = new JsonObject(downloadResponse.body());
        assertThat(downloadBody.getString("downloadUrl")).isNotBlank();
        JsonObject fileAfterDownloadLookup = fetchFileRecord(fileId, userId);
        assertThat(fileAfterDownloadLookup.getString("status")).isEqualTo("UPLOADED");

        // Download file bytes from MinIO using presigned GET URL.
        HttpResponse<byte[]> fetchedFile = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(downloadBody.getString("downloadUrl")))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(fetchedFile.statusCode()).isEqualTo(200);
        assertThat(fetchedFile.body()).isEqualTo(fileContent);
    }

    private static JsonObject fetchFileRecord(UUID fileId, UUID userId) throws Exception {
        var rows = pool
                .preparedQuery("""
                        SELECT id, owner_id, name, size, content_type, status, bucket, s3_key
                        FROM files
                        WHERE id = $1 AND owner_id = $2
                        """)
                .execute(Tuple.of(fileId, userId))
                .await(10, TimeUnit.SECONDS);

        assertThat(rows.size()).isEqualTo(1);
        var row = rows.iterator().next();

        return new JsonObject()
                .put("fileId", row.getUUID("id").toString())
                .put("ownerId", row.getUUID("owner_id").toString())
                .put("name", row.getString("name"))
                .put("size", row.getLong("size"))
                .put("contentType", row.getString("content_type"))
                .put("status", row.getString("status"))
                .put("bucket", row.getString("bucket"))
                .put("s3Key", row.getString("s3_key"));
    }

    private static HttpResponse<String> postJson(String url, UUID userId, String body) throws Exception {
        return HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header(HttpHeader.USER_ID, userId.toString())
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postWithoutBody(String url, UUID userId) throws Exception {
        return HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header(HttpHeader.USER_ID, userId.toString())
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> get(String url, UUID userId) throws Exception {
        return HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header(HttpHeader.USER_ID, userId.toString())
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static void createS3Bucket(JsonObject config) {
        try (S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(config.getString("s3.endpoint")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)))
                .region(Region.of(config.getString("s3.region")))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build()) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(S3_BUCKET).build());
        }
    }

    private static int randomAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
