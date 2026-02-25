package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FilesDAOTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    private static Vertx vertx;
    private static Pool pool;
    private static FilesDAO filesDAO;

    @BeforeAll
    static void setUp() {
        JsonObject config = createDbConfig();
        new FlywayRunner().runMigration(config);

        vertx = Vertx.vertx();
        pool = PgPoolCreator.create(vertx, config);
        filesDAO = new FilesDAO(pool);
    }

    @BeforeEach
    void cleanDb() throws Exception {
        pool.query("TRUNCATE TABLE files").execute()
                .await(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pool != null) {
            pool.close().await(10, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            vertx.close().await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldReturnMetadataForExistingFileAndOwner() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        pool.preparedQuery("""
                        INSERT INTO files (id, name, size, content_type, owner_id, status, bucket, s3_key)
                        VALUES ($1, $2, $3, $4, $5, 'UPLOADED', $6, $7)
                        """)
                .execute(Tuple.of(
                        fileId,
                        "voice.wav",
                        12345L,
                        "audio/wav",
                        ownerId,
                        "dropvox-files",
                        "users/" + ownerId + "/voice.wav"
                ))
                .await(10, TimeUnit.SECONDS);

        JsonObject file = filesDAO.findFileByIdAndOwner(fileId, ownerId)
                .await(10, TimeUnit.SECONDS);

        assertThat(file.getString("name")).isEqualTo("voice.wav");
        assertThat(file.getLong("size")).isEqualTo(12345L);
        assertThat(file.getString("contentType")).isEqualTo("audio/wav");
        assertThat(file.getString("bucket")).isEqualTo("dropvox-files");
        assertThat(file.getString("s3Key")).isEqualTo("users/" + ownerId + "/voice.wav");
        assertThat(file.getString("fileId")).isEqualTo(fileId.toString());
        assertThat(file.getString("ownerId")).isEqualTo(ownerId.toString());
        assertThat(file.getString("uploadedAt")).isNotBlank();
        assertThat(file.getString("lastModifiedAt")).isNotBlank();
    }

    @Test
    void shouldFailWhenFileIsNotFound() {
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        assertThatThrownBy(() -> filesDAO.findFileByIdAndOwner(fileId, ownerId)
                .await(10, TimeUnit.SECONDS))
                .hasMessage("File not found by {fileId=%s, ownerId=%s}".formatted(fileId, ownerId));
    }

    @Test
    void shouldCreatePendingFileMetadata() throws Exception {
        UUID ownerId = UUID.randomUUID();

        JsonObject file = filesDAO.createPendingFile(
                        "voice.wav",
                        12345L,
                        "audio/wav",
                        ownerId,
                        "dropvox-files",
                        "users/" + ownerId + "/voice.wav"
                )
                .await(10, TimeUnit.SECONDS);

        assertThat(file.getString("name")).isEqualTo("voice.wav");
        assertThat(file.getLong("size")).isEqualTo(12345L);
        assertThat(file.getString("contentType")).isEqualTo("audio/wav");
        assertThat(file.getString("bucket")).isEqualTo("dropvox-files");
        assertThat(file.getString("s3Key")).isEqualTo("users/" + ownerId + "/voice.wav");
        assertThat(file.getString("fileId")).isNotBlank();
        assertThat(file.getString("ownerId")).isEqualTo(ownerId.toString());
        assertThat(file.getString("uploadedAt")).isNotBlank();
        assertThat(file.getString("lastModifiedAt")).isNotBlank();

        UUID fileId = UUID.fromString(file.getString("fileId"));
        var rows = pool.preparedQuery("SELECT status FROM files WHERE id = $1 AND owner_id = $2")
                .execute(Tuple.of(fileId, ownerId))
                .await(10, TimeUnit.SECONDS);

        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.iterator().next().getString("status")).isEqualTo("PENDING");
    }

    @Test
    void shouldConfirmPendingFileUpload() throws Exception {
        UUID ownerId = UUID.randomUUID();
        JsonObject pendingFile = filesDAO.createPendingFile(
                        "voice.wav",
                        12345L,
                        "audio/wav",
                        ownerId,
                        "dropvox-files",
                        "users/" + ownerId + "/voice.wav"
                )
                .await(10, TimeUnit.SECONDS);
        UUID fileId = UUID.fromString(pendingFile.getString("fileId"));

        JsonObject confirmedFile = filesDAO.confirmFileUpload(fileId, ownerId)
                .await(10, TimeUnit.SECONDS);

        assertThat(confirmedFile.getString("fileId")).isEqualTo(fileId.toString());
        assertThat(confirmedFile.getString("ownerId")).isEqualTo(ownerId.toString());
        assertThat(confirmedFile.getString("uploadedAt")).isNotBlank();
        assertThat(confirmedFile.getString("lastModifiedAt")).isNotBlank();

        var rows = pool.preparedQuery("SELECT status FROM files WHERE id = $1 AND owner_id = $2")
                .execute(Tuple.of(fileId, ownerId))
                .await(10, TimeUnit.SECONDS);

        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.iterator().next().getString("status")).isEqualTo("UPLOADED");
    }

    @Test
    void shouldFailToConfirmFileUploadWhenPendingFileIsNotFound() {
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        assertThatThrownBy(() -> filesDAO.confirmFileUpload(fileId, ownerId)
                .await(10, TimeUnit.SECONDS))
                .hasMessage("No pending file metadata was found by {fileId=%s, ownerId=%s}".formatted(fileId, ownerId));
    }

    private static JsonObject createDbConfig() {
        return new JsonObject()
                .put("db.host", postgres.getHost())
                .put("db.port", postgres.getMappedPort(5432))
                .put("db.database", postgres.getDatabaseName())
                .put("db.user", postgres.getUsername())
                .put("db.password", postgres.getPassword())
                .put("db.scheme", "metadata")
                .put("db.pool.maxSize", 5);
    }
}
