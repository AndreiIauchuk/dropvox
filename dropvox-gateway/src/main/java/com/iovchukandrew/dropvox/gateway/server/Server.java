package com.iovchukandrew.dropvox.gateway.server;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;

public class Server extends VerticleBase {

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
                .onSuccess(server -> System.out
                        .println(getClass().getSimpleName() + " HTTP server started on port " +
                                server.actualPort()))
                .onFailure(Throwable::printStackTrace);
    }
}
