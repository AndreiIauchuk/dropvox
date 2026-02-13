package org.iovchukandrew.dropvox.rest;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class RestVerticle extends VerticleBase {

    private Router router;

    @Override
    public Future<?> start() {
        router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        basicRoute();
        basicChunked();
        fileRoute();

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888)
                .onSuccess(server -> {
                    System.out.println("HTTP server started on port " + server.actualPort());
                })
                .onFailure(Throwable::printStackTrace);
    }

    private void basicRoute() {
        router.get("/").respond(ctx -> {
            String address = ctx.request().connection().remoteAddress().toString();
            MultiMap queryParams = ctx.queryParams();
            String name = queryParams.contains("name") ? queryParams.get("name") : "unknown";
            return ctx.json(
                    new JsonObject()
                            .put("name", name)
                            .put("address", address)
                            .put("message", "Hello " + name + " connected from " + address)
            );
        });
    }

    private void basicChunked() {
        Route route = router.route("/chunked");
        route.handler(ctx -> {

            HttpServerResponse response = ctx.response();
            response.setChunked(true);
            response.write("route1\n");
            ctx.vertx().setTimer(5000, tid -> ctx.next());
        });

        route.handler(ctx -> {
            HttpServerResponse response = ctx.response();
            response.write("route2\n");
            ctx.vertx().setTimer(5000, tid -> ctx.next());
        });

        route.handler(ctx -> {
            HttpServerResponse response = ctx.response();
            response.write("route3");
            ctx.response().end();
        });
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