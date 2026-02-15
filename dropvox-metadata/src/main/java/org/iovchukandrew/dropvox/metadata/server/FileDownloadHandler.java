package org.iovchukandrew.dropvox.metadata.server;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;

/**
 * Handles GET /files/:id requests.
 */
public class FileDownloadHandler {

    private final MetadataDAO metadataDAO;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public FileDownloadHandler(MetadataDAO metadataDAO, S3Presigner s3Presigner, String bucketName) {
        this.metadataDAO = metadataDAO;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
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
                    // Generate presigned URL locally
                    String presignedUrl = generatePresignedUrl(metadata.getString("s3Key"));
                    metadata.put("downloadUrl", presignedUrl);
                    return Future.succeededFuture(metadata);
                })
                .onSuccess(metadata -> sendResponse(ctx, metadata))
                .onFailure(err -> handleError(ctx, err));
    }

    private String generatePresignedUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
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