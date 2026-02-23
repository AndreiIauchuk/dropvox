package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.MDC;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

abstract class FileHandler implements Handler<RoutingContext> {

    protected final FilesDAO filesDAO;
    protected final S3PresignedUrlGenerator s3PresignedUrlGenerator;

    protected FileHandler(FilesDAO filesDAO, S3PresignedUrlGenerator s3PresignedUrlGenerator) {
        this.filesDAO = filesDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String trackId = UUID.randomUUID().toString(); //TODO From HTTP header?
        MDC.put("trackId", trackId);
        try {
            handle_(ctx);
        } finally {
            MDC.clear();
        }
    }

    private void handle_(RoutingContext ctx) {
        String fileId = ctx.pathParam("id");
        String userId = ctx.request().getHeader("X-User-Id");

        UUID userUuid;
        try {
            validateUserId(userId);
            userUuid = UUID.fromString(userId);
        } catch (Exception e) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Invalid userId. Cause: " + e.getMessage());
            return;
        }

        UUID fileUuid;
        try {
            fileUuid = UUID.fromString(fileId);
        } catch (IllegalArgumentException e) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Invalid fileId. Cause: " + e.getMessage());
            return;
        }

        handleRequest(fileUuid, userUuid)
                .onSuccess(metadata -> sendResponse(ctx, metadata))
                .onFailure(e -> handleError(ctx, e));
    }

    protected abstract Future<JsonObject> handleRequest(UUID fileUuid, UUID userUuid);

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
