package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.HttpStatusCode;

/**
 * Handles POST /files/init requests.
 */
public class FileUploadInitHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(FileUploadInitHandler.class);

    private final AuthServiceClient authServiceClient;
    private final MetadataServiceClient metadataServiceClient;

    public FileUploadInitHandler(AuthServiceClient authServiceClient, MetadataServiceClient metadataServiceClient) {
        this.authServiceClient = authServiceClient;
        this.metadataServiceClient = metadataServiceClient;
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Request body is missing or not JSON");
            return;
        }

        authServiceClient.validateToken("token")
                .compose(userId -> metadataServiceClient.initFileUpload(body, userId))
                .onSuccess(metadata -> ctx.response()
                        .setStatusCode(HttpStatusCode.OK)
                        .putHeader("Content-Type", "application/json")
                        .end(metadata.toBuffer()))
                .onFailure(err -> {
                    log.error("Failed to initialize upload", err);
                    ctx.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR).end(err.getMessage());
                });
    }
}
