package org.iovchukandrew.dropvox.metadata.server;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class Server extends VerticleBase {

    @Override
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);

        FileDownloadHandler handler = new FileDownloadHandler();
        router.get("/files/:id").handler(handler::handle);

       return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8082)
                .onSuccess(s -> System.out.println("Metadata Service started on port 8082"))
                .onFailure(Throwable::printStackTrace);
    }
}
