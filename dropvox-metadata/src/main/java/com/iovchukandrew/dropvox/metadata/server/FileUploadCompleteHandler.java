package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.processing.UploadCompletionProcessor;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
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

    private final UploadCompletionProcessor uploadCompletionProcessor;

    public FileUploadCompleteHandler(UploadCompletionProcessor uploadCompletionProcessor) {
        this.uploadCompletionProcessor = uploadCompletionProcessor;
    }

    @Override
    public void handle(RoutingContext ctx) {
        UUID userUuid = UuidParser.parseHeader(ctx, HttpHeader.USER_ID);
        if (userUuid == null) return;

        UUID fileUuid = UuidParser.parsePathParam(ctx, "fileId");
        if (fileUuid == null) return;

        JsonObject acceptedPayload = new JsonObject()
                .put("fileId", fileUuid)
                .put("status", "PROCESSING");

        ctx.response()
                .setStatusCode(HttpStatusCode.ACCEPTED)
                .putHeader("Content-Type", "application/json")
                .end(acceptedPayload.toBuffer());

        uploadCompletionProcessor.processRequestedCompletion(fileUuid, userUuid)
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
}
