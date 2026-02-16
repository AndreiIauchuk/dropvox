package com.iovchukandrew.dropvox.gateway.server;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
