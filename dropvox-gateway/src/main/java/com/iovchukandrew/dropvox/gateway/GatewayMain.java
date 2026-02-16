package com.iovchukandrew.dropvox.gateway;

import com.iovchukandrew.dropvox.gateway.server.Server;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayMain {
    private static final Logger log = LoggerFactory.getLogger(GatewayMain.class);

    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Server())
                .onSuccess(id -> log.info("Verticle deployed, id: {}", id))
                .onFailure(Throwable::printStackTrace);
    }
}