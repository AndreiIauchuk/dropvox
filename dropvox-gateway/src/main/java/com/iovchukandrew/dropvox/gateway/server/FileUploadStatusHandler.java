package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

/**
 * Handles GET /files/:fileId/status requests.
 */
public class FileUploadStatusHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadStatusHandler.class);

    private final AuthServiceClient authServiceClient;
    private final MetadataServiceClient metadataServiceClient;

    public FileUploadStatusHandler(
            AuthServiceClient authServiceClient,
            MetadataServiceClient metadataServiceClient
    ) {
        this.authServiceClient = authServiceClient;
        this.metadataServiceClient = metadataServiceClient;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String fileId = ctx.pathParam("fileId");

        authServiceClient.validateToken("token")
                .compose(userId -> {
                    log.info("Fetching upload status for fileId={}, userId={}", fileId, userId);
                    return metadataServiceClient.getFileUploadStatus(fileId, userId);
                })
                .onSuccess(status -> ctx.response()
                        .setStatusCode(HttpStatusCode.OK)
                        .putHeader("Content-Type", "application/json")
                        .end(status.toBuffer()))
                .onFailure(err -> {
                    log.error("Failed to retrieve upload status", err);
                    int statusCode = HttpStatusCode.INTERNAL_SERVER_ERROR;
                    if (err instanceof MetadataServiceException metadataServiceException) {
                        statusCode = metadataServiceException.getStatusCode();
                    }
                    ctx.response().setStatusCode(statusCode).end(err.getMessage());
                });
    }
}

