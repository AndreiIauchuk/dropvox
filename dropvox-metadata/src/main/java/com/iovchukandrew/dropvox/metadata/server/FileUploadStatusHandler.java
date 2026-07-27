package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FileMetadataInvariantViolationException;
import com.iovchukandrew.dropvox.metadata.db.FileMetadataNotFoundException;
import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * Handles GET /files/:fileId/status requests.
 */
public class FileUploadStatusHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadStatusHandler.class);

    private final FilesDAO filesDAO;

    public FileUploadStatusHandler(FilesDAO filesDAO) {
        this.filesDAO = filesDAO;
    }

    @Override
    public void handle(RoutingContext ctx) {
        UUID userUuid = UuidParser.parseHeader(ctx, HttpHeader.USER_ID);
        if (userUuid == null) return;

        UUID fileUuid = UuidParser.parsePathParam(ctx, "fileId");
        if (fileUuid == null) return;

        filesDAO.findFileByIdAndOwnerAnyStatus(fileUuid, userUuid)
                .onSuccess(metadata -> ctx.response()
                        .setStatusCode(HttpStatusCode.OK)
                        .putHeader("Content-Type", "application/json")
                        .end(metadata.toBuffer()))
                .onFailure(err -> {
                    log.error("Failed to retrieve upload status", err);
                    int statusCode = HttpStatusCode.INTERNAL_SERVER_ERROR;
                    if (err instanceof FileMetadataInvariantViolationException) {
                        statusCode = HTTP_CONFLICT;
                    } else if (err instanceof FileMetadataNotFoundException) {
                        statusCode = HTTP_NOT_FOUND;
                    }
                    ctx.response().setStatusCode(statusCode).end(err.getMessage());
                });
    }
}

