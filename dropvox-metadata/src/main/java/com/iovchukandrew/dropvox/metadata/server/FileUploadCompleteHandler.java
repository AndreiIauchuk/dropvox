package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

/**
 * Handles POST /files/complete/:fileId requests.
 */
public class FileUploadCompleteHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCompleteHandler.class);

    private final FilesDAO filesDAO;

    public FileUploadCompleteHandler(FilesDAO filesDAO) {
        this.filesDAO = filesDAO;
    }

    @Override
    public void handle(RoutingContext ctx) {
        UUID userUuid = UuidParser.parseHeader(ctx, HttpHeader.USER_ID);
        if (userUuid == null) return;

        UUID fileUuid = UuidParser.parsePathParam(ctx, "fileId");
        if (fileUuid == null) return;

        filesDAO.confirmFileUpload(fileUuid, userUuid)
                .compose(Future::succeededFuture)
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
