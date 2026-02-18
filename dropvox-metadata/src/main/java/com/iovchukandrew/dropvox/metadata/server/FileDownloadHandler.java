package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import software.amazon.awssdk.http.HttpStatusCode;

/**
 * Handles GET /files/:id requests.
 */
public class FileDownloadHandler {

    private final FilesDAO filesDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;

    public FileDownloadHandler(
            FilesDAO filesDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator
    ) {
        this.filesDAO = filesDAO;
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

        filesDAO.findFileByIdAndOwner(fileId, userId)
                .compose(metadata -> {
                    String presignedUrl = s3PresignedUrlGenerator.generateGetUrl(
                            metadata.getString("s3.bucket"),
                            metadata.getString("s3.accessKey")
                    );
                    metadata.put("downloadUrl", presignedUrl);
                    return Future.succeededFuture(metadata);
                })
                .onSuccess(metadata -> sendResponse(ctx, metadata))
                .onFailure(e -> handleError(ctx, e));
    }

    private void sendResponse(RoutingContext ctx, JsonObject metadata) {
        ctx.response()
                .setStatusCode(HttpStatusCode.OK)
                .putHeader("Content-Type", "application/json")
                .end(metadata.toBuffer());
    }

    private void handleError(RoutingContext ctx, Throwable e) {
        ctx.response()
                .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                .end(e.getMessage());
    }
}