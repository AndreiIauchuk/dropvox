package com.iovchukandrew.dropvox.gateway.client;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Calls Metadata Service for retrieving file metadata.
 */
public class MetadataServiceClient {

    // private final String metadataHost;
    // private final int metaPort;

    /**
     * Retrieves file metadata (including presigned URL) from Metadata Service.
     *
     * @param fileId the file identifier
     * @param userId the user identifier (for access control)
     * @return Future containing the metadata JSON
     */
    public Future<JsonObject> fetchFileMetadata(String fileId, String userId) {
        JsonObject mockMetadata = new JsonObject()
                .put("fileId", fileId)
                .put("userId", userId)
                .put("fileName", "example.txt")
                .put("fileSize", 1024)
                .put("presignedUrl", "https://s3.minio.com/bucket/example.txt?signature=abc123");
        return Future.succeededFuture(mockMetadata);

        // return webClient.get(metaPort, metadataHost, "/files/" + fileId)
        // .putHeader("X-User-Id", userId)
        // .send()
        // .map(HttpResponse::bodyAsJsonObject); // TODO Use transform() here to handle error or add logs
    }
}
