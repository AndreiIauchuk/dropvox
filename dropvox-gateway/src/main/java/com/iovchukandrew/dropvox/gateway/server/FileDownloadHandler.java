package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

/**
 * Handles GET /files/:id requests.
 */
public class FileDownloadHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileDownloadHandler.class);

    private final AuthServiceClient authServiceClient;
    private final MetadataServiceClient metadataServiceClient;

    public FileDownloadHandler(
            AuthServiceClient authServiceClient,
            MetadataServiceClient metadataServiceClient) {
        this.authServiceClient = authServiceClient;
        this.metadataServiceClient = metadataServiceClient;
    }

    @Override
    public void handle(RoutingContext ctx) {
        //String authHeader = ctx.request().getHeader("Authorization");
        // if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        // ctx.response().setStatusCode(401).end("Missing or invalid token");
        // return;
        // }
        // String token = authHeader.substring(7);

        String fileId = ctx.pathParam("fileId");

        authServiceClient.validateToken("token")
                .compose(userId -> metadataServiceClient.getFileMetadata(fileId, userId))
                .onSuccess(metadata -> ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(metadata.toBuffer()))
                .onFailure(err -> {
                    log.error("Failed to complete upload", err);
                    ctx.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR).end(err.getMessage());
                });
    }
}
