package org.iovchukandrew.dropvox.gateway.server.handler;

import org.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import org.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles GET /files/{id} requests in the API Gateway.
 * Coordinates calls to Auth Service and Metadata Service.
 */
public class FileDownloadHandler {

    private final AuthServiceClient authServiceClient;
    private final MetadataServiceClient metadataServiceClient;

    /**
     * Constructs the handler with injected dependencies.
     *
     * @param vertx    the Vert.x instance
     * @param authHost host of the Auth Service
     * @param authPort port of the Auth Service
     * @param metaHost host of the Metadata Service
     * @param metaPort port of the Metadata Service
     */
    public FileDownloadHandler(
            Vertx vertx,
            AuthServiceClient authServiceClient,
            MetadataServiceClient metadataServiceClient) {
        this.authServiceClient = authServiceClient;
        this.metadataServiceClient = metadataServiceClient;
    }

    /**
     * Entry point from the router.
     *
     * @param ctx the routing context
     */
    public void handle(RoutingContext ctx) {
        String fileId = ctx.pathParam("id");

        //String authHeader = ctx.request().getHeader("Authorization");
        // if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        // ctx.response().setStatusCode(401).end("Missing or invalid token");
        // return;
        // }
        // String token = authHeader.substring(7);

        authServiceClient.validateToken("token")
                .compose(userId -> metadataServiceClient.fetchFileMetadata(fileId, userId))
                .onSuccess(metadata -> sendSuccessResponse(ctx, metadata))
                .onFailure(error -> handleError(ctx, error));
    }

    /**
     * Sends a successful response with file metadata to the client.
     *
     * @param ctx      the routing context
     * @param metadata the metadata JSON
     */
    private void sendSuccessResponse(RoutingContext ctx, JsonObject metadata) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(metadata.toBuffer());
    }

    /**
     * Handles errors occurred during the request processing.
     *
     * @param ctx   the routing context
     * @param error the error cause
     */
    private void handleError(RoutingContext ctx, Throwable error) {
        ctx.response().setStatusCode(500).end(error.getMessage());
    }
}