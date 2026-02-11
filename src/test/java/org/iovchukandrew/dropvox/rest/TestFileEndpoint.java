package org.iovchukandrew.dropvox.rest;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class TestFileEndpoint {

    private WebClient webClient;

    @BeforeEach
    void deployVerticle(Vertx vertx, VertxTestContext testContext) {
        // Разворачиваем наш вертикл
        vertx.deployVerticle(new RestVerticle())
                .onSuccess(id -> {
                    webClient = WebClient.create(vertx);
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @Test
    void testGetFile(Vertx vertx, VertxTestContext testContext) {
        webClient.get(8080, "localhost", "/files/123")
                .expect(ResponsePredicate.SC_OK)
                .expect(ResponsePredicate.CONTENT_TYPE_JSON)
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        JsonObject json = response.bodyAsJsonObject();
                        assertEquals("123", json.getString("id"));
                        assertEquals("example.txt", json.getString("name"));
                        assertEquals(1024, json.getInteger("size"));
                        assertEquals("text/plain", json.getString("contentType"));
                    });
                    testContext.completeNow();
                }));
    }
}