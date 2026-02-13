package org.iovchukandrew.dropvox.rest;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class RestVerticleTest {

    private WebClient webClient;

    @BeforeEach
    @DisplayName("Deploy a verticle")
    void prepare(Vertx vertx, VertxTestContext testContext) {
        webClient = WebClient.create(vertx);
        vertx.deployVerticle(new RestVerticle())
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void basicRouteTest(VertxTestContext testContext) {
        Checkpoint requestsServed = testContext.checkpoint(10);

        for (int i = 0; i < 10; i++) {
            webClient.get(8888, "localhost", "/")
                    .send()
                    .onComplete(testContext.succeeding(response -> {
                        testContext.verify(() -> {
                            JsonObject json = response.bodyAsJsonObject();
                            assertThat(json.getString("name")).isEqualTo("unknown");
                            assertThat(json.getString("address")).contains("127.0.0.1");
                            assertThat(json.getString("message")).contains("Hello unknown connected from 127.0.0.1");
                            requestsServed.flag();
                        });
                    }));
        }
    }
}
