package com.iovchukandrew.dropvox.gateway.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.MDC;

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
        return withTrace(webClient.get(port, host, "/files/" + fileId))
                .putHeader(HttpHeader.USER_ID, userId)
                .send()
                .transform(ar -> handleJsonResponse(ar, "GET /files/" + fileId));
    }

    /**
     * GET current file upload status from Metadata Service.
     *
     * @param fileId the file identifier
     * @param userId the user identifier
     * @return Future containing metadata JSON with current status
     */
    public Future<JsonObject> getFileUploadStatus(String fileId, String userId) {
        return withTrace(webClient.get(port, host, "/files/" + fileId + "/status"))
                .putHeader(HttpHeader.USER_ID, userId)
                .send()
                .transform(ar -> handleJsonResponse(ar, "GET /files/" + fileId + "/status"));
    }

    /**
     * POST init file upload in Metadata Service.
     *
     * @param request upload init payload
     * @param userId  the user identifier
     * @return Future containing metadata JSON with fileId and uploadUrl
     */
    public Future<JsonObject> initFileUpload(JsonObject request, String userId) {
        return withTrace(webClient.post(port, host, "/files/init"))
                .putHeader(HttpHeader.USER_ID, userId)
                .sendJsonObject(request)
                .transform(ar -> handleJsonResponse(ar, "POST /files/init"));
    }

    /**
     * POST requests upload completion in Metadata Service.
     *
     * @param fileId the file identifier
     * @param userId the user identifier
     * @return Future containing accepted processing payload
     */
    public Future<JsonObject> requestFileUploadCompletion(String fileId, String userId) {
        return withTrace(webClient.post(port, host, "/files/" + fileId + "/completion-request"))
                .putHeader(HttpHeader.USER_ID, userId)
                .send()
                .transform(ar -> handleJsonResponse(ar, "POST /files/" + fileId + "/completion-request"));
    }

    private HttpRequest<Buffer> withTrace(HttpRequest<Buffer> request) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            request.putHeader(HttpHeader.TRACE_ID, traceId);
        }
        return request;
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
            return Future.failedFuture(new MetadataServiceException(status, extractErrorMessage(resp, operation)));
        }

        JsonObject body = resp.bodyAsJsonObject();
        if (body == null) {
            return Future.failedFuture("Service returned empty/invalid JSON for " + operation);
        }

        return Future.succeededFuture(body);
    }

    private String extractErrorMessage(HttpResponse<Buffer> resp, String operation) {
        String body = resp.bodyAsString();
        if (body != null) {
            String trimmedBody = body.trim();
            if (!trimmedBody.isEmpty()) {
                return trimmedBody;
            }
        }

        return "Service returned " + resp.statusCode() + " for " + operation;
    }
}
