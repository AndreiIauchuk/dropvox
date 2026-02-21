package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

//TODO Rename to FilesDAO? looks like it will only quirying FIlES table

/**
 * Data access object for file metadata.
 */
public class FilesDAO {
    private static final Logger log = LoggerFactory.getLogger(FilesDAO.class);

    private final Pool pool;

    public FilesDAO(Vertx vertx, Pool pool) {
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

        String sql = "SELECT name, size, content_type, bucket, s3_key, created_at, updated_at " +
                "FROM files WHERE id = $1 AND owner_id = $2";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(fileId, ownerId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture(
                                String.format("File not found by {fileId=%s, ownerId=%s}", fileId, ownerId));
                    }
                    Row row = rows.iterator().next();
                    JsonObject result = new JsonObject()
                            .put("id", fileId)
                            .put("name", row.getString("name"))
                            .put("size", row.getLong("size"))
                            .put("contentType", row.getString("content_type"))
                            .put("ownerId", ownerId)
                            .put("bucket", row.getString("bucket"))
                            .put("s3Key", row.getString("s3_key"))
                            .put("uploadedAt", row.getLocalDateTime("created_at").toString())
                            .put("lastModifiedAt", row.getLocalDateTime("updated_at").toString());
                    return Future.succeededFuture(result);
                })
                .onFailure(e -> log.error("Unable to lookup a file by {fileId={}, ownerId={}}", fileId, ownerId, e));
    }
}
