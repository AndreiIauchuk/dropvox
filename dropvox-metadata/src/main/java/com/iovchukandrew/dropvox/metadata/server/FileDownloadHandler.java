package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

/**
 * Handles GET /files/:id requests.
 */
public class FileDownloadHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadHandler.class);
    private final FilesDAO filesDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;

    public FileDownloadHandler(
            FilesDAO filesDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator
    ) {
        this.filesDAO = filesDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
    }

    @Override
    public void handle(RoutingContext ctx) {
        UUID userUuid = UuidParser.parseHeader(ctx, HttpHeader.USER_ID);
        if (userUuid == null) return;

        UUID fileUuid = UuidParser.parsePathParam(ctx, "fileId");
        if (fileUuid == null) return;

        filesDAO.findFileByIdAndOwner(fileUuid, userUuid)
                .map(metadata -> {
                    String presignedUrl = s3PresignedUrlGenerator.generateGetUrl(
                            metadata.getString("bucket"),
                            metadata.getString("s3Key")
                    );
                    metadata.put("downloadUrl", presignedUrl);
                    return metadata;
                })
                .onSuccess(metadata -> sendResponse(ctx, metadata))
                .onFailure(e -> handleError(ctx, e));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("Missing X-User-Id header");
        }
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
