package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access object for file metadata.
 */
public class FilesDAO {
    private static final Logger log = LoggerFactory.getLogger(FilesDAO.class);

    public enum FileStatus {
        PENDING,
        UPLOADED,
        FAILED
    }

    private final Vertx vertx;
    private final Pool pool;

    public FilesDAO(Vertx vertx, Pool pool) {
        this.vertx = vertx;
        this.pool = pool;
    }

    /**
     * Retrieves file metadata for a given file ID and owner ID.
     *
     * @param fileId  the file identifier
     * @param ownerId the file owner identifier
     * @return Future containing file metadata as JsonObject
     */
    public Future<JsonObject> findFileByIdAndOwner(UUID fileId, UUID ownerId) {
        return findFileByIdAndOwner(fileId, ownerId, FileStatus.UPLOADED);
    }

    /**
     * Retrieves file metadata for a given file ID, owner ID and status.
     *
     * @param fileId  the file identifier
     * @param ownerId the file owner identifier
     * @param status  expected file status
     * @return Future containing file metadata as JsonObject
     */
    public Future<JsonObject> findFileByIdAndOwner(UUID fileId, UUID ownerId, FileStatus status) {
        log.info("Retrieving file metadata by {fileId={}, ownerId={}, status={}}", fileId, ownerId, status);

        String sql = "SELECT id, name, size, content_type, owner_id, status, bucket, s3_key, created_at, updated_at " +
                "FROM files WHERE id = $1 AND owner_id = $2 AND status = $3";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId, status.name()))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(new FileMetadataNotFoundException(
                                String.format("File not found by {fileId=%s, ownerId=%s}", fileId, ownerId)));
                    }
                    if (rows.size() > 1) {
                        return Future.failedFuture(new FileMetadataInvariantViolationException(
                                String.format("Expected exactly 1 file by {fileId=%s, ownerId=%s}, but got %s",
                                        fileId, ownerId, rows.size())));
                    }
                    return mapRowToJsonAsync(rows.iterator().next());
                });
    }

    /**
     * Retrieves file metadata for a given file ID and owner ID regardless of upload status.
     *
     * @param fileId  the file identifier
     * @param ownerId the file owner identifier
     * @return Future containing file metadata as JsonObject
     */
    public Future<JsonObject> findFileByIdAndOwnerAnyStatus(UUID fileId, UUID ownerId) {
        log.info("Retrieving file metadata by {fileId={}, ownerId={}} regardless of status", fileId, ownerId);

        String sql = "SELECT id, name, size, content_type, owner_id, status, bucket, s3_key, created_at, updated_at " +
                "FROM files WHERE id = $1 AND owner_id = $2";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(new FileMetadataNotFoundException(
                                String.format("File not found by {fileId=%s, ownerId=%s}", fileId, ownerId)));
                    }
                    if (rows.size() > 1) {
                        return Future.failedFuture(new FileMetadataInvariantViolationException(
                                String.format("Expected exactly 1 file by {fileId=%s, ownerId=%s}, but got %s",
                                        fileId, ownerId, rows.size())));
                    }
                    return mapRowToJsonAsync(rows.iterator().next());
                });
    }

    /**
     * Retrieves pending file metadata for a given file ID and owner ID.
     *
     * @param fileId  the file identifier
     * @param ownerId the file owner identifier
     * @return Future containing pending file metadata as JsonObject
     */
    public Future<JsonObject> findPendingFileByIdAndOwner(UUID fileId, UUID ownerId) {
        return findFileByIdAndOwner(fileId, ownerId, FileStatus.PENDING);
    }

    /**
     * Creates file metadata in {@code PENDING} status before the actual object upload is confirmed.
     *
     * @param filename    original file name
     * @param size        file size in bytes
     * @param contentType MIME type of the file
     * @param ownerId     owner identifier
     * @param bucket      storage bucket name
     * @param s3Key       object key in storage
     * @return Future containing created file metadata
     */
    public Future<JsonObject> createPendingFile(
            String filename, long size, String contentType, UUID ownerId, String bucket, String s3Key
    ) {
        log.info("Creating pending file metadata by {filename={}, size={} contentType={}, ownerId={}, bucket={}}",
                filename, size, contentType, ownerId, bucket);

        String sql = "INSERT INTO files (id, name, size, content_type, owner_id, status, bucket, s3_key) " +
                "VALUES ($1, $2, $3, $4, $5, $6, $7, $8) " +
                "RETURNING id, name, size, content_type, owner_id, status, bucket, s3_key, created_at, updated_at";

        UUID fileId = UUID.randomUUID();
        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, filename, size, contentType, ownerId, FileStatus.PENDING.name(), bucket, s3Key))
                .compose(rows -> {
                    if (rows.size() != 1) {
                        return Future.failedFuture("Expected to insert single pending file metadata, but got " + rows.size());
                    }
                    return mapRowToJsonAsync(rows.iterator().next());
                });
    }

    /**
     * Confirms upload for an existing pending file and transitions its status to {@code UPLOADED}.
     *
     * @param fileId  file identifier
     * @param ownerId file owner identifier
     * @return Future containing updated file metadata
     */
    public Future<JsonObject> confirmFileUpload(UUID fileId, UUID ownerId) {
        log.info("Updating file metadata of uploaded file by {fileId={}, ownerId={}}",
                fileId, ownerId);

        String sql = "UPDATE files SET status = $3 " +
                "WHERE id = $1 AND owner_id = $2 AND status = $4 " +
                "RETURNING id, name, size, content_type, owner_id, bucket, s3_key, status, created_at, updated_at";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId, FileStatus.UPLOADED.name(), FileStatus.PENDING.name()))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(new FileMetadataNotFoundException(
                                String.format("No pending file metadata was found by {fileId=%s, ownerId=%s}", fileId, ownerId)));
                    }
                    return mapRowToJsonAsync(rows.iterator().next());
                });
    }

    /**
     * Confirms upload for a pending file matched by bucket and object key.
     *
     * @param bucket storage bucket name
     * @param s3Key  object key in storage
     * @return Future containing updated file metadata
     */
    public Future<JsonObject> confirmPendingFileUploadByObjectLocation(String bucket, String s3Key) {
        log.info("Updating pending file metadata by object location {bucket={}, s3Key={}}", bucket, s3Key);

        String sql = "SELECT id, owner_id FROM files WHERE bucket = $1 AND s3_key = $2 AND status = $3";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(bucket, s3Key, FileStatus.PENDING.name()))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(new FileMetadataNotFoundException(
                                String.format("No pending file metadata was found by {bucket=%s, s3Key=%s}", bucket, s3Key)));
                    }
                    if (rows.size() > 1) {
                        return Future.failedFuture(new FileMetadataInvariantViolationException(
                                String.format("Expected exactly 1 pending file by {bucket=%s, s3Key=%s}, but got %s",
                                        bucket, s3Key, rows.size())));
                    }

                    Row row = rows.iterator().next();
                    UUID fileId = row.getUUID("id");
                    UUID ownerId = row.getUUID("owner_id");
                    return confirmFileUpload(fileId, ownerId);
                });
    }

    /**
     * Marks a pending file upload as failed when object confirmation did not succeed.
     *
     * @param fileId  file identifier
     * @param ownerId file owner identifier
     * @return Future containing updated file metadata
     */
    public Future<JsonObject> markPendingFileUploadAsFailed(UUID fileId, UUID ownerId) {
        log.info("Marking pending file metadata as failed by {fileId={}, ownerId={}}", fileId, ownerId);

        String sql = "UPDATE files SET status = $3 " +
                "WHERE id = $1 AND owner_id = $2 AND status = $4 " +
                "RETURNING id, name, size, content_type, owner_id, bucket, s3_key, status, created_at, updated_at";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId, FileStatus.FAILED.name(), FileStatus.PENDING.name()))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(new FileMetadataNotFoundException(
                                String.format("No pending file metadata was found by {fileId=%s, ownerId=%s}", fileId, ownerId)));
                    }
                    return mapRowToJsonAsync(rows.iterator().next());
                });
    }

    private Future<JsonObject> mapRowToJsonAsync(Row row) {
        return vertx.executeBlocking(() -> mapRowToJson(row), false);
    }

    private JsonObject mapRowToJson(Row row) {
        var json = new JsonObject()
                .put("fileId", row.getUUID("id"))
                .put("name", row.getString("name"))
                .put("size", row.getLong("size"))
                .put("contentType", row.getString("content_type"))
                .put("ownerId", row.getUUID("owner_id"))
                .put("status", row.getString("status"))
                .put("bucket", row.getString("bucket"))
                .put("s3Key", row.getString("s3_key"))
                .put("uploadedAt", row.getLocalDateTime("created_at").toString());

        Optional.ofNullable(row.getLocalDateTime("updated_at"))
                .ifPresent(ldt -> json.put("lastModifiedAt", ldt.toString()));

        return json;
    }
}
