package com.iovchukandrew.dropvox.metadata.server;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import com.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;

/**
 * Handles GET /files/:id requests.
 */
public class FileDownloadHandler {

    private final MetadataDAO metadataDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;

    public FileDownloadHandler(
            MetadataDAO metadataDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator
    ) {
        this.metadataDAO = metadataDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
    }

    /**
     * Entry point from router.
     */
    public void handle(RoutingContext ctx) {
        String fileId = ctx.pathParam("id");
        String userId = ctx.request().getHeader("X-User-Id");

        if (userId == null || userId.isEmpty()) {
            ctx.response().setStatusCode(400).end("Missing X-User-Id header");
            return;
        }

        metadataDAO.findFileByIdAndUser(fileId, userId)
                .compose(metadata -> {
                    String presignedUrl = s3PresignedUrlGenerator.generateGetUrl(metadata.getString("s3Key"));
                    metadata.put("downloadUrl", presignedUrl);
                    return Future.succeededFuture(metadata);
                })
                .onSuccess(metadata -> sendResponse(ctx, metadata))
                .onFailure(err -> handleError(ctx, err));
    }

    private void sendResponse(RoutingContext ctx, JsonObject metadata) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(metadata.toBuffer());
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        //Log the error appropriately
        ctx.response().setStatusCode(500).end(err.getMessage());
    }
}