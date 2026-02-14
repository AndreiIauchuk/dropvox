package org.iovchukandrew.dropvox.rest;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class RestVerticle extends VerticleBase {

    private Router router;

    @Override
    public Future<?> start() {
        router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        fileRoute();

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888)
                .onSuccess(server -> {
                    System.out.println("HTTP server started on port " + server.actualPort());
                })
                .onFailure(Throwable::printStackTrace);
    }

    private void fileRoute() {
        router.get("/files/:id").respond(ctx -> {
            String fileId = ctx.pathParam("id");
            JsonObject response = new JsonObject()
                    .put("id", fileId)
                    .put("name", "example.txt")
                    .put("size", 1024)
                    .put("contentType", "text/plain");
            return io.vertx.core.Future.succeededFuture(response);
        });
    }
}