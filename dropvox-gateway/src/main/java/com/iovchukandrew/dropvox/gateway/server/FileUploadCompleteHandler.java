package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import io.vertx.core.Handler;
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

    public FileUploadCompleteHandler(
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
                .compose(userId -> metadataServiceClient.completeFileUpload(fileId, userId))
                .onSuccess(metadata -> ctx.response()
                        .setStatusCode(HttpStatusCode.OK)
                        .putHeader("Content-Type", "application/json")
                        .end(metadata.toBuffer()))
                .onFailure(err -> {
                    log.error("Failed to complete upload", err);
                    ctx.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR).end(err.getMessage());
                });
    }
}
