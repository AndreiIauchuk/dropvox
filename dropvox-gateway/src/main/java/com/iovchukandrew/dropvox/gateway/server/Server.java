package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends VerticleBase {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    @Override
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);

        AuthServiceClient authServiceClient = new AuthServiceClient();
        MetadataServiceClient metadataServiceClient = new MetadataServiceClient();

        FileDownloadHandler handler = new FileDownloadHandler(authServiceClient, metadataServiceClient);
        router.get("/files/:id").handler(handler::handle);

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080)
                .onSuccess(s -> log.info("Server started on port 8082"))
                .onFailure(Throwable::printStackTrace);
    }
}
