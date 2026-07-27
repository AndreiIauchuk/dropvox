package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FileMetadataInvariantViolationException;
import com.iovchukandrew.dropvox.metadata.db.FileMetadataNotFoundException;
import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3ObjectExistenceChecker;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * Handles POST /files/complete/:fileId requests.
 */
public class FileUploadCompleteHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCompleteHandler.class);
    private static final int MAX_S3_EXISTENCE_CHECK_ATTEMPTS = 5;
    private static final long S3_EXISTENCE_RETRY_DELAY_MS = 200L;

    private final FilesDAO filesDAO;
    private final S3ObjectExistenceChecker s3ObjectExistenceChecker;

    public FileUploadCompleteHandler(FilesDAO filesDAO, S3ObjectExistenceChecker s3ObjectExistenceChecker) {
        this.filesDAO = filesDAO;
        this.s3ObjectExistenceChecker = s3ObjectExistenceChecker;
    }

    @Override
    public void handle(RoutingContext ctx) {
        UUID userUuid = UuidParser.parseHeader(ctx, HttpHeader.USER_ID);
        if (userUuid == null) return;

        UUID fileUuid = UuidParser.parsePathParam(ctx, "fileId");
        if (fileUuid == null) return;

        filesDAO.findPendingFileByIdAndOwner(fileUuid, userUuid)
                .onSuccess(metadata -> {
                    JsonObject acceptedPayload = new JsonObject()
                            .put("fileId", fileUuid)
                            .put("status", "PROCESSING");

                    ctx.response()
                            .setStatusCode(HttpStatusCode.ACCEPTED)
                            .putHeader("Content-Type", "application/json")
                            .end(acceptedPayload.toBuffer());

                    processUploadCompletionAsync(ctx.vertx(), fileUuid, userUuid, metadata);
                })
                .onFailure(err -> {
                    log.error("Failed to accept upload completion", err);
                    int statusCode = HttpStatusCode.INTERNAL_SERVER_ERROR;
                    if (err instanceof FileMetadataInvariantViolationException) {
                        statusCode = HTTP_CONFLICT;
                    } else if (err instanceof FileMetadataNotFoundException) {
                        statusCode = HTTP_NOT_FOUND;
                    }
                    ctx.response().setStatusCode(statusCode).end(err.getMessage());
                });
    }

    private void processUploadCompletionAsync(Vertx vertx, UUID fileUuid, UUID userUuid, JsonObject metadata) {
        checkObjectExistsWithRetry(vertx, metadata, 1)
                .compose(objectExists -> {
                    if (!objectExists) {
                        return Future.failedFuture(new FileNotYetUploadedException());
                    }
                    return filesDAO.confirmFileUpload(fileUuid, userUuid).mapEmpty();
                })
                .onSuccess(ignored -> log.info("Upload completion finished for fileId={}, userId={}", fileUuid, userUuid))
                .onFailure(err -> {
                    if (err instanceof FileNotYetUploadedException) {
                        log.warn("Upload completion failed because object is still missing for fileId={}, userId={}",
                                fileUuid, userUuid);
                        return;
                    }
                    log.error("Unexpected failure during async upload completion for fileId={}, userId={}",
                            fileUuid, userUuid, err);
                });
    }

    private Future<Boolean> checkObjectExistsWithRetry(Vertx vertx, JsonObject metadata, int attempt) {
        return vertx.executeBlocking(() ->
                s3ObjectExistenceChecker.objectExists(
                        metadata.getString("bucket"),
                        metadata.getString("s3Key")
                )
        ).compose(objectExists -> {
            if (objectExists || attempt >= MAX_S3_EXISTENCE_CHECK_ATTEMPTS) {
                return Future.succeededFuture(objectExists);
            }

            return Future.<Void>future(promise ->
                    vertx.setTimer(S3_EXISTENCE_RETRY_DELAY_MS, ignored -> promise.complete())
            ).compose(ignored -> checkObjectExistsWithRetry(vertx, metadata, attempt + 1));
        });
    }
}
