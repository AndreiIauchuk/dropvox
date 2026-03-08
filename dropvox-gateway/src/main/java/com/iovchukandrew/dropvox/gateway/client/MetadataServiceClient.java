package com.iovchukandrew.dropvox.gateway.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Metadata Service client.
 */
public class MetadataServiceClient {
    private final WebClient webClient;
    private final String host;
    private final int port;

    public MetadataServiceClient(WebClient webClient, String host, int port) {
        this.webClient = webClient;
        this.host = host;
        this.port = port;
    }

    /**
     * GET file metadata (including presigned URL) from Metadata Service.
     *
     * @param fileId the file identifier
     * @param userId the user identifier
     * @return Future containing the metadata JSON
     */
    public Future<JsonObject> getFileMetadata(String fileId, String userId) {
        return webClient.get(port, host, "/files/" + fileId)
                .putHeader(HttpHeader.USER_ID, userId)
                .send()
                .transform(ar -> handleJsonResponse(ar, "GET /files/" + fileId));
    }

    /**
     * POST init file upload in Metadata Service.
     *
     * @param request upload init payload
     * @param userId  the user identifier
     * @return Future containing metadata JSON with fileId and uploadUrl
     */
    public Future<JsonObject> initFileUpload(JsonObject request, String userId) {
        return webClient.post(port, host, "/files/init")
                .putHeader(HttpHeader.USER_ID, userId)
                .sendJsonObject(request)
                .transform(ar -> handleJsonResponse(ar, "POST /files/init"));
    }

    /**
     * POST completes file upload in Metadata Service.
     *
     * @param fileId the file identifier
     * @param userId the user identifier
     * @return Future containing updated metadata JSON
     */
    public Future<JsonObject> completeFileUpload(String fileId, String userId) {
        return webClient.post(port, host, "/files/complete/" + fileId)
                .putHeader(HttpHeader.USER_ID, userId)
                .send()
                .transform(ar -> handleJsonResponse(ar, "POST /files/complete/" + fileId));
    }

    private Future<JsonObject> handleJsonResponse(
            AsyncResult<HttpResponse<Buffer>> ar,
            String operation
    ) {
        if (ar.failed()) {
            return Future.failedFuture(ar.cause());
        }

        HttpResponse<Buffer> resp = ar.result();
        int status = resp.statusCode();

        if (status < 200 || status >= 300) {
            String msg = "Metadata service returned " + status + " for " + operation;
            return Future.failedFuture(msg);
        }

        JsonObject body = resp.bodyAsJsonObject();
        if (body == null) {
            return Future.failedFuture("Metadata service returned empty/invalid JSON for " + operation);
        }

        return Future.succeededFuture(body);
    }
}
