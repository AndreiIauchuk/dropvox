package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.ConfigRetrieverFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class DeployServerTest {

    @Test
    void shouldDeployServer(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = ConfigRetrieverFactory.create(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServer(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    private void deployServer(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new Server(webClient, config))
                .onComplete(handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }
}
