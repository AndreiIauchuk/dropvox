package org.iovchukandrew.dropvox.gateway.rest.server;

import org.iovchukandrew.dropvox.gateway.server.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class ServerTest {

    @Test
    void shouldDeployVerticle(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new Server())
                .onComplete(handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }
}
