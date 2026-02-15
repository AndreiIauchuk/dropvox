package org.iovchukandrew.dropvox.gateway;

import io.vertx.core.Vertx;
import org.iovchukandrew.dropvox.gateway.server.Server;

public class GatewayMain {
    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Server())
                .onSuccess(id -> System.out.println("Verticle deployed, id: " + id))
                .onFailure(Throwable::printStackTrace);
    }
}