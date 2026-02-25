package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Future;
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

    private final Pool pool;

    public FilesDAO(Pool pool) {
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
        log.info("Retrieving file metadata by {fileId={}, ownerId={}}", fileId, ownerId);

        String sql = "SELECT id, name, size, content_type, owner_id, bucket, s3_key, created_at, updated_at " +
                "FROM files WHERE id = $1 AND owner_id = $2 AND status = 'UPLOADED'";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(
                                String.format("File not found by {fileId=%s, ownerId=%s}", fileId, ownerId));
                    }
                    if (rows.size() > 1) {
                        return Future.failedFuture(
                                String.format("Expected exactly 1 file by {fileId=%s, ownerId=%s}, but got %s",
                                        fileId, ownerId, rows.size()));
                    }
                    Row row = rows.iterator().next();
                    return Future.succeededFuture(mapRowToJson(row));
                })
                .onFailure(e -> log.error("Unable to find a file by {fileId={}, ownerId={}}", fileId, ownerId, e));
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
                "VALUES ($1, $2, $3, $4, $5, 'PENDING', $6, $7) " +
                "RETURNING id, name, size, content_type, owner_id, bucket, s3_key, created_at, updated_at";

        UUID fileId = UUID.randomUUID();
        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, filename, size, contentType, ownerId, bucket, s3Key))
                .compose(rows -> {
                    if (rows.size() != 1) {
                        return Future.failedFuture("Expected to insert single pending file metadata, but got " + rows.size());
                    }
                    return Future.succeededFuture(mapRowToJson(rows.iterator().next()));
                })
                .onFailure(e -> log.error("Unable to create a pending file metadata", e));
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

        String sql = "UPDATE files SET status = 'UPLOADED' " +
                "WHERE id = $1 AND owner_id = $2 AND status = 'PENDING' " +
                "RETURNING id, name, size, content_type, owner_id, bucket, s3_key, status, created_at, updated_at";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(
                                String.format("No pending file metadata was found by {fileId=%s, ownerId=%s}", fileId, ownerId));
                    }
                    return Future.succeededFuture(mapRowToJson(rows.iterator().next()));
                })
                .onFailure(e -> log.error("Unable to update a file metadata of uploaded file", e));
    }

    private JsonObject mapRowToJson(Row row) {
        var json = new JsonObject()
                .put("fileId", row.getUUID("id"))
                .put("name", row.getString("name"))
                .put("size", row.getLong("size"))
                .put("contentType", row.getString("content_type"))
                .put("ownerId", row.getUUID("owner_id"))
                .put("bucket", row.getString("bucket"))
                .put("s3Key", row.getString("s3_key"))
                .put("uploadedAt", row.getLocalDateTime("created_at").toString());

        Optional.ofNullable(row.getLocalDateTime("updated_at"))
                .ifPresent(ldt -> json.put("lastModifiedAt", ldt.toString()));

        return json;
    }
}
