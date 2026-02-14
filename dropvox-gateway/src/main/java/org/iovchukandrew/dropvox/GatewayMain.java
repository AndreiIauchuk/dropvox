package org.iovchukandrew.dropvox;

import io.vertx.core.Vertx;
import org.iovchukandrew.dropvox.rest.RestVerticle;

public class GatewayMain {
    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new RestVerticle())
                .onSuccess(id -> System.out.println("Verticle deployed, id: " + id))
                .onFailure(Throwable::printStackTrace);
    }
}