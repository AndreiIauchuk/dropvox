package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

//TODO Refactor?
public class FileUploadCompleteHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCompleteHandler.class);

    private final FilesDAO filesDAO;

    public FileUploadCompleteHandler(FilesDAO filesDAO) {
        this.filesDAO = filesDAO;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String ownerIdHeader = ctx.request().getHeader("X-User-Id");
        if (ownerIdHeader == null) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Missing X-User-Id header");
            return;
        }

        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerIdHeader);
        } catch (IllegalArgumentException e) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Invalid user ID format");
            return;
        }

        String fileIdParam = ctx.pathParam("fileId");
        if (fileIdParam == null) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Missing fileId in path");
            return;
        }

        UUID fileId;
        try {
            fileId = UUID.fromString(fileIdParam);
        } catch (IllegalArgumentException e) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Invalid file ID format");
            return;
        }

        filesDAO.confirmFileUpload(fileId, ownerId)
                .compose(Future::succeededFuture)
                .onSuccess(metadata -> {
                    ctx.response()
                            .setStatusCode(HttpStatusCode.OK)
                            .putHeader("Content-Type", "application/json")
                            .end(metadata.toBuffer());
                })
                .onFailure(err -> {
                    log.error("Failed to complete upload", err);
                    ctx.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR).end(err.getMessage());
                });
    }
}
