package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends VerticleBase {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final WebClient webClient;
    private final JsonObject config;

    public Server(WebClient webClient, JsonObject config) {
        this.webClient = webClient;
        this.config = config;
    }

    @Override
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        AuthServiceClient authServiceClient = new AuthServiceClient(
                webClient,
                config.getString("auth.service.host"),
                config.getInteger("auth.service.port"));

        MetadataServiceClient metadataServiceClient = new MetadataServiceClient(
                webClient,
                config.getString("metadata.service.host"),
                config.getInteger("metadata.service.port"));

        FileDownloadHandler fileDownloadHandler = new FileDownloadHandler(authServiceClient, metadataServiceClient);
        FileUploadInitHandler fileUploadInitHandler = new FileUploadInitHandler(authServiceClient, metadataServiceClient);
        FileUploadCompleteHandler fileUploadCompleteHandler =
                new FileUploadCompleteHandler(authServiceClient, metadataServiceClient);

        router.get("/files/:fileId").handler(fileDownloadHandler);
        router.post("/files/init").handler(fileUploadInitHandler);
        router.post("/files/complete/:fileId").handler(fileUploadCompleteHandler);

        int port = config.getInteger("server.port");
        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(s -> log.info("Server started on port 8082"))
                .onFailure(Throwable::printStackTrace);
    }
}
