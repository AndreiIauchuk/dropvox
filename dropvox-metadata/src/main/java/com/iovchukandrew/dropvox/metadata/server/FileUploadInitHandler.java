package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

/**
 * Handles POST /files/init requests.
 */
public class FileUploadInitHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadInitHandler.class);

    private final FilesDAO filesDAO;
    private final S3PresignedUrlGenerator urlGenerator;
    private final String bucketName;

    public FileUploadInitHandler(FilesDAO filesDAO, S3PresignedUrlGenerator urlGenerator, String bucketName) {
        this.filesDAO = filesDAO;
        this.urlGenerator = urlGenerator;
        this.bucketName = bucketName;
    }

    @Override
    public void handle(RoutingContext ctx) {
        UUID userUuid = UuidParser.parseHeader(ctx, HttpHeader.USER_ID);
        if (userUuid == null) return;

        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Request body is missing or not JSON");
            return;
        }

        String filename = body.getString("filename");
        String contentType = body.getString("contentType");
        Long size = body.getLong("size");

        if (filename == null || size == null || contentType == null) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Missing required fields: filename, size, contentType");
            return;
        }

        UUID fileUuid = UUID.randomUUID();
        String s3Key = bucketName + "/users/" + userUuid + "/file/" + fileUuid + "/" + filename + "." + contentType;

        filesDAO.createPendingFile(filename, size, contentType, userUuid, bucketName, s3Key)
                .compose(metadata -> {
                    String uploadUrl = urlGenerator.generatePutUrl(bucketName, s3Key);
                    metadata.put("uploadUrl", uploadUrl);
                    return Future.succeededFuture(metadata);
                })
                .onSuccess(metadata -> {
                    ctx.response()
                            .setStatusCode(HttpStatusCode.OK)
                            .putHeader("Content-Type", "application/json")
                            .end(metadata.toBuffer());
                })
                .onFailure(err -> {
                    log.error("Failed to initialize upload", err);
                    ctx.response().setStatusCode(500).end(err.getMessage());
                });
    }
}
