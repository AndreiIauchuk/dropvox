package org.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;

/**
 * Data access object for file metadata.
 */
public class MetadataDAO {

    private final PgPool pgPool;

    public MetadataDAO(PgPool pgPool) {
        this.pgPool = pgPool;
    }

    /**
     * Retrieves file metadata for a given file ID and owner ID.
     *
     * @param fileId the file identifier
     * @param userId the owner identifier
     * @return Future containing file metadata as JsonObject
     */
    public Future<JsonObject> findFileByIdAndUser(String fileId, String userId) {
        String sql = "SELECT id, name, size, content_type, s3_key, created_at " +
                "FROM files WHERE id = $1 AND owner_id = $2";

        return pgPool.preparedQuery(sql)
                .execute(Tuple.of(fileId, userId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture("File not found or access denied");
                    }
                    Row row = rows.iterator().next();
                    JsonObject result = new JsonObject()
                            .put("id", row.getString("id"))
                            .put("name", row.getString("name"))
                            .put("size", row.getLong("size"))
                            .put("contentType", row.getString("content_type"))
                            .put("s3Key", row.getString("s3_key"))
                            .put("createdAt", row.getLocalDateTime("created_at").toString());
                    return Future.succeededFuture(result);
                });
    }
}
