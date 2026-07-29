package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceException;
import com.iovchukandrew.dropvox.gateway.kafka.UploadCompletionRequestedPublisher;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

/**
 * Handles POST /files/complete/:fileId requests.
 */
public class FileUploadCompleteHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCompleteHandler.class);

    private final AuthServiceClient authServiceClient;
    private final MetadataServiceClient metadataServiceClient;
    private final UploadCompletionRequestedPublisher uploadCompletionRequestedPublisher;

    public FileUploadCompleteHandler(
            AuthServiceClient authServiceClient,
            MetadataServiceClient metadataServiceClient,
            UploadCompletionRequestedPublisher uploadCompletionRequestedPublisher
    ) {
        this.authServiceClient = authServiceClient;
        this.metadataServiceClient = metadataServiceClient;
        this.uploadCompletionRequestedPublisher = uploadCompletionRequestedPublisher;
    }

    @Override
    public void handle(RoutingContext ctx) {
        //Auth here

        String fileId = ctx.pathParam("fileId");
        String traceId = ctx.get("traceId");

        authServiceClient.validateToken("token")
                .compose(userId -> {
                    log.info("Completing upload for fileId={}, userId={}", fileId, userId);
                    if (uploadCompletionRequestedPublisher != null) {
                        return uploadCompletionRequestedPublisher.publish(fileId, userId, traceId)
                                .map(new JsonObject()
                                        .put("fileId", fileId)
                                        .put("status", "PROCESSING"));
                    }

                    return metadataServiceClient.completeFileUpload(fileId, userId);
                })
                .onSuccess(acceptedPayload -> {
                    log.info("Upload completion accepted for fileId={}", fileId);
                    JsonObject response = acceptedPayload.copy().put("statusUrl", "/files/" + fileId + "/status");
                    ctx.response()
                            .setStatusCode(HttpStatusCode.ACCEPTED)
                            .putHeader("Content-Type", "application/json")
                            .end(response.toBuffer());
                })
                .onFailure(err -> {
                    log.error("Failed to complete upload", err);
                    int statusCode = HttpStatusCode.INTERNAL_SERVER_ERROR;
                    if (err instanceof MetadataServiceException metadataServiceException) {
                        statusCode = metadataServiceException.getStatusCode();
                    }
                    ctx.response().setStatusCode(statusCode).end(err.getMessage());
                });
    }
}
