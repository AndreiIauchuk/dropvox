package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FileMetadataInvariantViolationException;
import com.iovchukandrew.dropvox.metadata.db.FileMetadataNotFoundException;
import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3ObjectExistenceChecker;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
                .compose(metadata -> ctx.vertx().executeBlocking(() ->
                        s3ObjectExistenceChecker.objectExists(
                                metadata.getString("bucket"),
                                metadata.getString("s3Key")
                        )))
                .compose(objectExists -> {
                    if (!objectExists) {
                        return Future.failedFuture(new FileNotYetUploadedException());
                    }
                    return filesDAO.confirmFileUpload(fileUuid, userUuid);
                })
                .onSuccess(metadata -> ctx.response()
                        .setStatusCode(HttpStatusCode.OK)
                        .putHeader("Content-Type", "application/json")
                        .end(metadata.toBuffer()))
                .onFailure(err -> {
                    log.error("Failed to complete upload", err);
                    int statusCode = HttpStatusCode.INTERNAL_SERVER_ERROR;
                    if (err instanceof FileNotYetUploadedException) {
                        statusCode = HTTP_CONFLICT;
                    } else if (err instanceof FileMetadataInvariantViolationException) {
                        statusCode = HTTP_CONFLICT;
                    } else if (err instanceof FileMetadataNotFoundException) {
                        statusCode = HTTP_NOT_FOUND;
                    }
                    ctx.response().setStatusCode(statusCode).end(err.getMessage());
                });
    }
}
